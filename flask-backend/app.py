import os
import datetime
import jwt
from flask import Flask, request, jsonify
from flask_cors import CORS
from flask_bcrypt import Bcrypt
from dotenv import load_dotenv
from models import db, User, Vendor, Location, Product, VendorProduct, DailyLog

# Load environment variables
load_dotenv()

app = Flask(__name__)
CORS(app)
bcrypt = Bcrypt(app)

# Database configuration
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('DATABASE_URL')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['SQLALCHEMY_ENGINE_OPTIONS'] = {
    'pool_pre_ping': True,
    'pool_recycle': 300
}

db.init_app(app)

with app.app_context():
    db.create_all()

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
        stall_size=data.get('stallSize')
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
    return jsonify({"message": "Product synced to cloud successfully"}), 201

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
        if not product_id:
            continue

        existing_log = DailyLog.query.filter_by(
            vendor_id=vendor_id,
            product_id=product_id,
            date=log_date
        ).first()

        if mode == 'morning':
            opening_val = float(item.get('opening_stock', 0.0))
            if existing_log:
                existing_log.opening_stock = opening_val
                existing_log.closing_stock = max(0.0, opening_val - existing_log.sales - existing_log.waste)
            else:
                new_log = DailyLog(
                    vendor_id=vendor_id,
                    product_id=product_id,
                    date=log_date,
                    opening_stock=opening_val,
                    closing_stock=opening_val,
                    sales=0.0,
                    waste=0.0
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
                    vendor_id=vendor_id,
                    product_id=product_id,
                    date=log_date,
                    opening_stock=opening_val,
                    closing_stock=closing_val,
                    sales=sales_val,
                    waste=waste_val
                )
                db.session.add(new_log)

    db.session.commit()
    return jsonify({"message": "Daily logs synced to cloud"}), 200

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)