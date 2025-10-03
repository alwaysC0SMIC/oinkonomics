package com.example.oinkonomics.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlin.math.abs

@Dao
interface OinkonomicsDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: BudgetCategory): Long

    @Update
    suspend fun updateCategory(category: BudgetCategory): Int

    @Query("DELETE FROM budget_categories WHERE id = :categoryId AND user_id = :userId")
    suspend fun deleteCategory(categoryId: Long, userId: Long): Int

    @Query("SELECT * FROM budget_categories WHERE user_id = :userId ORDER BY id ASC")
    suspend fun getCategories(userId: Long): List<BudgetCategory>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpenseRaw(expense: Expense): Long

    @Update
    suspend fun updateExpenseRaw(expense: Expense): Int

    @Query("DELETE FROM expenses WHERE id = :expenseId AND user_id = :userId")
    suspend fun deleteExpenseRaw(expenseId: Long, userId: Long): Int

    @Query("SELECT * FROM expenses WHERE user_id = :userId ORDER BY date_iso DESC, created_at DESC")
    suspend fun getExpenses(userId: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :expenseId AND user_id = :userId LIMIT 1")
    suspend fun getExpense(expenseId: Long, userId: Long): Expense?

    @Query("UPDATE budget_categories SET spent_amount = spent_amount + :delta WHERE id = :categoryId")
    suspend fun adjustCategorySpent(categoryId: Long, delta: Double)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: User): Long

    @Query("SELECT COUNT(*) FROM users WHERE username = :username")
    suspend fun countUsersWithUsername(username: String): Int

    @Query("SELECT id FROM users WHERE username = :username AND password_hash = :passwordHash LIMIT 1")
    suspend fun getUserIdIfValid(username: String, passwordHash: String): Long?

    @Transaction
    suspend fun insertExpense(expense: Expense): Long {
        val id = insertExpenseRaw(expense)
        if (id != -1L) {
            adjustCategorySpent(expense.categoryId, expense.amount)
        }
        return id
    }

    @Transaction
    suspend fun updateExpense(
        expense: Expense,
        originalAmount: Double,
        originalCategoryId: Long
    ): Boolean {
        val updatedRows = updateExpenseRaw(expense)
        if (updatedRows == 0) {
            return false
        }
        if (expense.categoryId != originalCategoryId) {
            adjustCategorySpent(originalCategoryId, -originalAmount)
            adjustCategorySpent(expense.categoryId, expense.amount)
        } else {
            val delta = expense.amount - originalAmount
            if (abs(delta) > 0.000_001) {
                adjustCategorySpent(expense.categoryId, delta)
            }
        }
        return true
    }

    @Transaction
    suspend fun deleteExpense(expenseId: Long, userId: Long): Boolean {
        val existing = getExpense(expenseId, userId) ?: return false
        val deleted = deleteExpenseRaw(expenseId, userId)
        if (deleted == 0) {
            return false
        }
        adjustCategorySpent(existing.categoryId, -existing.amount)
        return true
    }

    suspend fun userExists(username: String): Boolean = countUsersWithUsername(username) > 0
}

@Database(
    entities = [User::class, BudgetCategory::class, Expense::class],
    version = 1,
    exportSchema = false
)
abstract class OinkonomicsDatabase : RoomDatabase() {

    abstract fun oinkonomicsDao(): OinkonomicsDao

    companion object {
        private const val DATABASE_NAME = "oinkonomics.db"

        @Volatile
        private var INSTANCE: OinkonomicsDatabase? = null

        fun getInstance(context: Context): OinkonomicsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OinkonomicsDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
