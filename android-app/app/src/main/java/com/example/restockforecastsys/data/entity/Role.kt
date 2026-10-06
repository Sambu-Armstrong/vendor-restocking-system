package com.example.restockforecastsys.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roles")
data class Role(
    @PrimaryKey(autoGenerate = true)
    val roleId: Int = 0,
    val roleName: String
)
