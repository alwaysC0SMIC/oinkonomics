package com.example.oinkonomics.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.max
import kotlin.random.Random

// PROVIDES COROUTINE-FRIENDLY ACCESS TO REMOTE FIRESTORE OPERATIONS.
class OinkonomicsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val firestore: FirebaseFirestore

    init {
        val existingApps = FirebaseApp.getApps(appContext)
        Log.d(TAG, "Initialising Firebase. Existing apps=${existingApps.map { it.name }}")
        if (existingApps.isEmpty()) {
            val options = FirebaseOptions.fromResource(appContext)
            Log.d(TAG, "No cached FirebaseApp found. Options present=${options != null}")
            val app = if (options != null) {
                Log.d(TAG, "Initialising Firebase with explicit FirebaseOptions")
                FirebaseApp.initializeApp(appContext, options)
            } else {
                Log.w(TAG, "FirebaseOptions resource missing. Falling back to google-services.json configuration")
                FirebaseApp.initializeApp(appContext)
            }
            if (app == null) {
                Log.e(TAG, "Firebase initialisation returned null app instance")
                throw IllegalStateException(
                    "Firebase initialization failed. Add a valid google-services.json or FirebaseOptions configuration."
                )
            }
            Log.d(TAG, "FirebaseApp initialised successfully with name=${app.name}")
        } else {
            Log.d(TAG, "Reusing FirebaseApp instance(s): ${existingApps.joinToString { it.name }}")
        }
        firestore = Firebase.firestore
        Log.d(TAG, "FirebaseFirestore instance initialised: ${firestore.app.name}")
    }

    private val usersCollection get() = firestore.collection(COLLECTION_USERS)

    suspend fun getBudgetCategories(userId: Long): List<BudgetCategory> = withContext(Dispatchers.IO) {
        // FETCHES THE USER'S CATEGORY LIST FROM FIRESTORE.
        Log.d(TAG, "Fetching budget categories for userId=$userId")
        ensureValidUser(userId)
        return@withContext try {
            val snapshot = userDocument(userId)
                .collection(COLLECTION_CATEGORIES)
                .get()
                .await()
            val categories = snapshot.documents.mapNotNull { it.toBudgetCategory(userId) }.sortedBy { it.id }
            Log.d(TAG, "Fetched ${categories.size} categories for userId=$userId")
            categories
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to fetch budget categories for userId=$userId", ex)
            throw ex
        }
    }

    suspend fun createBudgetCategory(
        userId: Long,
        name: String,
        maxAmount: Double,
        spentAmount: Double = 0.0
    ): BudgetCategory = withContext(Dispatchers.IO) {
        // CREATES AND RETURNS A NEW CATEGORY DOCUMENT.
        Log.d(TAG, "Creating budget category for userId=$userId name=$name maxAmount=$maxAmount spentAmount=$spentAmount")
        ensureValidUser(userId)
        return@withContext try {
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
            Log.d(TAG, "Created category $categoryId for userId=$userId")
            category
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to create budget category for userId=$userId", ex)
            throw ex
        }
    }

    suspend fun updateBudgetCategory(category: BudgetCategory) = withContext(Dispatchers.IO) {
        // PERSISTS CHANGES TO A CATEGORY DOCUMENT.
        Log.d(TAG, "Updating budget category id=${category.id} for userId=${category.userId}")
        ensureValidUser(category.userId)
        try {
            userDocument(category.userId)
                .collection(COLLECTION_CATEGORIES)
                .document(category.id.toString())
                .set(category.toMap(), SetOptions.merge())
                .await()
            Log.d(TAG, "Updated budget category id=${category.id} for userId=${category.userId}")
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update budget category id=${category.id} for userId=${category.userId}", ex)
            throw ex
        }
    }

    suspend fun deleteBudgetCategory(categoryId: Long, userId: Long) = withContext(Dispatchers.IO) {
        // REMOVES A CATEGORY AND CLEARS REFERENCES IN EXPENSES.
        Log.d(TAG, "Deleting budget category id=$categoryId for userId=$userId")
        ensureValidUser(userId)
        val userDoc = userDocument(userId)
        try {
            firestore.runTransaction { transaction ->
                val categoryRef = userDoc.collection(COLLECTION_CATEGORIES).document(categoryId.toString())
                Log.d(TAG, "Transaction deleting category doc=${categoryRef.path}")
                transaction.delete(categoryRef)
            }.await()
            val expensesSnapshot = userDoc
                .collection(COLLECTION_EXPENSES)
                .whereEqualTo(FIELD_CATEGORY_ID, categoryId)
                .get()
                .await()
            Log.d(TAG, "Found ${expensesSnapshot.size()} expenses referencing categoryId=$categoryId for cleanup")
            expensesSnapshot.documents.forEach { document ->
                Log.d(TAG, "Clearing category reference from expense doc=${document.reference.path}")
                document.reference.update(FIELD_CATEGORY_ID, null).await()
            }
            Log.d(TAG, "Deleted budget category id=$categoryId for userId=$userId")
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to delete budget category id=$categoryId for userId=$userId", ex)
            throw ex
        }
    }

    suspend fun getExpenses(userId: Long): List<Expense> = withContext(Dispatchers.IO) {
        // RETRIEVES ALL EXPENSES BELONGING TO THE USER FROM FIRESTORE.
        Log.d(TAG, "Fetching expenses for userId=$userId")
        ensureValidUser(userId)
        return@withContext try {
            val snapshot = userDocument(userId)
                .collection(COLLECTION_EXPENSES)
                .get()
                .await()
            val expenses = snapshot.documents.mapNotNull { it.toExpense(userId) }
                .sortedWith(compareByDescending<Expense> { it.createdAtEpochMillis }.thenByDescending { it.dateIso })
            Log.d(TAG, "Fetched ${expenses.size} expenses for userId=$userId")
            expenses
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to fetch expenses for userId=$userId", ex)
            throw ex
        }
    }

    suspend fun createExpense(
        userId: Long,
        categoryId: Long?,
        name: String,
        amount: Double,
        date: LocalDate,
        receiptUri: String?
    ): Expense = withContext(Dispatchers.IO) {
        // RECORDS A NEW EXPENSE DOCUMENT AND UPDATES THE CATEGORY TOTALS.
        Log.d(TAG, "Creating expense for userId=$userId categoryId=$categoryId name=$name amount=$amount date=$date receiptUri=$receiptUri")
        ensureValidUser(userId)
        val userDoc = userDocument(userId)
        return@withContext try {
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
                Log.d(TAG, "Transaction creating expense doc=${expenseRef.path}")
                transaction.set(expenseRef, expense.toMap())
                transaction.adjustCategorySpent(userDoc, categoryId, amount)
            }.await()
            Log.d(TAG, "Created expense id=$expenseId for userId=$userId")
            expense
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to create expense for userId=$userId", ex)
            throw ex
        }
    }

    suspend fun updateExpense(expense: Expense): Boolean = withContext(Dispatchers.IO) {
        // APPLIES CHANGES TO AN EXISTING EXPENSE AND ADJUSTS CATEGORY TOTALS.
        Log.d(TAG, "Updating expense id=${expense.id} for userId=${expense.userId}")
        ensureValidUser(expense.userId)
        val userDoc = userDocument(expense.userId)
        val existing = getExpense(expense.id, expense.userId) ?: run {
            Log.w(TAG, "Cannot update expense id=${expense.id}; original document missing for userId=${expense.userId}")
            return@withContext false
        }
        return@withContext try {
            val result = firestore.runTransaction { transaction ->
                val expenseRef = userDoc.collection(COLLECTION_EXPENSES).document(expense.id.toString())
                Log.d(TAG, "Transaction updating expense doc=${expenseRef.path}")
                val snapshot = transaction.get(expenseRef)
                if (!snapshot.exists()) {
                    Log.w(TAG, "Expense document ${expenseRef.path} missing during update")
                    return@runTransaction false
                }
                transaction.set(expenseRef, expense.toMap(), SetOptions.merge())
                if (existing.categoryId != expense.categoryId) {
                    Log.d(TAG, "Expense category changed from ${existing.categoryId} to ${expense.categoryId}")
                    transaction.adjustCategorySpent(userDoc, existing.categoryId, -existing.amount)
                    transaction.adjustCategorySpent(userDoc, expense.categoryId, expense.amount)
                } else {
                    val delta = expense.amount - existing.amount
                    if (delta != 0.0) {
                        Log.d(TAG, "Adjusting category spend by delta=$delta for categoryId=${expense.categoryId}")
                        transaction.adjustCategorySpent(userDoc, expense.categoryId, delta)
                    }
                }
                true
            }.await()
            Log.d(TAG, "Updated expense id=${expense.id} for userId=${expense.userId} result=$result")
            result
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update expense id=${expense.id} for userId=${expense.userId}", ex)
            throw ex
        }
    }

    suspend fun deleteExpense(expenseId: Long, userId: Long): Boolean = withContext(Dispatchers.IO) {
        // REMOVES AN EXPENSE DOCUMENT AND DEDUCTS ITS CATEGORY SPEND.
        Log.d(TAG, "Deleting expense id=$expenseId for userId=$userId")
        ensureValidUser(userId)
        val userDoc = userDocument(userId)
        return@withContext try {
            val result = firestore.runTransaction { transaction ->
                val expenseRef = userDoc.collection(COLLECTION_EXPENSES).document(expenseId.toString())
                Log.d(TAG, "Transaction deleting expense doc=${expenseRef.path}")
                val snapshot = transaction.get(expenseRef)
                if (!snapshot.exists()) {
                    Log.w(TAG, "Expense document ${expenseRef.path} not found during delete")
                    return@runTransaction false
                }
                val expense = snapshot.toExpense(userId) ?: run {
                    Log.w(TAG, "Unable to parse expense snapshot for doc=${expenseRef.path}")
                    return@runTransaction false
                }
                transaction.delete(expenseRef)
                transaction.adjustCategorySpent(userDoc, expense.categoryId, -expense.amount)
                true
            }.await()
            Log.d(TAG, "Deleted expense id=$expenseId for userId=$userId result=$result")
            result
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to delete expense id=$expenseId for userId=$userId", ex)
            throw ex
        }
    }

    suspend fun registerUser(username: String, password: String): Result<Long> = withContext(Dispatchers.IO) {
        // CREATES A USER DOCUMENT IF THE NAME IS AVAILABLE.
        Log.d(TAG, "Registering new user with username=$username")
        return@withContext try {
            val existingUser = usersCollection
                .whereEqualTo(FIELD_USERNAME, username)
                .limit(1)
                .get()
                .await()
            if (!existingUser.isEmpty) {
                Log.w(TAG, "Registration failed: username already taken for username=$username")
                return@withContext Result.failure(IllegalStateException("Username already taken"))
            }
            val hashed = password.sha256()
            var userId: Long
            do {
                userId = generateStableId()
                val doc = userDocument(userId).get().await()
                Log.d(TAG, "Generated candidate userId=$userId exists=${doc.exists()}")
            } while (doc.exists())
            val userData = mapOf(
                FIELD_ID to userId,
                FIELD_USERNAME to username,
                FIELD_PASSWORD to hashed
            )
            userDocument(userId).set(userData).await()
            Log.d(TAG, "Registered new userId=$userId for username=$username")
            Result.success(userId)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to register username=$username", ex)
            throw ex
        }
    }

    suspend fun authenticate(username: String, password: String): Long? = withContext(Dispatchers.IO) {
        // LOOKS UP A USER MATCHING THE PROVIDED CREDENTIALS IN FIRESTORE.
        Log.d(TAG, "Authenticating username=$username")
        return@withContext try {
            val snapshot = usersCollection
                .whereEqualTo(FIELD_USERNAME, username)
                .whereEqualTo(FIELD_PASSWORD, password.sha256())
                .limit(1)
                .get()
                .await()
            val document = snapshot.documents.firstOrNull() ?: run {
                Log.w(TAG, "Authentication failed: no matching user for username=$username")
                return@withContext null
            }
            val id = document.getLong(FIELD_ID) ?: document.id.toLongOrNull()
            Log.d(TAG, "Authentication succeeded for username=$username resolvedUserId=$id")
            id
        } catch (ex: Exception) {
            Log.e(TAG, "Authentication error for username=$username", ex)
            throw ex
        }
    }

    private suspend fun getExpense(expenseId: Long, userId: Long): Expense? {
        Log.d(TAG, "Fetching single expense id=$expenseId for userId=$userId")
        val snapshot = userDocument(userId)
            .collection(COLLECTION_EXPENSES)
            .document(expenseId.toString())
            .get()
            .await()
        return if (snapshot.exists()) {
            Log.d(TAG, "Expense snapshot found for id=$expenseId userId=$userId")
            snapshot.toExpense(userId)
        } else {
            Log.w(TAG, "Expense snapshot missing for id=$expenseId userId=$userId")
            null
        }
    }

    private fun String.sha256(): String {
        // HASHES PLAINTEXT USING SHA-256.
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(toByteArray())
        return hashBytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private suspend fun ensureValidUser(userId: Long) {
        // THROWS IF THE TARGET USER CANNOT BE FOUND IN FIRESTORE.
        Log.d(TAG, "Verifying existence of userId=$userId")
        val exists = userDocument(userId).get().await().exists()
        if (!exists) {
            Log.e(TAG, "UserId=$userId does not exist in Firestore")
            throw MissingUserException()
        }
        Log.d(TAG, "Verified userId=$userId exists")
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

    fun Subscription.toMap(): Map<String, Any?> = mapOf(
        FIELD_ID to id,
        FIELD_USER_ID to userId,
        FIELD_NAME to name,
        FIELD_AMOUNT to amount,
        FIELD_DATE_ISO to dateIso,
        FIELD_ICON_URI to iconUri,
        FIELD_CREATED_AT to createdAtEpochMillis
    )

    fun Debt.toMap(): Map<String, Any?> = mapOf(
        FIELD_ID to id,
        FIELD_USER_ID to userId,
        FIELD_NAME to name,
        FIELD_TOTAL_AMOUNT to totalAmount,
        FIELD_PAID_AMOUNT to paidAmount,
        FIELD_DUE_DATE_ISO to dueDateIso,
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

    private fun DocumentSnapshot.toSubscription(userId: Long): Subscription? {
        val name = getString(FIELD_NAME) ?: return null
        val amount = getNumeric(FIELD_AMOUNT) ?: return null
        val dateIso = getString(FIELD_DATE_ISO) ?: return null
        val createdAt = when (val value = get(FIELD_CREATED_AT)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: System.currentTimeMillis()
        val iconUri = getString(FIELD_ICON_URI)
        return Subscription(
            id = getLong(FIELD_ID) ?: id.toLongOrNull() ?: return null,
            userId = userId,
            name = name,
            amount = amount,
            dateIso = dateIso,
            iconUri = iconUri,
            createdAtEpochMillis = createdAt
        )
    }

    private fun DocumentSnapshot.toDebt(userId: Long): Debt? {
        val name = getString(FIELD_NAME) ?: return null
        val total = getNumeric(FIELD_TOTAL_AMOUNT) ?: return null
        val paid = getNumeric(FIELD_PAID_AMOUNT) ?: 0.0
        val dueIso = getString(FIELD_DUE_DATE_ISO) ?: return null
        val createdAt = when (val value = get(FIELD_CREATED_AT)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: System.currentTimeMillis()
        return Debt(
            id = getLong(FIELD_ID) ?: id.toLongOrNull() ?: return null,
            userId = userId,
            name = name,
            totalAmount = total,
            paidAmount = paid,
            dueDateIso = dueIso,
            createdAtEpochMillis = createdAt
        )
    }

    suspend fun getDebts(userId: Long): List<Debt> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching debts for userId=$userId")
        ensureValidUser(userId)
        val snapshot = userDocument(userId).collection(COLLECTION_DEBTS).get().await()
        snapshot.documents.mapNotNull { it.toDebt(userId) }
    }

    suspend fun createDebt(
        userId: Long,
        name: String,
        totalAmount: Double,
        paidAmount: Double,
        dueDate: LocalDate
    ): Debt = withContext(Dispatchers.IO) {
        Log.d(TAG, "Creating debt for userId=$userId name=$name total=$totalAmount paid=$paidAmount due=$dueDate")
        ensureValidUser(userId)
        val id = generateStableId()
        val debt = Debt(
            id = id,
            userId = userId,
            name = name,
            totalAmount = totalAmount,
            paidAmount = paidAmount,
            dueDateIso = dueDate.toString()
        )
        userDocument(userId).collection(COLLECTION_DEBTS).document(id.toString()).set(debt.toMap()).await()
        debt
    }

    suspend fun updateDebt(debt: Debt): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Updating debt id=${debt.id} for userId=${debt.userId}")
        ensureValidUser(debt.userId)
        val ref = userDocument(debt.userId).collection(COLLECTION_DEBTS).document(debt.id.toString())
        val exists = ref.get().await().exists()
        if (!exists) return@withContext false
        ref.set(debt.toMap(), SetOptions.merge()).await()
        true
    }

    suspend fun deleteDebt(debtId: Long, userId: Long): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Deleting debt id=$debtId for userId=$userId")
        ensureValidUser(userId)
        val ref = userDocument(userId).collection(COLLECTION_DEBTS).document(debtId.toString())
        val exists = ref.get().await().exists()
        if (!exists) return@withContext false
        ref.delete().await()
        true
    }

    suspend fun getSubscriptions(userId: Long): List<Subscription> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching subscriptions for userId=$userId")
        ensureValidUser(userId)
        val snapshot = userDocument(userId)
            .collection(COLLECTION_SUBSCRIPTIONS)
            .get()
            .await()
        snapshot.documents.mapNotNull { it.toSubscription(userId) }
    }

    suspend fun createSubscription(
        userId: Long,
        name: String,
        amount: Double,
        date: LocalDate,
        iconUri: String?
    ): Subscription = withContext(Dispatchers.IO) {
        Log.d(TAG, "Creating subscription for userId=$userId name=$name amount=$amount date=$date")
        ensureValidUser(userId)
        val subId = generateStableId()
        val subscription = Subscription(
            id = subId,
            userId = userId,
            name = name,
            amount = amount,
            dateIso = date.toString(),
            iconUri = iconUri
        )
        userDocument(userId)
            .collection(COLLECTION_SUBSCRIPTIONS)
            .document(subId.toString())
            .set(subscription.toMap())
            .await()
        subscription
    }

    suspend fun updateSubscription(subscription: Subscription): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Updating subscription id=${subscription.id} for userId=${subscription.userId}")
        ensureValidUser(subscription.userId)
        val ref = userDocument(subscription.userId)
            .collection(COLLECTION_SUBSCRIPTIONS)
            .document(subscription.id.toString())
        val exists = ref.get().await().exists()
        if (!exists) return@withContext false
        ref.set(subscription.toMap(), SetOptions.merge()).await()
        true
    }

    suspend fun deleteSubscription(subscriptionId: Long, userId: Long): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Deleting subscription id=$subscriptionId for userId=$userId")
        ensureValidUser(userId)
        val ref = userDocument(userId).collection(COLLECTION_SUBSCRIPTIONS).document(subscriptionId.toString())
        val exists = ref.get().await().exists()
        if (!exists) return@withContext false
        ref.delete().await()
        true
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
        if (categoryId == null || delta == 0.0) {
            Log.d(TAG, "Skipping category spend adjustment. categoryId=$categoryId delta=$delta")
            return
        }
        val categoryRef = userDoc.collection(COLLECTION_CATEGORIES).document(categoryId.toString())
        val snapshot = get(categoryRef)
        if (snapshot.exists()) {
            val current = snapshot.getNumeric(FIELD_SPENT_AMOUNT) ?: 0.0
            val updated = max(0.0, current + delta)
            Log.d(TAG, "Adjusting category spend for doc=${categoryRef.path} current=$current delta=$delta updated=$updated")
            update(categoryRef, FIELD_SPENT_AMOUNT, updated)
        } else {
            Log.w(TAG, "Cannot adjust spend: category document missing for doc=${categoryRef.path}")
        }
    }

    companion object {
        private const val TAG = "OinkonomicsRepository"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_CATEGORIES = "categories"
        private const val COLLECTION_EXPENSES = "expenses"
        private const val COLLECTION_SUBSCRIPTIONS = "subscriptions"
        private const val COLLECTION_DEBTS = "debts"

        private const val FIELD_ID = "id"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PASSWORD = "password"
        private const val FIELD_NAME = "name"
        private const val FIELD_MAX_AMOUNT = "maxAmount"
        private const val FIELD_SPENT_AMOUNT = "spentAmount"
        private const val FIELD_CATEGORY_ID = "categoryId"
        private const val FIELD_AMOUNT = "amount"
        private const val FIELD_DATE_ISO = "dateIso"
        private const val FIELD_RECEIPT_URI = "receiptUri"
        private const val FIELD_ICON_URI = "iconUri"
        private const val FIELD_CREATED_AT = "createdAtEpochMillis"
        private const val FIELD_TOTAL_AMOUNT = "totalAmount"
        private const val FIELD_PAID_AMOUNT = "paidAmount"
        private const val FIELD_DUE_DATE_ISO = "dueDateIso"
    }
}
