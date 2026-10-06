package com.example.restockforecastsys.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = Vendor::class,
            parentColumns = ["userId"],
            childColumns = ["vendorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vendorId"])]
)
data class Product(
    @PrimaryKey(autoGenerate = true)
    val productId: Int = 0,
    val vendorId: Int,
    val name: String,
    val category: String,
    val shelfLifeDays: Int,
    val unitOfMeasure: String,
    val currentPrice: Double,
    val dateAdded: Long
)
