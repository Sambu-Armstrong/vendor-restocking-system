import os
import pandas as pd
from datetime import datetime, timedelta
from app import app
from models import db, Vendor, Location, Product, VendorProduct, DailyLog, Subscription

def import_csv_to_db():
    # Check current folder first, then data/ subfolder
    csv_path = 'demo_vendor_data.csv'
    if not os.path.exists(csv_path) and os.path.exists(os.path.join('data', 'demo_vendor_data.csv')):
        csv_path = os.path.join('data', 'demo_vendor_data.csv')

    if not os.path.exists(csv_path):
        print(f"Error: Could not find demo_vendor_data.csv in {os.getcwd()}")
        return

    df = pd.read_csv(csv_path)

    with app.app_context():
        # 1. Seed the 4 towns with exact coordinates & model_town names
        towns = [
            {"name": "Kawangware", "model_town": "Kawangware", "lat": -1.2833, "lon": 36.7333},
            {"name": "Machakos",   "model_town": "Machakos",   "lat": -1.5177, "lon": 37.2634},
            {"name": "Kakamega",   "model_town": "Kakamega",   "lat":  0.2827, "lon": 34.7519},
            {"name": "Mombasa",    "model_town": "Mombasa",    "lat": -4.0435, "lon": 39.6682},
        ]
        for t in towns:
            loc = Location.query.filter_by(name=t["name"]).first()
            if not loc:
                loc = Location(**t)
                db.session.add(loc)
            else:
                loc.model_town = t["model_town"]
                loc.lat = t["lat"]
                loc.lon = t["lon"]
        db.session.flush()

        # 2. Seed all 9 catalog products with exact display_name and ML model_name
        catalog = [
            ("Tomatoes",           "Tomatoes",  10.0),
            ("Kale (Sukuma Wiki)", "Kale",      25.0),
            ("Onions",             "Onions",    15.0),
            ("Spinach",            "Spinach",   20.0),
            ("Coriander",          "Coriander", 20.0),
            ("Capsicum",           "Capsicum",  25.0),
            ("Bananas",            "Bananas",   10.0),
            ("Avocados",           "Avocados",  25.0),
            ("Lemons",             "Lemons",    20.0),
        ]
        model_to_product = {}
        for display_name, model_name, default_price in catalog:
            prod = Product.query.filter(
                (Product.display_name == display_name) | (Product.model_name == model_name)
            ).first()
            if not prod:
                prod = Product(display_name=display_name, model_name=model_name)
                db.session.add(prod)
                db.session.flush()
            else:
                prod.display_name = display_name
                prod.model_name = model_name
            model_to_product[model_name] = prod

        # 3. Find your active vendor account (Vendor ID 1, or first vendor in DB)
        vendor = db.session.get(Vendor, 1) or Vendor.query.first()
        if not vendor:
            print("Error: No vendor found in DB. Please register an account in the app first.")
            return

        # Ensure vendor is mapped to Kawangware and medium stall size to match demo profile
        kawangware = Location.query.filter_by(model_town="Kawangware").first()
        if kawangware:
            vendor.location_id = kawangware.id
        if not vendor.stall_size or vendor.stall_size.lower() not in ("small", "medium", "large"):
            vendor.stall_size = "medium"

        # 4. Link the CSV's products (Kale, Tomatoes, Onions) to this vendor in vendor_products
        latest_prices = df.groupby('productName')['price'].last().to_dict()
        for model_name, price_val in latest_prices.items():
            prod = model_to_product.get(model_name)
            if not prod:
                continue
            vp = VendorProduct.query.filter_by(vendor_id=vendor.id, product_id=prod.id).first()
            if not vp:
                vp = VendorProduct(
                    vendor_id=vendor.id,
                    product_id=prod.id,
                    current_price=float(price_val)
                )
                db.session.add(vp)
            else:
                vp.current_price = float(price_val)

        # 5. Ensure Vendor has an active Subscription so /api/forecast does not return 403
        active_sub = Subscription.query.filter_by(vendor_id=vendor.id, is_active=True).first()
        if not active_sub:
            sub = Subscription(
                vendor_id=vendor.id,
                start_date=datetime.utcnow(),
                end_date=datetime.utcnow() + timedelta(days=30),
                is_active=True
            )
            db.session.add(sub)

        # 6. Import all 172 historical logs into daily_logs
        logs_added = 0
        logs_updated = 0
        for _, row in df.iterrows():
            prod = model_to_product.get(row['productName'])
            if not prod:
                continue

            log_date = datetime.strptime(str(row['date']).split(' ')[0], '%Y-%m-%d').date()
            existing_log = DailyLog.query.filter_by(
                vendor_id=vendor.id,
                product_id=prod.id,
                date=log_date
            ).first()

            if not existing_log:
                new_log = DailyLog(
                    vendor_id=vendor.id,
                    product_id=prod.id,
                    date=log_date,
                    opening_stock=float(row['openingStock']),
                    closing_stock=float(row['closingStock']),
                    sales=float(row['unitsSold']),
                    waste=float(row['estimatedWaste'])
                )
                db.session.add(new_log)
                logs_added += 1
            else:
                existing_log.opening_stock = float(row['openingStock'])
                existing_log.closing_stock = float(row['closingStock'])
                existing_log.sales = float(row['unitsSold'])
                existing_log.waste = float(row['estimatedWaste'])
                logs_updated += 1

        db.session.commit()
        print(f"SUCCESS: Linked to Vendor ID {vendor.id} ({vendor.business_name})")
        print(f"-> Products linked: {list(latest_prices.keys())}")
        print(f"-> Daily logs inserted: {logs_added} (Updated: {logs_updated})")
        print("-> Active subscription verified.")

if __name__ == '__main__':
    import_csv_to_db()