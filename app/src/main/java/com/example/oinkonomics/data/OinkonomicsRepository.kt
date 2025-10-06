package com.example.oinkonomics.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate

// PROVIDES COROUTINE-FRIENDLY ACCESS TO DATABASE OPERATIONS.
class OinkonomicsRepository(context: Context) {

    private val database = OinkonomicsDatabase(context.applicationContext)

    suspend fun getBudgetCategories(userId: Long): List<BudgetCategory> = withContext(Dispatchers.IO) {
        // FETCHES THE USER'S CATEGORY LIST.
        ensureValidUser(userId)
        database.getCategories(userId)
    }

    suspend fun createBudgetCategory(userId: Long, name: String, maxAmount: Double, spentAmount: Double = 0.0): BudgetCategory =
        withContext(Dispatchers.IO) {
            // CREATES AND RETURNS A NEW CATEGORY RECORD.
            ensureValidUser(userId)
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
        // PERSISTS CHANGES TO A CATEGORY.
        ensureValidUser(category.userId)
        database.updateCategory(category)
    }

    suspend fun deleteBudgetCategory(categoryId: Long, userId: Long) = withContext(Dispatchers.IO) {
        // REMOVES A CATEGORY FOR THE USER.
        ensureValidUser(userId)
        database.deleteCategory(categoryId, userId)
    }

    suspend fun getExpenses(userId: Long): List<Expense> = withContext(Dispatchers.IO) {
        // RETRIEVES ALL EXPENSES BELONGING TO THE USER.
        ensureValidUser(userId)
        database.getExpenses(userId)
    }

    suspend fun createExpense(
        userId: Long,
        categoryId: Long?,
        name: String,
        amount: Double,
        date: LocalDate,
        receiptUri: String?
    ): Expense = withContext(Dispatchers.IO) {
        // RECORDS A NEW EXPENSE AND RETURNS THE SAVED OBJECT.
        ensureValidUser(userId)
        val expense = Expense(
            userId = userId,
            categoryId = categoryId,
            name = name,
            amount = amount,
            dateIso = date.toString(),
            receiptUri = receiptUri
        )
        val id = database.insertExpense(expense)
        if (id == -1L) {
            throw IllegalStateException("Unable to persist expense")
        }
        expense.copy(id = id)
    }

    suspend fun updateExpense(expense: Expense): Boolean = withContext(Dispatchers.IO) {
        // APPLIES CHANGES TO AN EXISTING EXPENSE.
        ensureValidUser(expense.userId)
        val existing = database.getExpense(expense.id, expense.userId) ?: return@withContext false
        database.updateExpense(expense, existing.amount, existing.categoryId)
    }

    suspend fun deleteExpense(expenseId: Long, userId: Long): Boolean = withContext(Dispatchers.IO) {
        // REMOVES AN EXPENSE FOR THE USER.
        ensureValidUser(userId)
        database.deleteExpense(expenseId, userId)
    }

    suspend fun registerUser(username: String, password: String): Result<Long> = withContext(Dispatchers.IO) {
        // CREATES A USER ACCOUNT IF THE NAME IS AVAILABLE.
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
        // LOOKS UP A USER MATCHING THE PROVIDED CREDENTIALS.
        database.getUserIdIfValid(username, password.sha256())
    }

    private fun String.sha256(): String {
        // HASHES PLAINTEXT USING SHA-256.
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(toByteArray())
        return hashBytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun ensureValidUser(userId: Long) {
        // THROWS IF THE TARGET USER CANNOT BE FOUND.
        if (!database.userExistsById(userId)) {
            throw MissingUserException()
        }
    }
}
