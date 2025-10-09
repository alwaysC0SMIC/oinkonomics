package com.example.oinkonomics.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.max
import kotlin.random.Random

// PROVIDES COROUTINE-FRIENDLY ACCESS TO REMOTE FIRESTORE OPERATIONS.
class OinkonomicsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val firestore: FirebaseFirestore
    private val auth = Firebase.auth
    private val authMutex = Mutex()

    init {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            val options = FirebaseOptions.fromResource(appContext)
            val app = if (options != null) {
                FirebaseApp.initializeApp(appContext, options)
            } else {
                FirebaseApp.initializeApp(appContext)
            }
            if (app == null) {
                throw IllegalStateException(
                    "Firebase initialization failed. Add a valid google-services.json or FirebaseOptions configuration."
                )
            }
        }
        firestore = Firebase.firestore
    }

    private val usersCollection get() = firestore.collection(COLLECTION_USERS)

    suspend fun getBudgetCategories(userId: Long): List<BudgetCategory> = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // FETCHES THE USER'S CATEGORY LIST FROM FIRESTORE.
        ensureValidUser(userId)
        val snapshot = userDocument(userId)
            .collection(COLLECTION_CATEGORIES)
            .get()
            .await()
        snapshot.documents.mapNotNull { it.toBudgetCategory(userId) }.sortedBy { it.id }
    }

    suspend fun createBudgetCategory(
        userId: Long,
        name: String,
        maxAmount: Double,
        spentAmount: Double = 0.0
    ): BudgetCategory = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // CREATES AND RETURNS A NEW CATEGORY DOCUMENT.
        ensureValidUser(userId)
        val categoryId = generateStableId()
        val category = BudgetCategory(
            id = categoryId,
            userId = userId,
            name = name,
            maxAmount = maxAmount,
            spentAmount = spentAmount
        )
        userDocument(userId)
            .collection(COLLECTION_CATEGORIES)
            .document(categoryId.toString())
            .set(category.toMap())
            .await()
        category
    }

    suspend fun updateBudgetCategory(category: BudgetCategory) = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // PERSISTS CHANGES TO A CATEGORY DOCUMENT.
        ensureValidUser(category.userId)
        userDocument(category.userId)
            .collection(COLLECTION_CATEGORIES)
            .document(category.id.toString())
            .set(category.toMap(), SetOptions.merge())
            .await()
    }

    suspend fun deleteBudgetCategory(categoryId: Long, userId: Long) = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // REMOVES A CATEGORY AND CLEARS REFERENCES IN EXPENSES.
        ensureValidUser(userId)
        val userDoc = userDocument(userId)
        firestore.runTransaction { transaction ->
            val categoryRef = userDoc.collection(COLLECTION_CATEGORIES).document(categoryId.toString())
            transaction.delete(categoryRef)
        }.await()
        val expensesSnapshot = userDoc
            .collection(COLLECTION_EXPENSES)
            .whereEqualTo(FIELD_CATEGORY_ID, categoryId)
            .get()
            .await()
        expensesSnapshot.documents.forEach { document ->
            document.reference.update(FIELD_CATEGORY_ID, null).await()
        }
    }

    suspend fun getExpenses(userId: Long): List<Expense> = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // RETRIEVES ALL EXPENSES BELONGING TO THE USER FROM FIRESTORE.
        ensureValidUser(userId)
        val snapshot = userDocument(userId)
            .collection(COLLECTION_EXPENSES)
            .get()
            .await()
        snapshot.documents.mapNotNull { it.toExpense(userId) }
            .sortedWith(compareByDescending<Expense> { it.createdAtEpochMillis }.thenByDescending { it.dateIso })
    }

    suspend fun createExpense(
        userId: Long,
        categoryId: Long?,
        name: String,
        amount: Double,
        date: LocalDate,
        receiptUri: String?
    ): Expense = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // RECORDS A NEW EXPENSE DOCUMENT AND UPDATES THE CATEGORY TOTALS.
        ensureValidUser(userId)
        val userDoc = userDocument(userId)
        val expenseId = generateStableId()
        val createdAt = System.currentTimeMillis()
        val expense = Expense(
            id = expenseId,
            userId = userId,
            categoryId = categoryId,
            name = name,
            amount = amount,
            dateIso = date.toString(),
            receiptUri = receiptUri,
            createdAtEpochMillis = createdAt
        )
        firestore.runTransaction { transaction ->
            val expenseRef = userDoc.collection(COLLECTION_EXPENSES).document(expenseId.toString())
            transaction.set(expenseRef, expense.toMap())
            transaction.adjustCategorySpent(userDoc, categoryId, amount)
        }.await()
        expense
    }

    suspend fun updateExpense(expense: Expense): Boolean = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // APPLIES CHANGES TO AN EXISTING EXPENSE AND ADJUSTS CATEGORY TOTALS.
        ensureValidUser(expense.userId)
        val userDoc = userDocument(expense.userId)
        val existing = getExpense(expense.id, expense.userId) ?: return@withContext false
        firestore.runTransaction { transaction ->
            val expenseRef = userDoc.collection(COLLECTION_EXPENSES).document(expense.id.toString())
            val snapshot = transaction.get(expenseRef)
            if (!snapshot.exists()) {
                return@runTransaction false
            }
            transaction.set(expenseRef, expense.toMap(), SetOptions.merge())
            if (existing.categoryId != expense.categoryId) {
                transaction.adjustCategorySpent(userDoc, existing.categoryId, -existing.amount)
                transaction.adjustCategorySpent(userDoc, expense.categoryId, expense.amount)
            } else {
                val delta = expense.amount - existing.amount
                if (delta != 0.0) {
                    transaction.adjustCategorySpent(userDoc, expense.categoryId, delta)
                }
            }
            true
        }.await()
    }

    suspend fun deleteExpense(expenseId: Long, userId: Long): Boolean = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // REMOVES AN EXPENSE DOCUMENT AND DEDUCTS ITS CATEGORY SPEND.
        ensureValidUser(userId)
        val userDoc = userDocument(userId)
        firestore.runTransaction { transaction ->
            val expenseRef = userDoc.collection(COLLECTION_EXPENSES).document(expenseId.toString())
            val snapshot = transaction.get(expenseRef)
            if (!snapshot.exists()) {
                return@runTransaction false
            }
            val expense = snapshot.toExpense(userId) ?: return@runTransaction false
            transaction.delete(expenseRef)
            transaction.adjustCategorySpent(userDoc, expense.categoryId, -expense.amount)
            true
        }.await()
    }

    suspend fun registerUser(username: String, password: String): Result<Long> = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // CREATES A USER DOCUMENT IF THE NAME IS AVAILABLE.
        val existingUser = usersCollection
            .whereEqualTo(FIELD_USERNAME, username)
            .limit(1)
            .get()
            .await()
        if (!existingUser.isEmpty) {
            return@withContext Result.failure(IllegalStateException("Username already taken"))
        }
        val hashed = password.sha256()
        var userId: Long
        do {
            userId = generateStableId()
            val doc = userDocument(userId).get().await()
        } while (doc.exists())
        val userData = mutableMapOf<String, Any?>(
            FIELD_ID to userId,
            FIELD_USERNAME to username,
            FIELD_PASSWORD to hashed,
            FIELD_EMAIL to username,
            FIELD_DISPLAY_NAME to username
        )
        userDocument(userId).set(userData).await()
        Result.success(userId)
    }

    suspend fun authenticate(username: String, password: String): Long? = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        // LOOKS UP A USER MATCHING THE PROVIDED CREDENTIALS IN FIRESTORE.
        val snapshot = usersCollection
            .whereEqualTo(FIELD_USERNAME, username)
            .whereEqualTo(FIELD_PASSWORD, password.sha256())
            .limit(1)
            .get()
            .await()
        val document = snapshot.documents.firstOrNull() ?: return@withContext null
        (document.getLong(FIELD_ID) ?: document.id.toLongOrNull())
    }

    suspend fun ensureUserForCurrentAuth(): Long = withContext(Dispatchers.IO) {
        val current = auth.currentUser
            ?: throw IllegalStateException("No Firebase user is currently signed in.")

        val existingUser = usersCollection
            .whereEqualTo(FIELD_AUTH_UID, current.uid)
            .limit(1)
            .get()
            .await()

        val document = existingUser.documents.firstOrNull()
        if (document != null) {
            return@withContext document.getLong(FIELD_ID)
                ?: document.id.toLongOrNull()
                ?: throw IllegalStateException("User document missing identifier.")
        }

        var userId: Long
        do {
            userId = generateStableId()
            val snapshot = userDocument(userId).get().await()
        } while (snapshot.exists())

        val userData = mutableMapOf<String, Any?>(
            FIELD_ID to userId,
            FIELD_AUTH_UID to current.uid
        )

        current.email?.let {
            userData[FIELD_EMAIL] = it
            userData[FIELD_USERNAME] = it
        }
        current.displayName?.let { displayName ->
            userData[FIELD_DISPLAY_NAME] = displayName
            userData.putIfAbsent(FIELD_USERNAME, displayName)
        }

        if (!userData.containsKey(FIELD_USERNAME)) {
            userData[FIELD_USERNAME] = current.uid
        }

        userDocument(userId).set(userData).await()
        userId
    }

    private suspend fun getExpense(expenseId: Long, userId: Long): Expense? {
        val snapshot = userDocument(userId)
            .collection(COLLECTION_EXPENSES)
            .document(expenseId.toString())
            .get()
            .await()
        return if (snapshot.exists()) snapshot.toExpense(userId) else null
    }

    private fun String.sha256(): String {
        // HASHES PLAINTEXT USING SHA-256.
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(toByteArray())
        return hashBytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private suspend fun ensureValidUser(userId: Long) {
        // THROWS IF THE TARGET USER CANNOT BE FOUND IN FIRESTORE.
        val exists = userDocument(userId).get().await().exists()
        if (!exists) {
            throw MissingUserException()
        }
    }

    private suspend fun ensureAuthenticated() {
        if (auth.currentUser != null) return
        authMutex.withLock {
            if (auth.currentUser != null) return
            try {
                auth.signInAnonymously().await()
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Firebase anonymous authentication failed. Enable anonymous sign-in or adjust Firestore security rules.",
                    error
                )
            }
        }
    }

    private fun userDocument(userId: Long): DocumentReference =
        usersCollection.document(userId.toString())

    private fun generateStableId(): Long {
        // GENERATES A LARGE PSEUDO-UNIQUE IDENTIFIER SUITABLE FOR FIRESTORE KEYS.
        val timestampComponent = System.currentTimeMillis() shl 20
        val randomComponent = Random.nextInt(1 shl 20)
        return timestampComponent or randomComponent.toLong()
    }

    private fun BudgetCategory.toMap(): Map<String, Any?> = mapOf(
        FIELD_ID to id,
        FIELD_USER_ID to userId,
        FIELD_NAME to name,
        FIELD_MAX_AMOUNT to maxAmount,
        FIELD_SPENT_AMOUNT to spentAmount
    )

    private fun Expense.toMap(): Map<String, Any?> = mapOf(
        FIELD_ID to id,
        FIELD_USER_ID to userId,
        FIELD_CATEGORY_ID to categoryId,
        FIELD_NAME to name,
        FIELD_AMOUNT to amount,
        FIELD_DATE_ISO to dateIso,
        FIELD_RECEIPT_URI to receiptUri,
        FIELD_CREATED_AT to createdAtEpochMillis
    )

    private fun DocumentSnapshot.toBudgetCategory(userId: Long): BudgetCategory? {
        val name = getString(FIELD_NAME) ?: return null
        val maxAmount = getNumeric(FIELD_MAX_AMOUNT) ?: return null
        val spentAmount = getNumeric(FIELD_SPENT_AMOUNT) ?: 0.0
        val idValue = getLong(FIELD_ID) ?: id.toLongOrNull() ?: return null
        return BudgetCategory(
            id = idValue,
            userId = userId,
            name = name,
            maxAmount = maxAmount,
            spentAmount = spentAmount
        )
    }

    private fun DocumentSnapshot.toExpense(userId: Long): Expense? {
        val name = getString(FIELD_NAME) ?: return null
        val amount = getNumeric(FIELD_AMOUNT) ?: return null
        val dateIso = getString(FIELD_DATE_ISO) ?: return null
        val createdAt = when (val value = get(FIELD_CREATED_AT)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: System.currentTimeMillis()
        val categoryId = when (val value = get(FIELD_CATEGORY_ID)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
        return Expense(
            id = getLong(FIELD_ID) ?: id.toLongOrNull() ?: return null,
            userId = userId,
            categoryId = categoryId,
            name = name,
            amount = amount,
            dateIso = dateIso,
            receiptUri = getString(FIELD_RECEIPT_URI),
            createdAtEpochMillis = createdAt
        )
    }

    private fun DocumentSnapshot.getNumeric(fieldName: String): Double? {
        val value = get(fieldName) ?: return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun Transaction.adjustCategorySpent(
        userDoc: DocumentReference,
        categoryId: Long?,
        delta: Double
    ) {
        if (categoryId == null || delta == 0.0) return
        val categoryRef = userDoc.collection(COLLECTION_CATEGORIES).document(categoryId.toString())
        val snapshot = get(categoryRef)
        if (snapshot.exists()) {
            val current = snapshot.getNumeric(FIELD_SPENT_AMOUNT) ?: 0.0
            val updated = max(0.0, current + delta)
            update(categoryRef, FIELD_SPENT_AMOUNT, updated)
        }
    }

    companion object {
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_CATEGORIES = "categories"
        private const val COLLECTION_EXPENSES = "expenses"

        private const val FIELD_ID = "id"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PASSWORD = "password"
        private const val FIELD_AUTH_UID = "authUid"
        private const val FIELD_DISPLAY_NAME = "displayName"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_NAME = "name"
        private const val FIELD_MAX_AMOUNT = "maxAmount"
        private const val FIELD_SPENT_AMOUNT = "spentAmount"
        private const val FIELD_CATEGORY_ID = "categoryId"
        private const val FIELD_AMOUNT = "amount"
        private const val FIELD_DATE_ISO = "dateIso"
        private const val FIELD_RECEIPT_URI = "receiptUri"
        private const val FIELD_CREATED_AT = "createdAtEpochMillis"
    }
}
