package com.example.oinkonomics.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

// WRAPS SHARED PREFERENCES FOR USER SESSION STATE.
class SessionManager(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setLoggedInUser(userId: Long) {
        // PERSISTS THE ACTIVE USER IDENTIFIER.
        preferences.edit {
            putLong(KEY_USER_ID, userId)
        }
    }

    fun getLoggedInUserId(): Long? {
        // RETURNS THE CURRENT USER OR NULL IF NONE IS STORED.
        val stored = preferences.getLong(KEY_USER_ID, DEFAULT_NO_USER)
        return if (stored == DEFAULT_NO_USER) null else stored
    }

    fun clearSession() {
        // REMOVES ANY STORED USER SESSION.
        preferences.edit { remove(KEY_USER_ID) }
    }

    companion object {
        private const val PREFS_NAME = "oinkonomics_prefs"
        private const val KEY_USER_ID = "logged_in_user_id"
        private const val DEFAULT_NO_USER = -1L
    }
}