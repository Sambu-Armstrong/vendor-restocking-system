package com.example.restockforecastsys.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.restockforecastsys.data.entity.Role

@Dao
interface RoleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(role: Role): Long

    @Query("SELECT * FROM roles WHERE roleName = :name LIMIT 1")
    suspend fun getByName(name: String): Role?
}