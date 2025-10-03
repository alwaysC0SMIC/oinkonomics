package com.example.oinkonomics.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class OinkonomicsRepository(context: Context) {

    private val database = OinkonomicsDatabase(context.applicationContext)

    suspend fun getBudgetCategories(userId: Long): List<BudgetCategory> = withContext(Dispatchers.IO) {
        database.getCategories(userId)
    }

    suspend fun createBudgetCategory(userId: Long, name: String, maxAmount: Double, spentAmount: Double = 0.0): BudgetCategory =
        withContext(Dispatchers.IO) {
            val category = BudgetCategory(
                userId = userId,
                name = name,
                maxAmount = maxAmount,
                spentAmount = spentAmount
            )
            val id = database.insertCategory(category)
            category.copy(id = id)
        }

    suspend fun updateBudgetCategory(category: BudgetCategory) = withContext(Dispatchers.IO) {
        database.updateCategory(category)
    }

    suspend fun deleteBudgetCategory(categoryId: Long, userId: Long) = withContext(Dispatchers.IO) {
        database.deleteCategory(categoryId, userId)
    }

    suspend fun registerUser(username: String, password: String): Result<Long> = withContext(Dispatchers.IO) {
        if (database.userExists(username)) {
            return@withContext Result.failure(IllegalStateException("Username already taken"))
        }
        val hashed = password.sha256()
        val id = database.insertUser(username, hashed)
        if (id == -1L) {
            Result.failure(IllegalStateException("Unable to register user"))
        } else {
            Result.success(id)
        }
    }

    suspend fun authenticate(username: String, password: String): Long? = withContext(Dispatchers.IO) {
        database.getUserIdIfValid(username, password.sha256())
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(toByteArray())
        return hashBytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}