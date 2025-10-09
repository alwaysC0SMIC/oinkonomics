package com.example.oinkonomics.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.oinkonomics.MainActivity
import com.example.oinkonomics.R
import com.example.oinkonomics.data.OinkonomicsRepository
import com.example.oinkonomics.data.SessionManager
import com.example.oinkonomics.databinding.ActivityAuthBinding
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// HANDLES LOGIN AND REGISTRATION WORKFLOWS.
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var repository: OinkonomicsRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var facebookCallbackManager: CallbackManager
    private val firebaseAuth: FirebaseAuth by lazy { Firebase.auth }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                if (result.resultCode != Activity.RESULT_CANCELED) {
                    showProviderError()
                }
                return@registerForActivityResult
            }

            val data = result.data ?: run {
                showProviderError()
                return@registerForActivityResult
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            lifecycleScope.launch {
                try {
                    val account = task.getResult(ApiException::class.java)
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    signInWithCredential(credential)
                } catch (error: ApiException) {
                    Log.w(TAG, "Google sign-in failed", error)
                    showProviderError()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // PREPARES AUTHENTICATION UI AND SHORT-CIRCUITS IF ALREADY SIGNED IN.
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = OinkonomicsRepository(this)
        sessionManager = SessionManager(this)

        setupGoogleSignIn()
        setupFacebookSignIn()

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

        binding.googleSignInButton.setOnClickListener { startGoogleSignIn() }
        binding.facebookSignInButton.setOnClickListener { startFacebookSignIn() }
        binding.microsoftSignInButton.setOnClickListener { startMicrosoftSignIn() }
        binding.twitterSignInButton.setOnClickListener { startTwitterSignIn() }

        binding.loginButton.setOnClickListener {
            val username = binding.loginUsernameInput.text.toString().trim()
            val password = binding.loginPasswordInput.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.auth_error_missing_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
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
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::facebookCallbackManager.isInitialized) {
            facebookCallbackManager.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::facebookCallbackManager.isInitialized) {
            LoginManager.getInstance().unregisterCallback(facebookCallbackManager)
        }
    }

    private fun setupGoogleSignIn() {
        val webClientId = runCatching { getString(R.string.default_web_client_id) }.getOrNull()
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        if (!webClientId.isNullOrBlank()) {
            builder.requestIdToken(webClientId)
        }
        googleSignInClient = GoogleSignIn.getClient(this, builder.build())
    }

    private fun setupFacebookSignIn() {
        facebookCallbackManager = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(
            facebookCallbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    lifecycleScope.launch {
                        val credential = FacebookAuthProvider.getCredential(result.accessToken.token)
                        signInWithCredential(credential)
                    }
                }

                override fun onCancel() {
                    // NO-OP: USER CANCELLED SIGN-IN.
                }

                override fun onError(error: FacebookException) {
                    Log.w(TAG, "Facebook sign-in failed", error)
                    showProviderError()
                }
            }
        )
    }

    private fun startGoogleSignIn() {
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun startFacebookSignIn() {
        if (!isFacebookConfigured()) {
            showProviderNotConfigured()
            return
        }
        LoginManager.getInstance().logInWithReadPermissions(this, listOf("email"))
    }

    private fun startMicrosoftSignIn() {
        launchOAuthSignIn("microsoft.com")
    }

    private fun startTwitterSignIn() {
        launchOAuthSignIn("twitter.com")
    }

    private fun launchOAuthSignIn(providerId: String, scopes: List<String> = emptyList()) {
        lifecycleScope.launch {
            try {
                val result = startOAuthFlow(providerId, scopes)
                val user = result.user
                if (user != null) {
                    finalizeFirebaseSignIn(user)
                } else {
                    showProviderError()
                }
            } catch (error: Exception) {
                if (isProviderNotConfigured(error)) {
                    showProviderNotConfigured()
                } else {
                    Log.w(TAG, "OAuth sign-in failed for $providerId", error)
                    showProviderError()
                }
            }
        }
    }

    private suspend fun startOAuthFlow(providerId: String, scopes: List<String>): AuthResult {
        val pending = firebaseAuth.pendingAuthResult
        if (pending != null) {
            return pending.await()
        }
        val providerBuilder = OAuthProvider.newBuilder(providerId)
        if (scopes.isNotEmpty()) {
            providerBuilder.scopes = scopes
        }
        val provider = providerBuilder.build()
        return firebaseAuth.startActivityForSignInWithProvider(this, provider).await()
    }

    private suspend fun signInWithCredential(credential: AuthCredential) {
        try {
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                finalizeFirebaseSignIn(user)
            } else {
                showProviderError()
            }
        } catch (error: Exception) {
            if (isProviderNotConfigured(error)) {
                showProviderNotConfigured()
            } else {
                Log.w(TAG, "Credential sign-in failed", error)
                showProviderError()
            }
        }
    }

    private suspend fun finalizeFirebaseSignIn(user: FirebaseUser) {
        try {
            val userId = repository.ensureUserForCurrentAuth()
            sessionManager.setLoggedInUser(userId)
            launchMain()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to bind Firebase user ${user.uid} to repository", error)
            showProviderError()
        }
    }

    private fun showProviderError() {
        Toast.makeText(this, getString(R.string.auth_error_provider_failed), Toast.LENGTH_SHORT).show()
    }

    private fun showProviderNotConfigured() {
        Toast.makeText(this, getString(R.string.auth_error_provider_not_configured), Toast.LENGTH_SHORT).show()
    }

    private fun isProviderNotConfigured(error: Exception): Boolean {
        return error is FirebaseAuthException && error.errorCode == ERROR_OPERATION_NOT_ALLOWED
    }

    private fun isFacebookConfigured(): Boolean {
        val appId = getString(R.string.facebook_app_id)
        return appId.isNotBlank() && !appId.contains("PLACEHOLDER", ignoreCase = true)
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

    companion object {
        private const val TAG = "AuthActivity"
        private const val ERROR_OPERATION_NOT_ALLOWED = "ERROR_OPERATION_NOT_ALLOWED"
    }
}