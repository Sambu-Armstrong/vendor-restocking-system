package com.example.restockforecastsys.data.repository

import com.example.restockforecastsys.data.dao.ProductDao
import com.example.restockforecastsys.data.entity.Product
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {

    // Returns a live stream of products for the specific vendor
    fun getProductsForVendor(vendorId: Int): Flow<List<Product>> {
        return productDao.getProductsForVendor(vendorId)
    }

    suspend fun addProduct(product: Product) {
        productDao.insert(product)
    }

    suspend fun removeProduct(product: Product) {
        productDao.delete(product)
    }
}