package com.example.oinkonomics.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.oinkonomics.MainActivity
import com.example.oinkonomics.R
import com.example.oinkonomics.data.OinkonomicsRepository
import com.example.oinkonomics.data.SessionManager
import com.example.oinkonomics.databinding.ActivityAuthBinding
import kotlinx.coroutines.launch

// HANDLES LOGIN AND REGISTRATION WORKFLOWS.
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var repository: OinkonomicsRepository
    private lateinit var sessionManager: SessionManager

    companion object {
        private const val TAG = "AuthActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // PREPARES AUTHENTICATION UI AND SHORT-CIRCUITS IF ALREADY SIGNED IN.
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = OinkonomicsRepository(this)
        sessionManager = SessionManager(this)

        sessionManager.getLoggedInUserId()?.let {
            launchMain()
            return
        }

        showLoginForm()
        configureListeners()
    }

    private fun configureListeners() {
        // WIRES BUTTON INTERACTIONS FOR LOGIN AND REGISTRATION FORMS.
        binding.showRegisterButton.setOnClickListener { showRegisterForm() }
        binding.showLoginButton.setOnClickListener { showLoginForm() }

        binding.loginButton.setOnClickListener {
            val username = binding.loginUsernameInput.text.toString().trim()
            val password = binding.loginPasswordInput.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.auth_error_missing_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    // ATTEMPTS LOGIN AND STORES SESSION ON SUCCESS.
                    val userId = repository.authenticate(username, password)
                    if (userId != null) {
                        sessionManager.setLoggedInUser(userId)
                        launchMain()
                    } else {
                        Toast.makeText(
                            this@AuthActivity,
                            getString(R.string.auth_error_login_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Login failed", ex)
                    Toast.makeText(
                        this@AuthActivity,
                        getString(R.string.auth_error_login_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.registerButton.setOnClickListener {
            val username = binding.registerUsernameInput.text.toString().trim()
            val password = binding.registerPasswordInput.text.toString()
            val confirm = binding.registerConfirmInput.text.toString()

            if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, getString(R.string.auth_error_missing_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirm) {
                Toast.makeText(this, getString(R.string.auth_error_password_mismatch), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    // CREATES A NEW ACCOUNT AND OPENS MAIN FLOW ON SUCCESS.
                    val result = repository.registerUser(username, password)
                    result.onSuccess { userId ->
                        sessionManager.setLoggedInUser(userId)
                        Toast.makeText(
                            this@AuthActivity,
                            getString(R.string.auth_success_registered),
                            Toast.LENGTH_SHORT
                        ).show()
                        launchMain()
                    }.onFailure {
                        Toast.makeText(
                            this@AuthActivity,
                            getString(R.string.auth_error_username_taken),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Registration failed", ex)
                    Toast.makeText(
                        this@AuthActivity,
                        getString(R.string.auth_error_username_taken),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun launchMain() {
        // MOVES USERS TO THE PRIMARY APP EXPERIENCE.
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoginForm() {
        // DISPLAYS LOGIN FIELDS AND HIDES REGISTRATION UI.
        binding.loginContainer.visibility = View.VISIBLE
        binding.registerContainer.visibility = View.GONE
    }

    private fun showRegisterForm() {
        // DISPLAYS REGISTRATION FIELDS AND HIDES LOGIN UI.
        binding.loginContainer.visibility = View.GONE
        binding.registerContainer.visibility = View.VISIBLE
    }
}