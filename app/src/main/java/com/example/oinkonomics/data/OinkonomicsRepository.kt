package com.example.oinkonomics.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate

class OinkonomicsRepository(context: Context) {

    private val dao = OinkonomicsDatabase.getInstance(context.applicationContext).oinkonomicsDao()

    suspend fun getBudgetCategories(userId: Long): List<BudgetCategory> = withContext(Dispatchers.IO) {
        dao.getCategories(userId)
    }

    suspend fun createBudgetCategory(userId: Long, name: String, maxAmount: Double, spentAmount: Double = 0.0): BudgetCategory =
        withContext(Dispatchers.IO) {
            val category = BudgetCategory(
                userId = userId,
                name = name,
                maxAmount = maxAmount,
                spentAmount = spentAmount
            )
            val id = dao.insertCategory(category)
            category.copy(id = id)
        }

    suspend fun updateBudgetCategory(category: BudgetCategory) = withContext(Dispatchers.IO) {
        dao.updateCategory(category)
    }

    suspend fun deleteBudgetCategory(categoryId: Long, userId: Long) = withContext(Dispatchers.IO) {
        dao.deleteCategory(categoryId, userId)
    }

    suspend fun getExpenses(userId: Long): List<Expense> = withContext(Dispatchers.IO) {
        dao.getExpenses(userId)
    }

    suspend fun createExpense(
        userId: Long,
        categoryId: Long,
        name: String,
        amount: Double,
        date: LocalDate,
        receiptUri: String?
    ): Expense = withContext(Dispatchers.IO) {
        val expense = Expense(
            userId = userId,
            categoryId = categoryId,
            name = name,
            amount = amount,
            dateIso = date.toString(),
            receiptUri = receiptUri
        )
        val id = dao.insertExpense(expense)
        if (id == -1L) {
            throw IllegalStateException("Unable to persist expense")
        }
        expense.copy(id = id)
    }

    suspend fun updateExpense(expense: Expense): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getExpense(expense.id, expense.userId) ?: return@withContext false
        dao.updateExpense(expense, existing.amount, existing.categoryId)
    }

    suspend fun deleteExpense(expenseId: Long, userId: Long): Boolean = withContext(Dispatchers.IO) {
        dao.deleteExpense(expenseId, userId)
    }

    suspend fun registerUser(username: String, password: String): Result<Long> = withContext(Dispatchers.IO) {
        if (dao.userExists(username)) {
            return@withContext Result.failure(IllegalStateException("Username already taken"))
        }
        val hashed = password.sha256()
        val id = dao.insertUser(User(username = username, passwordHash = hashed))
        if (id == -1L) {
            Result.failure(IllegalStateException("Unable to register user"))
        } else {
            Result.success(id)
        }
    }

    suspend fun authenticate(username: String, password: String): Long? = withContext(Dispatchers.IO) {
        dao.getUserIdIfValid(username, password.sha256())
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(toByteArray())
        return hashBytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
