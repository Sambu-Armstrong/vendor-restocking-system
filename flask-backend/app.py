import os
import datetime
import jwt
from flask import Flask, request, jsonify
from flask_cors import CORS
from flask_bcrypt import Bcrypt
from dotenv import load_dotenv
from models import db, User, Vendor, Location

# Load environment variables
load_dotenv()

app = Flask(__name__)
CORS(app)
bcrypt = Bcrypt(app)

# Database configuration
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('DATABASE_URL')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

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
    db.session.flush() # Gets user ID before final commit
    
    # Handle hardcoded locations from Phase 1 Android dropdowns
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
            "vendor_id": user.vendor_profile.id if user.vendor_profile else None
        }), 200
        
    return jsonify({"message": "Invalid email or password"}), 401

if __name__ == '__main__':
    # host='0.0.0.0' exposes the server to your local Wi-Fi network
    app.run(debug=True, host='0.0.0.0', port=5000)