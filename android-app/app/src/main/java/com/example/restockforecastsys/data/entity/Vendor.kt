package com.example.restockforecastsys.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "vendors",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Vendor(
    @PrimaryKey
    val userId: Int, // NOT autoGenerate — acts as both PK and FK per your ERD

    val businessName: String,
    val locationId: Int? = null
)
