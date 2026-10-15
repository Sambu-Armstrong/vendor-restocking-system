package com.example.restockforecastsys.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales_entries",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["productId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Enforces UNIQUE(productId, date)
    indices = [Index(value = ["productId", "date"], unique = true)]
)
data class SalesEntry(
    @PrimaryKey(autoGenerate = true)
    val salesId: Int = 0,
    val productId: Int,
    val date: Long,
    val unitsSold: Int
)
