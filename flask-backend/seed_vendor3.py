import datetime
import random
from app import app
from models import db, Vendor, Product, VendorProduct, DailyLog

VENDOR_ID = 3
START_DATE = datetime.date(2026, 11, 12)
END_DATE = datetime.date(2026, 11, 25)  # Yesterday (leaves today Nov 26 clean for live demo)

# Realistic Medium-stall daily baselines per crop
CROP_PROFILES = {
    "Tomatoes": {"base_sales": 60, "waste_range": (1, 4)},
    "Onions":   {"base_sales": 50, "waste_range": (0, 1)},
    "Bananas":  {"base_sales": 55, "waste_range": (1, 3)},
    "Kale":     {"base_sales": 28, "waste_range": (1, 3)},
}

def seed():
    random.seed(42)  # Reproducible, clean numbers
    with app.app_context():
        vendor = db.session.get(Vendor, VENDOR_ID)
        if not vendor:
            print(f"Error: Vendor {VENDOR_ID} not found.")
            return

        vendor_products = VendorProduct.query.filter_by(vendor_id=VENDOR_ID).all()
        if not vendor_products:
            print(f"Error: No products linked to Vendor {VENDOR_ID}.")
            return

        # Clear any existing logs for Vendor 3 so there are zero duplicates
        DailyLog.query.filter_by(vendor_id=VENDOR_ID).delete()
        db.session.commit()

        # Track carried-forward stock day by day per product
        carry_forward = {vp.product_id: 10 for vp in vendor_products}
        total_inserted = 0

        current_date = START_DATE
        while current_date <= END_DATE:
            # Kawangware market days: Tuesday (1) and Friday (4)
            is_market_day = current_date.weekday() in (1, 4)
            day_mult = 1.25 if is_market_day else 1.0

            for vp in vendor_products:
                prod = db.session.get(Product, vp.product_id)
                clean_name = prod.model_name.split(" (")[0]
                profile = CROP_PROFILES.get(clean_name, {"base_sales": 45, "waste_range": (0, 2)})

                prev_closing = carry_forward[vp.product_id]
                expected_demand = int(round(profile["base_sales"] * day_mult * random.uniform(0.90, 1.12)))

                # Restock each morning so total opening stock covers expected demand + small buffer
                new_restock = max(0, expected_demand + random.randint(5, 14) - prev_closing)
                opening_stock = float(prev_closing + new_restock)

                # Simulate realistic sales & waste strictly bounded by opening_stock
                waste = float(random.randint(*profile["waste_range"]))
                max_sellable = max(0.0, opening_stock - waste)
                sales = float(min(max_sellable, expected_demand))
                closing_stock = round(opening_stock - sales - waste, 1)

                # On the final day (yesterday Nov 25), keep ~8-15 units carried forward for today's demo
                if current_date == END_DATE and closing_stock < 8:
                    opening_stock += 10.0
                    closing_stock = round(opening_stock - sales - waste, 1)

                db.session.add(DailyLog(
                    vendor_id=VENDOR_ID,
                    product_id=vp.product_id,
                    date=current_date,
                    opening_stock=opening_stock,
                    closing_stock=closing_stock,
                    sales=sales,
                    waste=waste,
                ))
                carry_forward[vp.product_id] = closing_stock
                total_inserted += 1

            current_date += datetime.timedelta(days=1)

        db.session.commit()
        print(f"Successfully seeded {total_inserted} daily logs for Vendor {VENDOR_ID} ({START_DATE} to {END_DATE}).")
        print("Yesterday's closing stock (carried forward to today):")
        for vp in vendor_products:
            prod = db.session.get(Product, vp.product_id)
            print(f"  - {prod.display_name}: {carry_forward[vp.product_id]} units")

if __name__ == "__main__":
    seed()