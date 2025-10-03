package com.example.oinkonomics.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budget_categories",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("user_id")]
)
data class BudgetCategory(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    @ColumnInfo(name = "user_id")
    var userId: Long,
    @ColumnInfo(name = "name")
    var name: String,
    @ColumnInfo(name = "max_amount")
    var maxAmount: Double,
    @ColumnInfo(name = "spent_amount", defaultValue = "0")
    var spentAmount: Double = 0.0
)