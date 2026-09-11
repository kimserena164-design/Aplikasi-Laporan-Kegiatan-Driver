package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val location: String,
    val date: String = "",
    val description: String,
    val imageUri: String?,
    val videoUri: String?,
    val timestamp: Long = System.currentTimeMillis()
)
