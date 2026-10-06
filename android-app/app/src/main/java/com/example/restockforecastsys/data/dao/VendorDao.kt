package com.example.restockforecastsys.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.restockforecastsys.data.entity.Vendor

@Dao
interface VendorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vendor: Vendor)

    @Query("SELECT * FROM vendors WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: Int): Vendor?
}