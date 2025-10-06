package com.example.oinkonomics

import android.content.Intent
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.oinkonomics.auth.AuthActivity
import com.example.oinkonomics.data.SessionManager
import com.example.oinkonomics.databinding.ActivityMainBinding

// HOSTS THE MAIN NAVIGATION SHELL ONCE A USER IS AUTHENTICATED.
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // SETS UP NAVIGATION ONLY AFTER ENSURING A USER IS LOGGED IN.
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        if (sessionManager.getLoggedInUserId() == null) {
            redirectToAuth()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)
    }

    override fun onResume() {
        // RETURNS USERS TO AUTH FLOW IF THEIR SESSION DISAPPEARS.
        super.onResume()
        if (sessionManager.getLoggedInUserId() == null) {
            redirectToAuth()
        }
    }

    private fun redirectToAuth() {
        // LAUNCHES THE LOGIN EXPERIENCE WHEN NO SESSION EXISTS.
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}