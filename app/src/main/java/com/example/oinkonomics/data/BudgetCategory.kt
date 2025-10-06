package com.example.oinkonomics.data

// DESCRIBES A USER-DEFINED SPENDING BUCKET.
data class BudgetCategory(
    var id: Long = 0L,
    var userId: Long,
    var name: String,
    var maxAmount: Double,
    var spentAmount: Double = 0.0
)