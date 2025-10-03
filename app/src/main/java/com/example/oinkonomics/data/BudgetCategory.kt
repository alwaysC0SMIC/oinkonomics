package com.example.oinkonomics.data

data class BudgetCategory(
    var id: Long = 0L,
    var userId: Long,
    var name: String,
    var maxAmount: Double,
    var spentAmount: Double = 0.0
)