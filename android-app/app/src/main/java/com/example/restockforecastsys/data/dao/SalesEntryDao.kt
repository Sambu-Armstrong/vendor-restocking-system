package com.example.restockforecastsys.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.restockforecastsys.data.entity.SalesEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesEntryDao {
    // Uses REPLACE so if a vendor updates their sales for the same day, it overwrites the old entry
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(salesEntry: SalesEntry): Long

    @Query("SELECT * FROM sales_entries WHERE productId = :productId AND date = :date LIMIT 1")
    suspend fun getSalesEntry(productId: Int, date: Long): SalesEntry?

    @Query("SELECT * FROM sales_entries WHERE date = :date")
    fun getSalesForDate(date: Long): Flow<List<SalesEntry>>
}