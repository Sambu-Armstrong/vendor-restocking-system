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

class ProductViewModel(
    private val repository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _vendorProducts = MutableStateFlow<List<Product>>(emptyList())
    val vendorProducts: StateFlow<List<Product>> = _vendorProducts.asStateFlow()

    fun loadVendorProducts() {
        val currentVendorId = sessionManager.getUserId()
        if (currentVendorId == -1) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getVendorProducts(currentVendorId)
                if (response.isSuccessful) {
                    val cloudList = response.body().orEmpty()
                    _vendorProducts.value = cloudList.map { dto ->
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

    fun addProductFromCatalog(catalogItem: CatalogItem, price: Double) {
        val currentVendorId = sessionManager.getUserId()
        if (currentVendorId == -1) return

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
                    loadVendorProducts()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
                    loadVendorProducts()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
                    loadVendorProducts()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}