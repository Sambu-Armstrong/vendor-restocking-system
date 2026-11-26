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

# Load environment variables (.env) and initialize Flask extensions
load_dotenv()
app = Flask(__name__)
CORS(app)
bcrypt = Bcrypt(app)

# Configure PostgreSQL (Supabase) connection with NullPool for serverless stability
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('DATABASE_URL')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['SQLALCHEMY_ENGINE_OPTIONS'] = {'poolclass': NullPool}
db.init_app(app)

# ---------------------------------------------------------
# ML MODEL LOADING (Loaded once into RAM on server startup)
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

# Domain constants matching the synthetic training dataset rules
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
TOWN_COORDS = {
    "Kawangware": (-1.2833, 36.7333), "Machakos": (-1.5177, 37.2634),
    "Kakamega":   (0.2827, 34.7519),  "Mombasa":  (-4.0435, 39.6682)
}
TOWN_WEATHER_FALLBACK = {
    "Kawangware": (22.5, 1.2, 62.0), "Machakos": (24.0, 0.8, 55.0),
    "Kakamega":   (23.5, 4.5, 72.0), "Mombasa":  (28.5, 1.5, 78.0)
}

with app.app_context():
    db.create_all()

# ---------------------------------------------------------
# HELPER FUNCTIONS (Auth, Location & Weather)
# ---------------------------------------------------------
def get_vendor_from_token():
    """Decodes Bearer JWT from Authorization header and returns the linked Vendor."""
    auth_header = request.headers.get('Authorization')
    # Reject immediately if the header is missing or doesn't start with 'Bearer '
    if not auth_header or not auth_header.startswith('Bearer '):
        return None
    token = auth_header.split(' ')[1]
    try:
        # Verify signature & 24h expiry using secret key, then fetch linked vendor profile
        payload = jwt.decode(token, os.getenv('JWT_SECRET_KEY'), algorithms=['HS256'])
        user = db.session.get(User, payload['user_id'])
        return user.vendor_profile if user else None
    except Exception:
        return None

def get_or_create_location(loc_name):
    """Fetches a market town or seeds it with GPS coordinates if missing."""
    location = Location.query.filter_by(name=loc_name).first()
    lat, lon = TOWN_COORDS.get(loc_name, (-1.2833, 36.7333))
    if not location:
        # Create new town row with GPS coordinates for weather lookups
        location = Location(name=loc_name, model_town=loc_name, lat=lat, lon=lon)
        db.session.add(location)
        db.session.flush()
    elif not location.lat or not location.lon:
        # Backfill coordinates if an older row had 0.0 / None
        location.lat, location.lon = lat, lon
    return location

def get_weather(lat, lon, target_date, model_town="Kawangware"):
    """Fetches tomorrow's forecasted weather via OpenWeatherMap, falling back to town baselines."""
    api_key = os.getenv('OWM_API_KEY') or os.getenv('WEATHER_API_KEY')
    if not lat or not lon:
        lat, lon = TOWN_COORDS.get(model_town, (-1.2833, 36.7333))

    if api_key and lat and lon:
        try:
            # Query 5-day / 3-hour forecast API so weather matches tomorrow's target_date
            url = f"https://api.openweathermap.org/data/2.5/forecast?lat={lat}&lon={lon}&appid={api_key}&units=metric"
            resp = requests.get(url, timeout=4)
            if resp.status_code == 200:
                w_data = resp.json()
                target_str = target_date.strftime('%Y-%m-%d')
                # Filter forecasts to tomorrow and prioritize daytime market hours (6 AM - 6 PM)
                all_tomorrow = [x for x in w_data.get('list', []) if x.get('dt_txt', '').startswith(target_str)]
                daytime = [x for x in all_tomorrow if any(h in x.get('dt_txt', '') for h in ("06:00:00", "09:00:00", "12:00:00", "15:00:00", "18:00:00"))]
                slots = daytime if daytime else all_tomorrow

                if slots:
                    # Average daytime temp/humidity; divide 3h rain by 3 to match training mm/h unit
                    temp = round(sum(float(s['main']['temp']) for s in slots) / len(slots), 2)
                    hum = round(sum(float(s['main']['humidity']) for s in slots) / len(slots), 2)
                    rain = round(sum(float(s.get('rain', {}).get('3h', 0.0)) / 3.0 for s in slots) / len(slots), 2)
                    print(f"[Weather FORECAST {target_str}] {model_town}: temp={temp}C, rain={rain}mm/h, hum={hum}%")
                    return temp, rain, hum
            else:
                print(f"[Weather API Warning] Status {resp.status_code}: {resp.text}")
        except Exception as e:
            print(f"[Weather API Error] {e}")

    # Graceful degradation: use typical town climate averages if API key is missing or offline
    fallback = TOWN_WEATHER_FALLBACK.get(model_town, (23.0, 0.0, 60.0))
    print(f"[Weather FALLBACK] {model_town}: temp={fallback[0]}C, rain={fallback[1]}mm, hum={fallback[2]}%")
    return fallback

# ---------------------------------------------------------
# AUTHENTICATION & PROFILE ROUTES
# ---------------------------------------------------------
@app.route('/')
def home():
    return "Restock Forecast API is running."

@app.route('/api/register', methods=['POST'])
def register():
    """Creates User, Vendor profile, and an automatic 30-day trial subscription."""
    data = request.get_json()
    # Prevent duplicate accounts using the same email
    if User.query.filter_by(email=data.get('email')).first():
        return jsonify({"message": "Email already registered"}), 400

    # Hash and salt password before storing in Supabase
    pw_hash = bcrypt.generate_password_hash(data.get('password')).decode('utf-8')
    new_user = User(
        first_name=data.get('firstName'),
        last_name=data.get('lastName'),
        email=data.get('email'),
        phone=data.get('phone'),
        password_hash=pw_hash
    )
    db.session.add(new_user)
    db.session.flush() # Generates new_user.id without committing yet

    # Link vendor to market location (used later for weather & market-day features)
    location = get_or_create_location(data.get('location', 'Kawangware'))
    new_vendor = Vendor(
        user_id=new_user.id,
        business_name=data.get('businessName'),
        location_id=location.id,
        stall_size=data.get('stallSize', 'medium').lower()
    )
    db.session.add(new_vendor)
    db.session.flush() # Generates new_vendor.id for the subscription link

    # Auto-activate 30-day trial so new vendors can log data and test forecasts immediately
    now_utc = datetime.datetime.now(datetime.UTC)
    trial_sub = Subscription(
        vendor_id=new_vendor.id,
        start_date=now_utc,
        end_date=now_utc + datetime.timedelta(days=30),
        is_active=True
    )
    db.session.add(trial_sub)
    db.session.commit() # Saves User, Vendor, and Subscription together
    return jsonify({"message": "Registration successful"}), 201

@app.route('/api/login', methods=['POST'])
def login():
    """Verifies Bcrypt password hash and returns a 24-hour JWT session token."""
    data = request.get_json()
    user = User.query.filter_by(email=data.get('email')).first()

    if user and bcrypt.check_password_hash(user.password_hash, data.get('password')):
        # Sign a stateless JWT containing user_id and a 24-hour expiry timestamp
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

@app.route('/api/profile', methods=['GET'])
def get_profile():
    """Returns current vendor personal and stall details for profile editing."""
    vendor = get_vendor_from_token() # Verify JWT before returning private data
    if not vendor:
        return jsonify({"message": "Unauthorized"}), 401

    user = db.session.get(User, vendor.user_id)
    location = db.session.get(Location, vendor.location_id)
    return jsonify({
        "first_name": user.first_name or "",
        "last_name": user.last_name or "",
        "email": user.email or "",
        "phone": user.phone or "",
        "business_name": vendor.business_name or "",
        "location": location.name if location else "Kawangware",
        "stall_size": (vendor.stall_size or "medium").capitalize()
    }), 200

@app.route('/api/profile', methods=['PUT'])
def update_profile():
    """Updates vendor account and stall attributes (location, stall size, contact)."""
    vendor = get_vendor_from_token()
    if not vendor:
        return jsonify({"message": "Unauthorized"}), 401

    user = db.session.get(User, vendor.user_id)
    data = request.get_json()

    # Selectively update only the fields provided in the request payload
    if data.get('first_name'):
        user.first_name = data['first_name'].strip()
    if data.get('last_name'):
        user.last_name = data['last_name'].strip()
    if data.get('phone') is not None:
        user.phone = data['phone'].strip()
    if data.get('business_name'):
        vendor.business_name = data['business_name'].strip()
    if data.get('stall_size'):
        vendor.stall_size = data['stall_size'].strip().lower()
    if data.get('location'):
        vendor.location_id = get_or_create_location(data['location'].strip()).id

    db.session.commit()
    updated_loc = db.session.get(Location, vendor.location_id)
    return jsonify({
        "first_name": user.first_name or "",
        "last_name": user.last_name or "",
        "email": user.email or "",
        "phone": user.phone or "",
        "business_name": vendor.business_name or "",
        "location": updated_loc.name if updated_loc else "Kawangware",
        "stall_size": (vendor.stall_size or "medium").capitalize()
    }), 200

# ---------------------------------------------------------
# PRODUCT CATALOG & DAILY INVENTORY LOG ROUTES
# ---------------------------------------------------------
@app.route('/api/products/<int:vendor_id>', methods=['GET'])
def get_products(vendor_id):
    """Fetches active products and unit prices configured by a vendor."""
    vendor_products = VendorProduct.query.filter_by(vendor_id=vendor_id).all() # Get vendor's linked items
    result = []
    for vp in vendor_products:
        product = db.session.get(Product, vp.product_id) # Look up product name from master catalog
        if product:
            result.append({
                "product_id": product.id,
                "display_name": product.display_name,
                "model_name": product.model_name, # Clean name expected by the ML model
                "current_price": vp.current_price # Vendor's custom unit selling price (KES)
            })
    return jsonify(result), 200

@app.route('/api/products', methods=['POST'])
def add_product():
    """Adds a catalog product to a vendor's stall or updates its selling price."""
    data = request.get_json()
    vendor_id = data.get('vendor_id')
    display_name = data.get('display_name')
    model_name = data.get('model_name', display_name).split(' (')[0] # Strip Swahili alias if missing
    current_price = float(data.get('current_price', 0.0))

    if current_price <= 0:
        return jsonify({"message": "Selling price must be greater than 0"}), 400

    # Ensure product exists in master Product table first
    product = Product.query.filter_by(display_name=display_name).first()
    if not product:
        product = Product(display_name=display_name, model_name=model_name)
        db.session.add(product)
        db.session.flush() # Get product.id before linking to vendor

    # Link to vendor if new, or update price if already stocked
    vendor_product = VendorProduct.query.filter_by(vendor_id=vendor_id, product_id=product.id).first()
    if not vendor_product:
        db.session.add(VendorProduct(vendor_id=vendor_id, product_id=product.id, current_price=current_price))
    else:
        vendor_product.current_price = current_price
    db.session.commit()
    return jsonify({"message": "Product synced"}), 201

@app.route('/api/products/<int:vendor_id>/<int:product_id>', methods=['DELETE'])
def delete_product(vendor_id, product_id):
    """Removes a product from a vendor's active catalog."""
    vendor_product = VendorProduct.query.filter_by(vendor_id=vendor_id, product_id=product_id).first()
    if vendor_product:
        db.session.delete(vendor_product) # Removes link only; keeps master product & past logs intact
        db.session.commit()
        return jsonify({"message": "Product removed"}), 200
    return jsonify({"message": "Product not found"}), 404

@app.route('/api/daily-logs/<int:vendor_id>', methods=['GET'])
def get_daily_logs(vendor_id):
    """Retrieves the 30 most recent stock/sales logs for dashboard charts and stock levels."""
    logs = (
        DailyLog.query
        .filter_by(vendor_id=vendor_id)
        .order_by(DailyLog.date.desc(), DailyLog.id.desc()) # Newest entries first
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
    """Saves morning opening stock or evening sales/waste with inventory boundary controls."""
    data = request.get_json()
    vendor_id = data.get('vendor_id')
    mode = data.get('mode', 'morning') # Either 'morning' or 'evening'
    date_str = data.get('date') # Optional custom date (useful for seeding demo logs)
    log_date = datetime.datetime.strptime(date_str, '%Y-%m-%d').date() if date_str else datetime.date.today()

    for item in data.get('entries', []):
        product_id = item.get('product_id')
        if not product_id:
            continue

        # Check if a log already exists for this vendor, product, and date (prevents duplicate rows)
        existing_log = DailyLog.query.filter_by(vendor_id=vendor_id, product_id=product_id, date=log_date).first()
        if mode == 'morning':
            opening_val = float(item.get('opening_stock', 0.0))
            if opening_val < 0:
                db.session.rollback()
                return jsonify({"message": "Opening stock cannot be negative"}), 400
            if existing_log:
                existing_log.opening_stock = opening_val # Update opening stock & recalculate remaining
                existing_log.closing_stock = max(0.0, opening_val - existing_log.sales - existing_log.waste)
            else:
                db.session.add(DailyLog(
                    vendor_id=vendor_id, product_id=product_id, date=log_date,
                    opening_stock=opening_val, closing_stock=opening_val, sales=0.0, waste=0.0
                ))
        else: # Evening mode: record sales & waste, then compute unsold closing stock
            sales_val = float(item.get('sales', 0.0))
            waste_val = float(item.get('waste', 0.0))
            if sales_val < 0 or waste_val < 0:
                db.session.rollback()
                return jsonify({"message": "Sales and waste cannot be negative"}), 400
            if existing_log:
                # Enforce inventory conservation: cannot sell/waste more than opening stock
                if existing_log.opening_stock > 0 and (sales_val + waste_val) > existing_log.opening_stock:
                    db.session.rollback()
                    return jsonify({
                        "message": f"Sold + waste ({sales_val + waste_val}) cannot exceed opening stock ({existing_log.opening_stock})"
                    }), 400
                existing_log.sales = sales_val
                existing_log.waste = waste_val
                existing_log.closing_stock = max(0.0, existing_log.opening_stock - sales_val - waste_val) # Never below 0
            else:
                # Fallback if vendor skipped morning entry and only logged evening sales
                opening_val = float(item.get('opening_stock', sales_val + waste_val))
                db.session.add(DailyLog(
                    vendor_id=vendor_id, product_id=product_id, date=log_date,
                    opening_stock=opening_val, closing_stock=max(0.0, opening_val - sales_val - waste_val),
                    sales=sales_val, waste=waste_val
                ))
    db.session.commit()
    return jsonify({"message": "Daily logs synced"}), 200

# ---------------------------------------------------------
# ML FORECASTING & RESTOCK RECOMMENDATION ENDPOINT
# ---------------------------------------------------------
@app.route('/api/forecast', methods=['GET', 'POST'])
def generate_forecast():
    """Engineers contextual features, runs Random Forest inference, and calculates restock orders."""
    # Ensure serialized models and metadata loaded properly into RAM on startup
    if not demand_model or not model_meta:
        return jsonify({"message": "ML models unavailable."}), 503

    # Authenticate the vendor using their Bearer JWT token
    vendor = get_vendor_from_token()
    if not vendor:
        return jsonify({"message": "Unauthorized"}), 401

    # Enforce active subscription access control before running predictions
    if not Subscription.query.filter_by(vendor_id=vendor.id, is_active=True).first():
        return jsonify({"message": "Active subscription required"}), 403

    # 1. Calendar & Holiday Features (Targeting tomorrow's market day)
    target_date = datetime.date.today() + datetime.timedelta(days=1)
    month, day_of_week = target_date.month, target_date.weekday()
    future_holidays = [h for h in HOLIDAYS if h >= target_date] # Filter upcoming holidays
    days_to_hol = min((h - target_date).days for h in future_holidays) if future_holidays else 8
    days_to_hol = 8 if days_to_hol > 7 else days_to_hol # Cap lead-up window at 8 (matches training)
    is_holiday = 1 if days_to_hol == 0 else 0           # Binary flag: 1 if tomorrow is a holiday

    # 2. Location, Market Day & Live Weather Features
    location = db.session.get(Location, vendor.location_id)
    model_town = location.model_town if location else "Kawangware"
    is_market_day = 1 if day_of_week in MARKET_DAYS.get(model_town, ()) else 0 # Town-specific market schedule
    temp, rain, hum = get_weather( # Fetch tomorrow's daytime weather forecast (or town fallback)
        location.lat if location else None,
        location.lon if location else None,
        target_date,
        model_town
    )
    # Convert stall size label ('small'/'medium'/'large') to numeric multiplier (0.6 / 1.0 / 1.5)
    size_val = float(model_meta['size_map'].get((vendor.stall_size or "medium").lower(), 1.0))

    # Fetch all active products configured in this vendor's stall catalog
    vendor_products = VendorProduct.query.filter_by(vendor_id=vendor.id).all()
    if not vendor_products:
        return jsonify({"message": "No products found for forecasting."}), 400

    # 3. Feature Engineering per Product (Q10 shelf-life decay + bounded relative price)
    rows_for_df = []
    for vp in vendor_products:
        prod = db.session.get(Product, vp.product_id)
        if not prod:
            continue

        m_name = prod.model_name.split(' (')[0] # Strip Swahili alias to match training crop names
        base_shelf = BASE_SHELF_LIFE.get(m_name, 6) # Intrinsic crop shelf life in days
        eff_shelf = base_shelf * (2.2 ** ((25.0 - temp) / 10.0)) # Q10 rule: heat accelerates spoilage
        if m_name in ["Kale", "Spinach", "Coriander"] and hum < 45.0:
            eff_shelf *= 0.85 # Extra 15% wilting penalty for leafy greens in dry air
        eff_shelf = max(0.3, eff_shelf) # Prevent unrealistic zero/negative shelf life

        # Normalize vendor's selling price against the crop's training average price
        mean_price = float(model_meta['product_mean_price'].get(m_name, vp.current_price or 15.0))
        effective_price = vp.current_price if (vp.current_price and vp.current_price > 0) else mean_price
        raw_price_rel = float(effective_price / mean_price) if mean_price else 1.0
        price_rel = float(max(0.5, min(2.0, raw_price_rel))) # Clamp within training elasticity bounds (0.5 - 2.0)

        # Build feature dictionary matching the training dataset schema
        row = {
            "month": month, "dayOfWeek": day_of_week,
            "shelfLifeDays": base_shelf, "effectiveShelfLifeDays": eff_shelf,
            "isMarketDay": is_market_day, "isHoliday": is_holiday, "daysToHoliday": days_to_hol,
            "temperature": temp, "rainfall": rain, "humidity": hum,
            "priceRelative": price_rel, "sizeFactor": size_val,
            f"location_{model_town}": 1, f"productName_{m_name}": 1 # One-hot town & crop flags
        }
        rows_for_df.append((vp, prod, row))

    if not rows_for_df:
        return jsonify({"message": "Failed to map products."}), 400

    # Reindex columns to exact 25-feature order from model_meta.json (missing flags default to 0)
    df_model = pd.DataFrame([r[2] for r in rows_for_df]).reindex(columns=model_meta['feature_columns'], fill_value=0)
    demand_preds = demand_model.predict(df_model) # Predict tomorrow's expected units sold
    waste_preds = waste_model.predict(df_model)   # Predict tomorrow's spoilage risk ratio (0.0 - 1.0)

    # 4. Post-Processing: Risk-adjusted safety buffer, carry-forward deduction & persistence
    forecast_results = []
    for idx, (vp, prod, _) in enumerate(rows_for_df):
        pred_demand = int(max(0, round(float(demand_preds[idx])))) # Non-negative whole-unit demand
        waste_risk = float(max(0.0, min(1.0, float(waste_preds[idx])))) # Clamp risk between 0% and 100%
        buffer = float(max(0.0, min(0.18, 0.18 * (1.0 - waste_risk / 0.5)))) # 0-18% buffer (0% if waste_risk >= 50%)

        # Fetch the most recent daily log to get unsold closing stock carried forward
        latest_log = (
            DailyLog.query
            .filter_by(vendor_id=vendor.id, product_id=prod.id)
            .order_by(DailyLog.date.desc(), DailyLog.id.desc())
            .first()
        )
        current_stock = float(latest_log.closing_stock) if latest_log else 0.0

        # Calculate recommended restock order after subtracting carried-forward inventory
        raw_recommendation = int(round(pred_demand * (1.0 + buffer) - current_stock))
        min_order = int(max(0, round(pred_demand - current_stock))) # Never order less than net demand
        recommended_qty = int(max(min_order, raw_recommendation))
       
        # Upsert forecast record into Supabase for audit logging and accuracy tracking
        existing_fc = Forecast.query.filter_by(vendor_id=vendor.id, product_id=prod.id, date=target_date).first()
        if existing_fc:
            existing_fc.predicted_sales = float(pred_demand)
            existing_fc.predicted_waste_rate = float(waste_risk)
            existing_fc.model_version = str(model_meta.get("model_version", "v1"))
        else:
            db.session.add(Forecast(
                vendor_id=vendor.id, product_id=prod.id, date=target_date,
                predicted_sales=float(pred_demand), predicted_waste_rate=float(waste_risk),
                model_version=str(model_meta.get("model_version", "v1"))
            ))

        # Append formatted product forecast card payload for the Android UI
        forecast_results.append({
            "product_id": prod.id,
            "product_name": prod.display_name,
            "target_date": target_date.strftime('%Y-%m-%d'),
            "predicted_demand": pred_demand,
            "waste_risk": round(waste_risk, 3),
            "recommended_qty": recommended_qty,
            "current_stock": current_stock
        })

    db.session.commit() # Commit all generated Forecast rows to Supabase
    return jsonify(forecast_results), 200

# Start Flask development server accessible across local Wi-Fi devices (Android phone)
if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)