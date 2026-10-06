package com.example.restockforecastsys.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.restockforecastsys.data.entity.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    // Using Flow means the UI will automatically update the list if items are added/removed
    @Query("SELECT * FROM products WHERE vendorId = :vendorId ORDER BY name ASC")
    fun getProductsForVendor(vendorId: Int): Flow<List<Product>>
}