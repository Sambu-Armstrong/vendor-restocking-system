# Perishable Goods Demand Forecasting & Inventory System — User Manual

## 1. System Overview
The **Perishable Goods Restock & Demand Forecasting App** is an Android mobile application designed for Small and Medium Enterprise (SME) perishable goods vendors. It enables vendors to log daily opening stock, record daily sales, track spoilage/waste, and receive Machine Learning-driven restocking recommendations based on historical sales, shelf life, and local weather conditions.

### System Requirements
* **Device:** Android Smartphone (Android 8.0 / API 26 or higher)
* **Connectivity:** Active internet connection (required for cloud data synchronization and real-time AI demand predictions)

---

## 2. Getting Started

### 2.1 Creating a Vendor Account (Registration)
1. Launch the application on your Android device.
2. On the **Login** screen, tap **Register / Create Account**.
3. Enter your **Full Name**, **Business Name**, **Location**, **Phone Number**, **Email Address**, and **Password**.
4. Tap **Register** to create your account and proceed to the login screen.

### 2.2 Logging In
1. Enter your registered **Email Address** and **Password**.
2. Tap **Login**. Once authenticated, your session remains active until you manually log out.

---

## 3. Navigation Overview
Once logged in, navigate through the application using the **Bottom Navigation Bar**:

| Tab / Control | Location | Function |
| :--- | :--- | :--- |
| **Home** | Bottom Bar (Left) | Displays your stock overview, KPI summary cards, recent logs, and access to **My Products**. |
| **Daily Log** | Bottom Bar (Center) | Allows morning opening stock entry and evening sales/waste logging. |
| **Forecast** | Bottom Bar (Right) | Displays AI-generated demand predictions, spoilage risk levels, and restock quantities. |
| **Log Out** | Top-Right Icon (Home) | Ends your current session and returns to the Login screen. |

---

## 4. Step-by-Step Operational Guide

### 4.1 Setting Up Your Product Catalog (`My Products`)
Before logging stock or generating forecasts, select the perishable items you sell:
1. From the **Home** screen, tap **My Products**.
2. Tap the **`+` (Add Product)** button at the bottom-right corner.
3. Select an item from the standardized **Product Catalog** (e.g., *Tomatoes*, *Onions*, *Kales/Sukuma*). Each item includes a predefined shelf-life duration.
4. To remove an item you no longer sell, tap the **Delete (Trash)** icon next to the product.

### 4.2 Recording Daily Stock and Sales (`Daily Log`)
Daily inventory tracking is split into two phases:

#### A. Morning — Recording Opening Stock
1. Tap the **Daily Log** tab on the bottom navigation bar.
2. Ensure the **Morning (Opening)** tab is selected at the top.
3. Enter the starting quantity available for each product in your catalog.
4. Tap **Save Opening Stock**.

#### B. Evening — Recording Sales & Calculating Waste
1. Open **Daily Log** and switch to the **Evening (Sales & Waste)** tab.
2. For each product, enter the **Units Sold** and **Unsold / Remaining Stock**.
3. Tap **Calculate Waste & Close Stock**. The system automatically computes estimated spoilage/waste and updates your closing inventory.

### 4.3 Viewing AI Restocking Recommendations (`Forecast`)
1. Tap the **Forecast** tab on the bottom navigation bar.
2. The app automatically fetches real-time predictions for the **Target Date** from the Machine Learning engine.
3. Review each product card:
   * **Order Badge (`Order: X`):** Recommended quantity to purchase for the next cycle.
   * **Predicted Demand:** Forecasted customer demand based on historical sales, market/day trends, and weather.
   * **Current Stock:** Remaining inventory currently on hand.
   * **Spoilage Risk Bar:** Color-coded indicator showing the probability of stock spoilage:
     * **Green (0% – 20%):** Low spoilage risk.
     * **Orange (21% – 50%):** Moderate spoilage risk — order cautiously.
     * **Red (Above 50%):** High spoilage risk — reduce order volume to avoid waste.

---

## 5. Troubleshooting

| Issue | Possible Cause | Resolution |
| :--- | :--- | :--- |
| **"No forecast available"** | No products have been added or no stock logs exist yet. | Add items in **My Products** and record at least one **Daily Log** entry. |
| **Network / Server Error** | Weak internet connection or backend API is unreachable. | Check your mobile data/Wi-Fi connection and tap **Retry**. |
| **Product missing in Daily Log** | Product has not been added to your active vendor list. | Open **My Products** from the Home screen and add the item from the catalog. |
