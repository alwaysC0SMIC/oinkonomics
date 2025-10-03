package com.example.oinkonomics.data

import java.time.LocalDate

/**
 * Represents a single expense entry recorded by the user.
 */
data class Expense(
    val id: Long = 0L,
    val userId: Long,
    val categoryId: Long,
    val name: String,
    val amount: Double,
    val dateIso: String,
    val receiptUri: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    val localDate: LocalDate by lazy { LocalDate.parse(dateIso) }
}
