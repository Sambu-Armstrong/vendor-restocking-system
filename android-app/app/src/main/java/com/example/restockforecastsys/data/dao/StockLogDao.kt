package com.example.restockforecastsys.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.restockforecastsys.data.entity.StockLog
import kotlinx.coroutines.flow.Flow

@Dao
interface StockLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stockLog: StockLog): Long

    @Update
    suspend fun update(stockLog: StockLog)

    @Query("SELECT * FROM stock_logs WHERE productId = :productId AND date = :date LIMIT 1")
    suspend fun getStockLog(productId: Int, date: Long): StockLog?

    @Query("SELECT * FROM stock_logs WHERE date = :date")
    fun getStockLogsForDate(date: Long): Flow<List<StockLog>>
}