package com.example.restockforecastsys.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restockforecastsys.data.catalog.CatalogItem
import com.example.restockforecastsys.data.entity.Product
import com.example.restockforecastsys.data.repository.ProductRepository
import com.example.restockforecastsys.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProductViewModel(
    private val repository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _vendorProducts = MutableStateFlow<List<Product>>(emptyList())
    val vendorProducts: StateFlow<List<Product>> = _vendorProducts.asStateFlow()

    private var collectionJob: Job? = null

    // Called dynamically when the screen is opened
    fun loadVendorProducts() {
        val currentVendorId = sessionManager.getUserId()
        if (currentVendorId != -1) {
            collectionJob?.cancel() // Clear any old data streams
            collectionJob = viewModelScope.launch {
                repository.getProductsForVendor(currentVendorId).collectLatest { products ->
                    _vendorProducts.value = products
                }
            }
        }
    }

    fun addProductFromCatalog(catalogItem: CatalogItem) {
        val currentVendorId = sessionManager.getUserId()
        if (currentVendorId == -1) return // Failsafe

        // Prevent duplicate entries
        if (_vendorProducts.value.any { it.name == catalogItem.name }) return

        val newProduct = Product(
            vendorId = currentVendorId,
            name = catalogItem.name,
            category = catalogItem.category,
            shelfLifeDays = catalogItem.defaultShelfLifeDays,
            unitOfMeasure = catalogItem.defaultUnitOfMeasure,
            currentPrice = 0.0,
            dateAdded = System.currentTimeMillis()
        )

        viewModelScope.launch { repository.addProduct(newProduct) }
    }

    fun removeProduct(product: Product) {
        viewModelScope.launch { repository.removeProduct(product) }
    }
}