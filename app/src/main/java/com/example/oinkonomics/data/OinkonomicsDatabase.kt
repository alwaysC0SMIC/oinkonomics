package com.example.oinkonomics.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class OinkonomicsDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_NAME TEXT NOT NULL UNIQUE,
                $COLUMN_USER_PASSWORD TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_BUDGET_CATEGORIES (
                $COLUMN_CATEGORY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CATEGORY_USER_ID INTEGER NOT NULL,
                $COLUMN_CATEGORY_NAME TEXT NOT NULL,
                $COLUMN_CATEGORY_MAX REAL NOT NULL,
                $COLUMN_CATEGORY_SPENT REAL NOT NULL DEFAULT 0,
                FOREIGN KEY ($COLUMN_CATEGORY_USER_ID) REFERENCES $TABLE_USERS($COLUMN_USER_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_EXPENSES (
                $COLUMN_EXPENSE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_EXPENSE_USER_ID INTEGER NOT NULL,
                $COLUMN_EXPENSE_CATEGORY_ID INTEGER NOT NULL,
                $COLUMN_EXPENSE_NAME TEXT NOT NULL,
                $COLUMN_EXPENSE_AMOUNT REAL NOT NULL,
                $COLUMN_EXPENSE_DATE TEXT NOT NULL,
                $COLUMN_EXPENSE_RECEIPT_URI TEXT,
                $COLUMN_EXPENSE_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_EXPENSE_USER_ID) REFERENCES $TABLE_USERS($COLUMN_USER_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COLUMN_EXPENSE_CATEGORY_ID) REFERENCES $TABLE_BUDGET_CATEGORIES($COLUMN_CATEGORY_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != newVersion) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_EXPENSES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_BUDGET_CATEGORIES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
            onCreate(db)
        }
    }

    fun insertCategory(category: BudgetCategory): Long {
        val values = ContentValues().apply {
            put(COLUMN_CATEGORY_USER_ID, category.userId)
            put(COLUMN_CATEGORY_NAME, category.name)
            put(COLUMN_CATEGORY_MAX, category.maxAmount)
            put(COLUMN_CATEGORY_SPENT, category.spentAmount)
        }
        return writableDatabase.insert(TABLE_BUDGET_CATEGORIES, null, values)
    }

    fun userExists(userId: Long): Boolean {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_USER_ID),
            "$COLUMN_USER_ID = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null
        )
        cursor.use {
            return it.moveToFirst()
        }
    }

    fun updateCategory(category: BudgetCategory): Int {
        val values = ContentValues().apply {
            put(COLUMN_CATEGORY_NAME, category.name)
            put(COLUMN_CATEGORY_MAX, category.maxAmount)
            put(COLUMN_CATEGORY_SPENT, category.spentAmount)
        }
        return writableDatabase.update(
            TABLE_BUDGET_CATEGORIES,
            values,
            "$COLUMN_CATEGORY_ID = ? AND $COLUMN_CATEGORY_USER_ID = ?",
            arrayOf(category.id.toString(), category.userId.toString())
        )
    }

    fun deleteCategory(categoryId: Long, userId: Long): Int {
        return writableDatabase.delete(
            TABLE_BUDGET_CATEGORIES,
            "$COLUMN_CATEGORY_ID = ? AND $COLUMN_CATEGORY_USER_ID = ?",
            arrayOf(categoryId.toString(), userId.toString())
        )
    }

    fun getCategories(userId: Long): List<BudgetCategory> {
        val categories = mutableListOf<BudgetCategory>()
        val cursor: Cursor = readableDatabase.query(
            TABLE_BUDGET_CATEGORIES,
            arrayOf(
                COLUMN_CATEGORY_ID,
                COLUMN_CATEGORY_USER_ID,
                COLUMN_CATEGORY_NAME,
                COLUMN_CATEGORY_MAX,
                COLUMN_CATEGORY_SPENT
            ),
            "$COLUMN_CATEGORY_USER_ID = ?",
            arrayOf(userId.toString()),
            null,
            null,
            "$COLUMN_CATEGORY_ID ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                categories.add(
                    BudgetCategory(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_CATEGORY_ID)),
                        userId = it.getLong(it.getColumnIndexOrThrow(COLUMN_CATEGORY_USER_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(COLUMN_CATEGORY_NAME)),
                        maxAmount = it.getDouble(it.getColumnIndexOrThrow(COLUMN_CATEGORY_MAX)),
                        spentAmount = it.getDouble(it.getColumnIndexOrThrow(COLUMN_CATEGORY_SPENT))
                    )
                )
            }
        }
        return categories
    }

    fun getCategory(categoryId: Long, userId: Long): BudgetCategory? {
        val cursor = readableDatabase.query(
            TABLE_BUDGET_CATEGORIES,
            arrayOf(
                COLUMN_CATEGORY_ID,
                COLUMN_CATEGORY_USER_ID,
                COLUMN_CATEGORY_NAME,
                COLUMN_CATEGORY_MAX,
                COLUMN_CATEGORY_SPENT
            ),
            "$COLUMN_CATEGORY_ID = ? AND $COLUMN_CATEGORY_USER_ID = ?",
            arrayOf(categoryId.toString(), userId.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return BudgetCategory(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_CATEGORY_ID)),
                    userId = it.getLong(it.getColumnIndexOrThrow(COLUMN_CATEGORY_USER_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(COLUMN_CATEGORY_NAME)),
                    maxAmount = it.getDouble(it.getColumnIndexOrThrow(COLUMN_CATEGORY_MAX)),
                    spentAmount = it.getDouble(it.getColumnIndexOrThrow(COLUMN_CATEGORY_SPENT))
                )
            }
        }
        return null
    }

    fun insertExpense(expense: Expense): Long {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val values = ContentValues().apply {
                put(COLUMN_EXPENSE_USER_ID, expense.userId)
                put(COLUMN_EXPENSE_CATEGORY_ID, expense.categoryId)
                put(COLUMN_EXPENSE_NAME, expense.name)
                put(COLUMN_EXPENSE_AMOUNT, expense.amount)
                put(COLUMN_EXPENSE_DATE, expense.dateIso)
                put(COLUMN_EXPENSE_RECEIPT_URI, expense.receiptUri)
                put(COLUMN_EXPENSE_CREATED_AT, expense.createdAtEpochMillis)
            }
            val id = db.insert(TABLE_EXPENSES, null, values)
            if (id != -1L) {
                adjustCategorySpent(db, expense.categoryId, expense.amount)
            }
            db.setTransactionSuccessful()
            id
        } finally {
            db.endTransaction()
        }
    }

    fun updateExpense(expense: Expense, originalAmount: Double, originalCategoryId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val values = ContentValues().apply {
                put(COLUMN_EXPENSE_CATEGORY_ID, expense.categoryId)
                put(COLUMN_EXPENSE_NAME, expense.name)
                put(COLUMN_EXPENSE_AMOUNT, expense.amount)
                put(COLUMN_EXPENSE_DATE, expense.dateIso)
                put(COLUMN_EXPENSE_RECEIPT_URI, expense.receiptUri)
            }
            val updatedRows = db.update(
                TABLE_EXPENSES,
                values,
                "$COLUMN_EXPENSE_ID = ? AND $COLUMN_EXPENSE_USER_ID = ?",
                arrayOf(expense.id.toString(), expense.userId.toString())
            )
            if (updatedRows > 0) {
                if (originalCategoryId != expense.categoryId) {
                    adjustCategorySpent(db, originalCategoryId, -originalAmount)
                    adjustCategorySpent(db, expense.categoryId, expense.amount)
                } else {
                    adjustCategorySpent(db, expense.categoryId, expense.amount - originalAmount)
                }
            }
            db.setTransactionSuccessful()
            updatedRows > 0
        } finally {
            db.endTransaction()
        }
    }

    fun deleteExpense(expenseId: Long, userId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val existing = getExpenseInternal(db, expenseId, userId)
            if (existing == null) {
                db.setTransactionSuccessful()
                return false
            }
            val deletedRows = db.delete(
                TABLE_EXPENSES,
                "$COLUMN_EXPENSE_ID = ? AND $COLUMN_EXPENSE_USER_ID = ?",
                arrayOf(expenseId.toString(), userId.toString())
            )
            if (deletedRows > 0) {
                adjustCategorySpent(db, existing.categoryId, -existing.amount)
            }
            db.setTransactionSuccessful()
            deletedRows > 0
        } finally {
            db.endTransaction()
        }
    }

    fun getExpenses(userId: Long): List<Expense> {
        val expenses = mutableListOf<Expense>()
        val cursor = readableDatabase.query(
            TABLE_EXPENSES,
            arrayOf(
                COLUMN_EXPENSE_ID,
                COLUMN_EXPENSE_USER_ID,
                COLUMN_EXPENSE_CATEGORY_ID,
                COLUMN_EXPENSE_NAME,
                COLUMN_EXPENSE_AMOUNT,
                COLUMN_EXPENSE_DATE,
                COLUMN_EXPENSE_RECEIPT_URI,
                COLUMN_EXPENSE_CREATED_AT
            ),
            "$COLUMN_EXPENSE_USER_ID = ?",
            arrayOf(userId.toString()),
            null,
            null,
            "$COLUMN_EXPENSE_DATE DESC, $COLUMN_EXPENSE_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                expenses.add(it.toExpense())
            }
        }
        return expenses
    }

    fun getExpense(expenseId: Long, userId: Long): Expense? {
        return getExpenseInternal(readableDatabase, expenseId, userId)
    }

    fun insertUser(username: String, hashedPassword: String): Long {
        val values = ContentValues().apply {
            put(COLUMN_USER_NAME, username)
            put(COLUMN_USER_PASSWORD, hashedPassword)
        }
        return writableDatabase.insert(TABLE_USERS, null, values)
    }

    fun getUserIdIfValid(username: String, hashedPassword: String): Long? {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_USER_ID),
            "$COLUMN_USER_NAME = ? AND $COLUMN_USER_PASSWORD = ?",
            arrayOf(username, hashedPassword),
            null,
            null,
            null
        )
        cursor.use {
            return if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    fun userExists(username: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_USER_ID),
            "$COLUMN_USER_NAME = ?",
            arrayOf(username),
            null,
            null,
            null
        )
        cursor.use {
            return it.moveToFirst()
        }
    }

    private fun Cursor.toExpense(): Expense {
        return Expense(
            id = getLong(getColumnIndexOrThrow(COLUMN_EXPENSE_ID)),
            userId = getLong(getColumnIndexOrThrow(COLUMN_EXPENSE_USER_ID)),
            categoryId = getLong(getColumnIndexOrThrow(COLUMN_EXPENSE_CATEGORY_ID)),
            name = getString(getColumnIndexOrThrow(COLUMN_EXPENSE_NAME)),
            amount = getDouble(getColumnIndexOrThrow(COLUMN_EXPENSE_AMOUNT)),
            dateIso = getString(getColumnIndexOrThrow(COLUMN_EXPENSE_DATE)),
            receiptUri = getString(getColumnIndexOrThrow(COLUMN_EXPENSE_RECEIPT_URI)),
            createdAtEpochMillis = getLong(getColumnIndexOrThrow(COLUMN_EXPENSE_CREATED_AT))
        )
    }

    private fun getExpenseInternal(db: SQLiteDatabase, expenseId: Long, userId: Long): Expense? {
        val cursor = db.query(
            TABLE_EXPENSES,
            arrayOf(
                COLUMN_EXPENSE_ID,
                COLUMN_EXPENSE_USER_ID,
                COLUMN_EXPENSE_CATEGORY_ID,
                COLUMN_EXPENSE_NAME,
                COLUMN_EXPENSE_AMOUNT,
                COLUMN_EXPENSE_DATE,
                COLUMN_EXPENSE_RECEIPT_URI,
                COLUMN_EXPENSE_CREATED_AT
            ),
            "$COLUMN_EXPENSE_ID = ? AND $COLUMN_EXPENSE_USER_ID = ?",
            arrayOf(expenseId.toString(), userId.toString()),
            null,
            null,
            null
        )
        cursor.use {
            return if (it.moveToFirst()) it.toExpense() else null
        }
    }

    private fun adjustCategorySpent(db: SQLiteDatabase, categoryId: Long, delta: Double) {
        db.execSQL(
            """
            UPDATE $TABLE_BUDGET_CATEGORIES
            SET $COLUMN_CATEGORY_SPENT = CASE
                WHEN $COLUMN_CATEGORY_SPENT + ? < 0 THEN 0
                ELSE $COLUMN_CATEGORY_SPENT + ?
            END
            WHERE $COLUMN_CATEGORY_ID = ?
            """.trimIndent(),
            arrayOf(delta, delta, categoryId)
        )
    }

    companion object {
        private const val DATABASE_NAME = "oinkonomics.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_BUDGET_CATEGORIES = "budget_categories"
        private const val COLUMN_CATEGORY_ID = "id"
        private const val COLUMN_CATEGORY_USER_ID = "user_id"
        private const val COLUMN_CATEGORY_NAME = "name"
        private const val COLUMN_CATEGORY_MAX = "max_amount"
        private const val COLUMN_CATEGORY_SPENT = "spent_amount"

        private const val TABLE_EXPENSES = "expenses"
        private const val COLUMN_EXPENSE_ID = "id"
        private const val COLUMN_EXPENSE_USER_ID = "user_id"
        private const val COLUMN_EXPENSE_CATEGORY_ID = "category_id"
        private const val COLUMN_EXPENSE_NAME = "name"
        private const val COLUMN_EXPENSE_AMOUNT = "amount"
        private const val COLUMN_EXPENSE_DATE = "expense_date"
        private const val COLUMN_EXPENSE_RECEIPT_URI = "receipt_uri"
        private const val COLUMN_EXPENSE_CREATED_AT = "created_at"

        private const val TABLE_USERS = "users"
        private const val COLUMN_USER_ID = "id"
        private const val COLUMN_USER_NAME = "username"
        private const val COLUMN_USER_PASSWORD = "password"
    }
}
