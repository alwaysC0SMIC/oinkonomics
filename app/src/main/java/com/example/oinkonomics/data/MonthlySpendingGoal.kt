package com.example.oinkonomics.data

/**
 * REPRESENTS MONTHLY SPENDING GOALS (MINIMUM AND MAXIMUM).
 */
data class MonthlySpendingGoal(
    val userId: Long,
    val minGoal: Double? = null,
    val maxGoal: Double? = null
)

