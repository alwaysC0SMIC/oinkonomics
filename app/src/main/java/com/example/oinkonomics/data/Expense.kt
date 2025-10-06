package com.example.oinkonomics.data

import java.time.LocalDate

/**
 * REPRESENTS A SINGLE EXPENSE ENTRY.
 */
data class Expense(
    val id: Long = 0L,
    val userId: Long,
    val categoryId: Long?,
    val name: String,
    val amount: Double,
    val dateIso: String,
    val receiptUri: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    // EXPOSES THE ISO DATE AS A PARSED LOCALDATE.
    val localDate: LocalDate by lazy { LocalDate.parse(dateIso) }
}
