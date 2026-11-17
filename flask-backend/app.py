import os
import datetime
import jwt
import json
import joblib
import pandas as pd
import numpy as np
import requests
from flask import Flask, request, jsonify
from flask_cors import CORS
from flask_bcrypt import Bcrypt
from dotenv import load_dotenv
from models import db, User, Vendor, Location, Product, VendorProduct, DailyLog, Subscription, Forecast
from sqlalchemy.pool import NullPool
# Load environment variables
load_dotenv()

app = Flask(__name__)
CORS(app)
bcrypt = Bcrypt(app)

# Database configuration
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('DATABASE_URL')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['SQLALCHEMY_ENGINE_OPTIONS'] = {
    'poolclass': NullPool
}

db.init_app(app)

# ---------------------------------------------------------
# ML MODEL LOADING (Runs once on startup)
# ---------------------------------------------------------
try:
    demand_model = joblib.load('artifacts/demand_model_v1.joblib')
    waste_model = joblib.load('artifacts/waste_model_v1.joblib')
    with open('artifacts/model_meta.json', 'r') as f:
        model_meta = json.load(f)
    print("ML Models and Metadata loaded successfully.")
except Exception as e:
    print(f"Warning: ML models failed to load. Check artifacts folder. Error: {e}")
    demand_model, waste_model, model_meta = None, None, None

BASE_SHELF_LIFE = {
    "Tomatoes": 6, "Kale": 2, "Spinach": 2, "Coriander": 2,
    "Capsicum": 7, "Bananas": 6, "Avocados": 7, "Lemons": 12, "Onions": 20
}

MARKET_DAYS = {
    "Kawangware": (1, 4), "Machakos": (0, 4),
    "Kakamega": (2, 5), "Mombasa": (4, 6)
}

HOLIDAYS = [
    datetime.date(2026, 12, 25), datetime.date(2027, 12, 25),
    datetime.date(2026, 4, 3),   datetime.date(2027, 3, 26),
    datetime.date(2026, 3, 20),  datetime.date(2027, 3, 10)
]

# Typical town climate baselines (temp C, rain mm, humidity %) if weather API is unavailable
TOWN_WEATHER_FALLBACK = {
    "Kawangware": (22.5, 1.2, 62.0),
    "Machakos":   (24.0, 0.8, 55.0),
    "Kakamega":   (23.5, 4.5, 72.0),
    "Mombasa":    (28.5, 1.5, 78.0)
}

with app.app_context():
    db.create_all()

# ---------------------------------------------------------
# HELPER FUNCTIONS
# ---------------------------------------------------------
def get_vendor_from_token():
    auth_header = request.headers.get('Authorization')
    if not auth_header or not auth_header.startswith('Bearer '):
        return None
    token = auth_header.split(' ')[1]
    try:
        payload = jwt.decode(token, os.getenv('JWT_SECRET_KEY'), algorithms=['HS256'])
        user = db.session.get(User, payload['user_id'])
        return user.vendor_profile if user else None
    except Exception:
        return None

def get_weather(lat, lon, target_date, model_town="Kawangware"):
    """Fetches weather from OpenWeatherMap, falling back to typical town values."""
    api_key = os.getenv('OWM_API_KEY') or os.getenv('WEATHER_API_KEY')
    if api_key and lat and lon:
        try:
            url = f"https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid={api_key}&units=metric"
            resp = requests.get(url, timeout=3)
            if resp.status_code == 200:
                w_data = resp.json()
                temp = float(w_data['main']['temp'])
                hum = float(w_data['main']['humidity'])
                rain = float(w_data.get('rain', {}).get('1h', 0.0))
                return temp, rain, hum
        except Exception:
            pass
    return TOWN_WEATHER_FALLBACK.get(model_town, (23.0, 0.0, 60.0))

# ---------------------------------------------------------
# ROUTES
# ---------------------------------------------------------
@app.route('/')
def home():
    return "Restock Forecast API is running."

@app.route('/api/register', methods=['POST'])
def register():
    data = request.get_json()
    if User.query.filter_by(email=data.get('email')).first():
        return jsonify({"message": "Email already registered"}), 400
        
    pw_hash = bcrypt.generate_password_hash(data.get('password')).decode('utf-8')
    
    new_user = User(
        first_name=data.get('firstName'),
        last_name=data.get('lastName'),
        email=data.get('email'),
        phone=data.get('phone'),
        password_hash=pw_hash
    )
    db.session.add(new_user)
    db.session.flush()
    
    loc_name = data.get('location')
    location = Location.query.filter_by(name=loc_name).first()
    if not location:
        location = Location(name=loc_name, model_town=loc_name, lat=0.0, lon=0.0)
        db.session.add(location)
        db.session.flush()
        
    new_vendor = Vendor(
        user_id=new_user.id,
        business_name=data.get('businessName'),
        location_id=location.id,
        stall_size=data.get('stallSize', 'medium').lower()
    )
    db.session.add(new_vendor)
    db.session.commit()
    return jsonify({"message": "Registration successful"}), 201

@app.route('/api/login', methods=['POST'])
def login():
    data = request.get_json()
    user = User.query.filter_by(email=data.get('email')).first()
    
    if user and bcrypt.check_password_hash(user.password_hash, data.get('password')):
        token = jwt.encode({
            'user_id': user.id,
            'exp': datetime.datetime.now(datetime.UTC) + datetime.timedelta(hours=24)
        }, os.getenv('JWT_SECRET_KEY'), algorithm='HS256')
        
        return jsonify({
            "message": "Login successful",
            "token": token,
            "vendor_id": user.vendor_profile.id if user.vendor_profile else None,
            "first_name": user.first_name
        }), 200
        
    return jsonify({"message": "Invalid email or password"}), 401

@app.route('/api/products/<int:vendor_id>', methods=['GET'])
def get_products(vendor_id):
    vendor_products = VendorProduct.query.filter_by(vendor_id=vendor_id).all()
    result = []
    for vp in vendor_products:
        product = db.session.get(Product, vp.product_id)
        if product:
            result.append({
                "product_id": product.id,
                "display_name": product.display_name,
                "model_name": product.model_name,
                "current_price": vp.current_price
            })
    return jsonify(result), 200

@app.route('/api/products', methods=['POST'])
def add_product():
    data = request.get_json()
    vendor_id = data.get('vendor_id')
    display_name = data.get('display_name')
    model_name = data.get('model_name', display_name)
    current_price = data.get('current_price', 0.0)

    product = Product.query.filter_by(display_name=display_name).first()
    if not product:
        product = Product(display_name=display_name, model_name=model_name)
        db.session.add(product)
        db.session.flush()

    vendor_product = VendorProduct.query.filter_by(vendor_id=vendor_id, product_id=product.id).first()
    if not vendor_product:
        vendor_product = VendorProduct(
            vendor_id=vendor_id,
            product_id=product.id,
            current_price=current_price
        )
        db.session.add(vendor_product)
    else:
        vendor_product.current_price = current_price
    db.session.commit()
    return jsonify({"message": "Product synced"}), 201

@app.route('/api/products/<int:vendor_id>/<int:product_id>', methods=['DELETE'])
def delete_product(vendor_id, product_id):
    vendor_product = VendorProduct.query.filter_by(vendor_id=vendor_id, product_id=product_id).first()
    if vendor_product:
        db.session.delete(vendor_product)
        db.session.commit()
        return jsonify({"message": "Product removed"}), 200
    return jsonify({"message": "Product not found"}), 404

@app.route('/api/daily-logs/<int:vendor_id>', methods=['GET'])
def get_daily_logs(vendor_id):
    logs = (
        DailyLog.query
        .filter_by(vendor_id=vendor_id)
        .order_by(DailyLog.date.desc(), DailyLog.id.desc())
        .limit(30)
        .all()
    )
    result = []
    for log in logs:
        product = db.session.get(Product, log.product_id)
        result.append({
            "id": log.id,
            "vendor_id": log.vendor_id,
            "product_id": log.product_id,
            "product_name": product.display_name if product else "Unknown",
            "date": log.date.strftime('%Y-%m-%d'),
            "opening_stock": log.opening_stock,
            "closing_stock": log.closing_stock,
            "sales": log.sales,
            "waste": log.waste
        })
    return jsonify(result), 200

@app.route('/api/daily-logs', methods=['POST'])
def save_daily_logs():
    data = request.get_json()
    vendor_id = data.get('vendor_id')
    mode = data.get('mode', 'morning')
    date_str = data.get('date')
    entries = data.get('entries', [])

    if date_str:
        log_date = datetime.datetime.strptime(date_str, '%Y-%m-%d').date()
    else:
        log_date = datetime.date.today()

    for item in entries:
        product_id = item.get('product_id')
        if not product_id: continue

        existing_log = DailyLog.query.filter_by(vendor_id=vendor_id, product_id=product_id, date=log_date).first()

        if mode == 'morning':
            opening_val = float(item.get('opening_stock', 0.0))
            if existing_log:
                existing_log.opening_stock = opening_val
                existing_log.closing_stock = max(0.0, opening_val - existing_log.sales - existing_log.waste)
            else:
                new_log = DailyLog(
                    vendor_id=vendor_id, product_id=product_id, date=log_date,
                    opening_stock=opening_val, closing_stock=opening_val, sales=0.0, waste=0.0
                )
                db.session.add(new_log)
        else:
            sales_val = float(item.get('sales', 0.0))
            waste_val = float(item.get('waste', 0.0))
            if existing_log:
                existing_log.sales = sales_val
                existing_log.waste = waste_val
                existing_log.closing_stock = max(0.0, existing_log.opening_stock - sales_val - waste_val)
            else:
                opening_val = float(item.get('opening_stock', sales_val + waste_val))
                closing_val = max(0.0, opening_val - sales_val - waste_val)
                new_log = DailyLog(
                    vendor_id=vendor_id, product_id=product_id, date=log_date,
                    opening_stock=opening_val, closing_stock=closing_val, sales=sales_val, waste=waste_val
                )
                db.session.add(new_log)
    db.session.commit()
    return jsonify({"message": "Daily logs synced"}), 200

# ---------------------------------------------------------
# ML FORECASTING ENDPOINT
# ---------------------------------------------------------
@app.route('/api/forecast', methods=['GET', 'POST'])
def generate_forecast():
    if not demand_model or not model_meta:
        return jsonify({"message": "ML models unavailable."}), 503

    vendor = get_vendor_from_token()
    if not vendor:
        return jsonify({"message": "Unauthorized"}), 401

    # Security check: subscription
    active_sub = Subscription.query.filter_by(vendor_id=vendor.id, is_active=True).first()
    if not active_sub:
        return jsonify({"message": "Active subscription required"}), 403

    # Target tomorrow
    target_date = datetime.date.today() + datetime.timedelta(days=1)
    
    # 1. Date Features
    month = target_date.month
    day_of_week = target_date.weekday()

    future_holidays = [h for h in HOLIDAYS if h >= target_date]
    days_to_hol = min((h - target_date).days for h in future_holidays) if future_holidays else 8
    if days_to_hol > 7: 
        days_to_hol = 8
    is_holiday = 1 if days_to_hol == 0 else 0

    # 2. Location Features (Fetch Location via vendor.location_id)
    location = db.session.get(Location, vendor.location_id)
    model_town = location.model_town if location else "Kawangware"
    town_market_days = MARKET_DAYS.get(model_town, ())
    is_market_day = 1 if day_of_week in town_market_days else 0

    # 3. Weather Fetch
    temp, rain, hum = get_weather(
        location.lat if location else -1.2833,
        location.lon if location else 36.7333,
        target_date,
        model_town
    )

    stall_key = (vendor.stall_size or "medium").lower()
    size_val = float(model_meta['size_map'].get(stall_key, 1.0))

    forecast_results = []
    rows_for_df = []

    # Fetch vendor's products to forecast
    vendor_products = VendorProduct.query.filter_by(vendor_id=vendor.id).all()
    if not vendor_products:
        return jsonify({"message": "No products found for forecasting."}), 400

    # Build the exact rows needed by the ML model
    for vp in vendor_products:
        prod = db.session.get(Product, vp.product_id)
        if not prod:
            continue
        
        m_name = prod.model_name
        base_shelf = BASE_SHELF_LIFE.get(m_name, 6)
        
        # Calculate mathematical effective shelf life
        eff_shelf = base_shelf * (2.2 ** ((25.0 - temp) / 10.0))
        if m_name in ["Kale", "Spinach", "Coriander"] and hum < 45.0:
            eff_shelf *= 0.85
        eff_shelf = max(0.3, eff_shelf)

        # Price Relative calculation
        mean_price = float(model_meta['product_mean_price'].get(m_name, vp.current_price or 15.0))
        effective_price = vp.current_price if (vp.current_price and vp.current_price > 0) else mean_price
        price_rel = float(effective_price / mean_price) if mean_price else 1.0

        # Create row dict
        row = {
            "month": month,
            "dayOfWeek": day_of_week,
            "shelfLifeDays": base_shelf,
            "effectiveShelfLifeDays": eff_shelf,
            "isMarketDay": is_market_day,
            "isHoliday": is_holiday,
            "daysToHoliday": days_to_hol,
            "temperature": temp,
            "rainfall": rain,
            "humidity": hum,
            "priceRelative": price_rel,
            "sizeFactor": size_val,
            f"location_{model_town}": 1,
            f"productName_{m_name}": 1
        }
        rows_for_df.append((vp, prod, row))

    if not rows_for_df:
        return jsonify({"message": "Failed to map products."}), 400

    # Convert to DataFrame and enforce exact strict column order from JSON
    df_raw = pd.DataFrame([r[2] for r in rows_for_df])
    df_model = df_raw.reindex(columns=model_meta['feature_columns'], fill_value=0)

    # 4. INFERENCE
    demand_preds = demand_model.predict(df_model)
    waste_preds = waste_model.predict(df_model)

    # 5. POST-PROCESSING (Restock Recommendation)
    for idx, (vp, prod, _) in enumerate(rows_for_df):
        pred_demand = int(max(0, round(float(demand_preds[idx]))))
        waste_risk = float(max(0.0, min(1.0, float(waste_preds[idx]))))
        
        # Buffer calculation: clip(0.18 * (1 - waste_risk / 0.5), 0, 0.18)
        buffer = float(max(0.0, min(0.18, 0.18 * (1.0 - waste_risk / 0.5))))
        
        # Find current stock
        latest_log = (
            DailyLog.query
            .filter_by(vendor_id=vendor.id, product_id=prod.id)
            .order_by(DailyLog.date.desc(), DailyLog.id.desc())
            .first()
        )
        current_stock = float(latest_log.closing_stock) if latest_log else 0.0
        
        raw_recommendation = int(round(pred_demand * (1.0 + buffer) - current_stock))
        min_order = int(max(0, round(pred_demand - current_stock)))  # Never drop below demand - stock
        
        recommended_qty = int(max(min_order, raw_recommendation))

        # Save to Forecast DB table (cast to standard Python float for PostgreSQL)
        existing_fc = Forecast.query.filter_by(vendor_id=vendor.id, product_id=prod.id, date=target_date).first()
        if existing_fc:
            existing_fc.predicted_sales = float(pred_demand)
            existing_fc.predicted_waste_rate = float(waste_risk)
            existing_fc.model_version = str(model_meta.get("model_version", "v1"))
        else:
            new_fc = Forecast(
                vendor_id=vendor.id,
                product_id=prod.id,
                date=target_date,
                predicted_sales=float(pred_demand),
                predicted_waste_rate=float(waste_risk),
                model_version=str(model_meta.get("model_version", "v1"))
            )
            db.session.add(new_fc)

        forecast_results.append({
            "product_id": prod.id,
            "product_name": prod.display_name,
            "target_date": target_date.strftime('%Y-%m-%d'),
            "predicted_demand": pred_demand,
            "waste_risk": round(waste_risk, 3),
            "recommended_qty": recommended_qty,
            "current_stock": current_stock
        })

    db.session.commit()
    return jsonify(forecast_results), 200

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)