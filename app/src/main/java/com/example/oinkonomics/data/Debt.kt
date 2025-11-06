package com.example.oinkonomics.data

import java.time.LocalDate

/**
 * REPRESENTS A DEBT ENTRY WITH TOTAL, PAID, AND DUE DATE.
 */
data class Debt(
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val totalAmount: Double,
    val paidAmount: Double,
    val dueDateIso: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    val dueDate: LocalDate by lazy { LocalDate.parse(dueDateIso) }
    val outstandingAmount: Double get() = (totalAmount - paidAmount).coerceAtLeast(0.0)
    val paidRatio: Double get() = if (totalAmount <= 0) 0.0 else (paidAmount / totalAmount).coerceIn(0.0, 1.0)
}


