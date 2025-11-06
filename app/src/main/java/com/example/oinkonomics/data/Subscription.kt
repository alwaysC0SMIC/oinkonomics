package com.example.oinkonomics.data

import java.time.LocalDate

/**
 * REPRESENTS A RECURRING SUBSCRIPTION ENTRY.
 */
data class Subscription(
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val amount: Double,
    val dateIso: String,
    val iconUri: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    // EXPOSES THE ISO DATE AS A PARSED LOCALDATE.
    val localDate: LocalDate by lazy { LocalDate.parse(dateIso) }
}


