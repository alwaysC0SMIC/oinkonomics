package com.example.oinkonomics.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "password_hash")
    val passwordHash: String
)
