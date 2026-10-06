package com.example.restockforecastsys.data.catalog

data class CatalogItem(
    val name: String,
    val category: String,
    val defaultShelfLifeDays: Int,
    val defaultUnitOfMeasure: String
)

object ProductCatalog {
    val items = listOf(
        CatalogItem("Tomatoes", "Vegetable", 5, "kg"),
        CatalogItem("Kale (Sukuma Wiki)", "Leafy Green", 2, "bunches"),
        CatalogItem("Bananas", "Fruit", 6, "kg"),
        CatalogItem("Avocados", "Fruit", 7, "kg"),
        CatalogItem("Onions", "Vegetable", 30, "kg"),
        CatalogItem("Lemons", "Fruit", 21, "kg"),
        CatalogItem("Spinach", "Leafy Green", 2, "bunches"),
        CatalogItem("Coriander (Dhania)", "Herb", 2, "bunches"),
        CatalogItem("Capsicum (Pilipili Hoho)", "Vegetable", 10, "kg")
    )
}