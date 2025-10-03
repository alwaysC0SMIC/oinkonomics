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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != newVersion) {
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

    companion object {
        private const val DATABASE_NAME = "oinkonomics.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_BUDGET_CATEGORIES = "budget_categories"
        private const val COLUMN_CATEGORY_ID = "id"
        private const val COLUMN_CATEGORY_USER_ID = "user_id"
        private const val COLUMN_CATEGORY_NAME = "name"
        private const val COLUMN_CATEGORY_MAX = "max_amount"
        private const val COLUMN_CATEGORY_SPENT = "spent_amount"

        private const val TABLE_USERS = "users"
        private const val COLUMN_USER_ID = "id"
        private const val COLUMN_USER_NAME = "username"
        private const val COLUMN_USER_PASSWORD = "password"
    }
}
