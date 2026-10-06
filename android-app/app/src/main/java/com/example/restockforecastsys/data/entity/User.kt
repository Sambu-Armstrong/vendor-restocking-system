package com.example.restockforecastsys.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = Role::class,
            parentColumns = ["roleId"],
            childColumns = ["roleId"]
        )
    ],
    indices = [Index(value = ["roleId"])]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val userId: Int = 0,
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val dateRegistered: Long,
    val roleId: Int
)
