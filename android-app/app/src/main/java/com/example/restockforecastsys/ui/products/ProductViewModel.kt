package com.example.restockforecastsys.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restockforecastsys.AddProductRequest
import com.example.restockforecastsys.RetrofitClient
import com.example.restockforecastsys.data.catalog.CatalogItem
import com.example.restockforecastsys.data.catalog.ProductCatalog
import com.example.restockforecastsys.data.entity.Product
import com.example.restockforecastsys.data.repository.ProductRepository
import com.example.restockforecastsys.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Manages the vendor's active produce catalog and syncs changes with Flask/Supabase
class ProductViewModel(
    private val repository: ProductRepository,
    private val sessionManager: SessionManager // Provides logged-in vendor_id
) : ViewModel() {

    // Private mutable list; UI screens observe the read-only StateFlow below via collectAsState()
    private val _vendorProducts = MutableStateFlow<List<Product>>(emptyList())
    val vendorProducts: StateFlow<List<Product>> = _vendorProducts.asStateFlow()

    // Fetches vendor's products from Supabase and enriches them with local catalog units & shelf life
    fun loadVendorProducts() {
        val currentVendorId = sessionManager.getUserId()
        if (currentVendorId == -1) return // Abort if no vendor is logged in

        viewModelScope.launch(Dispatchers.IO) { // Run network call on background IO thread
            try {
                val response = RetrofitClient.apiService.getVendorProducts(currentVendorId)
                if (response.isSuccessful) {
                    val cloudList = response.body().orEmpty()
                    _vendorProducts.value = cloudList.map { dto ->
                        // Match cloud crop name to ProductCatalog to attach unit (piece/bunch) & shelf life
                        val catalogMatch = ProductCatalog.items.find { it.name == dto.display_name }
                        Product(
                            productId = dto.product_id,
                            vendorId = currentVendorId,
                            name = dto.display_name,
                            category = catalogMatch?.category ?: "Produce",
                            shelfLifeDays = catalogMatch?.defaultShelfLifeDays ?: 5,
                            unitOfMeasure = catalogMatch?.defaultUnitOfMeasure ?: "kg",
                            currentPrice = dto.current_price,
                            dateAdded = System.currentTimeMillis()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Adds a new catalog crop with the vendor's unit price to Supabase (blocks duplicates)
    fun addProductFromCatalog(catalogItem: CatalogItem, price: Double) {
        val currentVendorId = sessionManager.getUserId()
        if (currentVendorId == -1) return

        // Prevent adding the same product twice
        if (_vendorProducts.value.any { it.name == catalogItem.name }) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = AddProductRequest(
                    vendor_id = currentVendorId,
                    display_name = catalogItem.name,
                    model_name = catalogItem.name,
                    current_price = price
                )
                val response = RetrofitClient.apiService.addVendorProduct(request)
                if (response.isSuccessful) {
                    loadVendorProducts() // Refresh UI list after saving
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Updates an existing product's unit selling price (uses Flask's upsert POST /api/products route)
    fun updateProductPrice(product: Product, newPrice: Double) {
        val currentVendorId = sessionManager.getUserId()
        if (currentVendorId == -1) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = AddProductRequest(
                    vendor_id = currentVendorId,
                    display_name = product.name,
                    model_name = product.name,
                    current_price = newPrice
                )
                val response = RetrofitClient.apiService.addVendorProduct(request)
                if (response.isSuccessful) {
                    loadVendorProducts() // Refresh UI with updated price
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Unlinks a product from the vendor's active stall catalog in Supabase
    fun removeProduct(product: Product) {
        val currentVendorId = sessionManager.getUserId()
        if (currentVendorId == -1) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.deleteVendorProduct(
                    vendorId = currentVendorId,
                    productId = product.productId
                )
                if (response.isSuccessful) {
                    loadVendorProducts() // Refresh UI after deletion
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}