package com.example.restockforecastsys.data.catalog

data class CatalogItem(
    val name: String,
    val category: String,
    val defaultShelfLifeDays: Int,
    val defaultUnitOfMeasure: String
)

object ProductCatalog {
    val items = listOf(
        CatalogItem("Tomatoes", "Vegetable", 6, "piece"),
        CatalogItem("Kale (Sukuma Wiki)", "Leafy Green", 2, "bunch"),
        CatalogItem("Bananas", "Fruit", 6, "piece"),
        CatalogItem("Avocados", "Fruit", 7, "piece"),
        CatalogItem("Onions", "Vegetable", 20, "piece"),
        CatalogItem("Lemons", "Fruit", 12, "piece"),
        CatalogItem("Spinach", "Leafy Green", 2, "bunch"),
        CatalogItem("Coriander (Dhania)", "Herb", 2, "bunch"),
        CatalogItem("Capsicum (Pilipili Hoho)", "Vegetable", 7, "piece")
    )
}