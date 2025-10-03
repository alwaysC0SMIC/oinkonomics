package com.example.oinkonomics.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Represents a single expense entry recorded by the user.
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BudgetCategory::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("user_id"), Index("category_id")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "amount")
    val amount: Double,
    @ColumnInfo(name = "date_iso")
    val dateIso: String,
    @ColumnInfo(name = "receipt_uri")
    val receiptUri: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    @delegate:Ignore
    val localDate: LocalDate by lazy { LocalDate.parse(dateIso) }
}
