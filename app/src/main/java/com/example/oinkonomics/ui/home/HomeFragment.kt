package com.example.oinkonomics.ui.home

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.oinkonomics.R
import com.example.oinkonomics.auth.AuthActivity
import com.example.oinkonomics.data.BudgetCategory
import com.example.oinkonomics.data.Expense
import com.example.oinkonomics.data.MonthlySpendingGoal
import com.example.oinkonomics.data.OinkonomicsRepository
import com.example.oinkonomics.data.SessionManager
import com.example.oinkonomics.databinding.FragmentHomeBinding
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.LazyThreadSafetyMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// DRIVES THE HOME TAB WITH EXPENSE LISTS AND SUMMARY STATS.
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val sessionManager by lazy(LazyThreadSafetyMode.NONE) { SessionManager(requireContext()) }
    private lateinit var adapter: HomeListAdapter

    private val dayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
    private val currencyFormatter = DecimalFormat("#,##0.00")
    private val plainFormatter = DecimalFormat("0.##")

    private var pendingReceiptCallback: ((Uri?) -> Unit)? = null
    private val receiptPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // IGNORE IF PERMISSION CANNOT BE PERSISTED (E.G. FROM GALLERY SHORTCUT).
            }
        }
        pendingReceiptCallback?.invoke(uri)
        pendingReceiptCallback = null
    }

    private val viewModel: HomeViewModel by viewModels {
        val userId = sessionManager.getLoggedInUserId() ?: -1
        HomeViewModel.Factory(OinkonomicsRepository(requireContext()), userId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ENSURES A USER SESSION EXISTS BEFORE SHOWING THE SCREEN.
        super.onCreate(savedInstanceState)
        val userId = sessionManager.getLoggedInUserId()
        if (userId == null || userId < 0) {
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // INFLATES THE LAYOUT AND PREPARES LIST AND OBSERVERS.
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupRecyclerView()
        setupMonthlyGoalsEditButton()
        setupDateRangeSelector()
        observeViewModel()
        return binding.root
    }

    override fun onResume() {
        // REFRESHES DATA WHEN RETURNING TO THE FRAGMENT.
        super.onResume()
        viewModel.refreshData()
    }

    private fun setupRecyclerView() {
        // CONFIGURES THE EXPENSE LIST WITH CLICK HANDLERS AND SPACING.
        adapter = HomeListAdapter(
            onAddExpense = { showExpenseDialog(null) },
            onExpenseClicked = { item ->
                val expense = viewModel.uiState.value.expenses.firstOrNull { it.id == item.id }
                if (expense != null) {
                    showExpenseDialog(expense)
                }
            }
        )
        binding.expensesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.expensesRecyclerView.adapter = adapter
        val spacing = (12 * resources.displayMetrics.density).roundToInt()
        binding.expensesRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                // ADDS SPACING BETWEEN LIST ROWS.
                val position = parent.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION) {
                    outRect.bottom = spacing
                }
            }
        })
    }

    private fun observeViewModel() {
        // SUBSCRIBES TO VIEWMODEL STATE CHANGES.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.errorMessage != null) {
                        Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    if (state.sessionExpired) {
                        sessionManager.clearSession()
                        startActivity(Intent(requireContext(), AuthActivity::class.java))
                        viewModel.onSessionInvalidHandled()
                        requireActivity().finish()
                        return@collect
                    }
                    updateHeader(state.totalSpent, state.totalBudget)
                    updateMonthlyGoals(state.monthlyGoal, state.currentMonthSpent)
                    updateDateRangeDisplay(state.dateRangeStart, state.dateRangeEnd)
                    renderExpenses(state.expenses, state.categories, state.dateRangeStart, state.dateRangeEnd)
                }
            }
        }
    }

    private fun updateHeader(totalSpent: Double, totalBudget: Double) {
        // REFRESHES SUMMARY LABELS AND PROGRESS BAR.
        val maxValue = max(max(totalBudget, totalSpent), 1.0).roundToInt()
        binding.totalBudgetProgress.max = maxValue
        binding.totalBudgetProgress.progress = totalSpent.roundToInt().coerceIn(0, maxValue)
        binding.totalSpentLabel.text = getString(R.string.home_total_spent_label)
        binding.totalSpentValue.text = getString(
            R.string.home_total_spent_value,
            formatCurrency(totalSpent),
            formatCurrency(totalBudget)
        )
        val remaining = totalBudget - totalSpent
        binding.totalLeftLabel.text = getString(R.string.home_total_left_value, formatCurrency(remaining))
    }

    private fun setupMonthlyGoalsEditButton() {
        // SETS UP THE EDIT BUTTON FOR MONTHLY GOALS.
        binding.monthlyGoalsEditButton.setOnClickListener {
            showMonthlyGoalsDialog()
        }
    }

    private fun setupDateRangeSelector() {
        // SETS UP THE DATE RANGE SELECTOR UI.
        binding.dateRangeStart.setOnClickListener {
            showDatePicker(true)
        }
        binding.dateRangeEnd.setOnClickListener {
            showDatePicker(false)
        }
        binding.dateRangeClear.setOnClickListener {
            viewModel.setDateRange(null, null)
        }
    }

    private fun updateDateRangeDisplay(startDate: LocalDate?, endDate: LocalDate?) {
        // UPDATES THE DATE RANGE DISPLAY TEXT.
        if (startDate != null) {
            binding.dateRangeStart.text = startDate.format(dayFormatter)
        } else {
            binding.dateRangeStart.text = "Start Date"
        }
        
        if (endDate != null) {
            binding.dateRangeEnd.text = endDate.format(dayFormatter)
        } else {
            binding.dateRangeEnd.text = "End Date"
        }
        
        // SHOW CLEAR BUTTON IF EITHER DATE IS SET
        binding.dateRangeClear.visibility = if (startDate != null || endDate != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        // SHOWS A DATE PICKER FOR SELECTING START OR END DATE.
        val currentState = viewModel.uiState.value
        val currentDate = if (isStartDate) {
            currentState.dateRangeStart ?: currentState.dateRangeEnd ?: LocalDate.now()
        } else {
            currentState.dateRangeEnd ?: currentState.dateRangeStart ?: LocalDate.now()
        }
        
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                val currentStart = currentState.dateRangeStart
                val currentEnd = currentState.dateRangeEnd
                
                if (isStartDate) {
                    // VALIDATE: START DATE SHOULD BE <= END DATE
                    val newEnd = if (currentEnd != null && selectedDate.isAfter(currentEnd)) {
                        selectedDate
                    } else {
                        currentEnd
                    }
                    viewModel.setDateRange(selectedDate, newEnd)
                } else {
                    // VALIDATE: END DATE SHOULD BE >= START DATE
                    val newStart = if (currentStart != null && selectedDate.isBefore(currentStart)) {
                        selectedDate
                    } else {
                        currentStart
                    }
                    viewModel.setDateRange(newStart, selectedDate)
                }
            },
            currentDate.year,
            currentDate.monthValue - 1,
            currentDate.dayOfMonth
        ).show()
    }

    private fun updateMonthlyGoals(goal: MonthlySpendingGoal?, currentMonthSpent: Double) {
        // UPDATES THE MONTHLY GOALS DISPLAY WITH CURRENT VALUES.
        binding.monthlySpentLabel.text = "This Month: ${formatCurrency(currentMonthSpent)}"
        
        val minGoalText = goal?.minGoal?.let { formatCurrency(it) } ?: "Not set"
        val maxGoalText = goal?.maxGoal?.let { formatCurrency(it) } ?: "Not set"
        
        binding.monthlyMinGoal.text = minGoalText
        binding.monthlyMaxGoal.text = maxGoalText
        
        // UPDATE STATUS MESSAGE
        val statusText = buildStatusMessage(goal, currentMonthSpent)
        if (statusText.isNotEmpty()) {
            binding.monthlyGoalsStatus.text = statusText
            binding.monthlyGoalsStatus.visibility = View.VISIBLE
        } else {
            binding.monthlyGoalsStatus.visibility = View.GONE
        }
    }

    private fun buildStatusMessage(goal: com.example.oinkonomics.data.MonthlySpendingGoal?, currentMonthSpent: Double): String {
        // BUILDS A STATUS MESSAGE BASED ON GOALS AND CURRENT SPENDING.
        if (goal == null) return ""
        
        val messages = mutableListOf<String>()
        
        goal.minGoal?.let { min ->
            if (currentMonthSpent < min) {
                val diff = min - currentMonthSpent
                messages.add("Below min goal by ${formatCurrency(diff)}")
            }
        }
        
        goal.maxGoal?.let { max ->
            if (currentMonthSpent > max) {
                val diff = currentMonthSpent - max
                messages.add("Over max goal by ${formatCurrency(diff)}")
            } else if (currentMonthSpent >= max * 0.9) {
                val remaining = max - currentMonthSpent
                messages.add("Close to max goal (${formatCurrency(remaining)} remaining)")
            }
        }
        
        if (messages.isEmpty() && goal.minGoal != null && goal.maxGoal != null) {
            if (currentMonthSpent >= goal.minGoal && currentMonthSpent <= goal.maxGoal) {
                messages.add("Within goal range")
            }
        }
        
        return messages.joinToString(" • ")
    }

    private fun showMonthlyGoalsDialog() {
        // PRESENTS A DIALOG FOR SETTING MONTHLY SPENDING GOALS.
        val currentGoal = viewModel.uiState.value.monthlyGoal
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_monthly_goals, null)
        val minGoalInput = dialogView.findViewById<EditText>(R.id.input_min_goal)
        val maxGoalInput = dialogView.findViewById<EditText>(R.id.input_max_goal)
        
        minGoalInput.setText(currentGoal?.minGoal?.let { plainFormatter.format(it) } ?: "")
        maxGoalInput.setText(currentGoal?.maxGoal?.let { plainFormatter.format(it) } ?: "")
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Set Monthly Spending Goals")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear", null)
            .create()
        
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            
            positiveButton.setOnClickListener {
                val minGoal = minGoalInput.text.toString().toDoubleOrNull()
                val maxGoal = maxGoalInput.text.toString().toDoubleOrNull()
                
                // VALIDATE: IF BOTH ARE SET, MAX SHOULD BE >= MIN
                if (minGoal != null && maxGoal != null && maxGoal < minGoal) {
                    Toast.makeText(requireContext(), "Max goal must be greater than or equal to min goal", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                viewModel.setMonthlyGoal(minGoal, maxGoal)
                dialog.dismiss()
            }
            
            neutralButton.setOnClickListener {
                viewModel.setMonthlyGoal(null, null)
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }

    private fun renderExpenses(expenses: List<Expense>, categories: List<BudgetCategory>, startDate: LocalDate?, endDate: LocalDate?) {
        // BUILDS A GROUPED LIST OF MONTH HEADERS AND EXPENSES, FILTERED BY DATE RANGE.
        val categoryNames = categories.associate { it.id to it.name }
        
        // FILTER EXPENSES BY DATE RANGE
        val filteredExpenses = expenses.filter { expense ->
            val expenseDate = expense.localDate
            when {
                startDate != null && endDate != null -> {
                    !expenseDate.isBefore(startDate) && !expenseDate.isAfter(endDate)
                }
                startDate != null -> !expenseDate.isBefore(startDate)
                endDate != null -> !expenseDate.isAfter(endDate)
                else -> true
            }
        }
        
        val sortedExpenses = filteredExpenses.sortedWith(
            compareByDescending<Expense> { it.localDate }
                .thenByDescending { it.createdAtEpochMillis }
        )
        val currentMonth = YearMonth.now()
        val monthOrder = linkedSetOf(currentMonth)
        sortedExpenses.forEach { monthOrder.add(YearMonth.from(it.localDate)) }

        val items = mutableListOf<HomeListItem>()
        monthOrder.forEachIndexed { index, month ->
            val title = if (month == currentMonth) {
                getString(R.string.home_this_month_label)
            } else {
                month.format(monthFormatter)
            }
            items.add(HomeListItem.MonthHeader(title))
            if (index == 0) {
                items.add(HomeListItem.AddButton)
            }
            sortedExpenses.filter { YearMonth.from(it.localDate) == month }.forEach { expense ->
                items.add(
                    HomeListItem.ExpenseItem(
                        id = expense.id,
                        name = expense.name,
                        amountFormatted = formatCurrency(expense.amount),
                        dateLabel = expense.localDate.format(dayFormatter),
                        categoryName = categoryNames[expense.categoryId]
                            ?: getString(R.string.home_category_none_option)
                    )
                )
            }
        }

        if (items.isEmpty()) {
            items.add(HomeListItem.MonthHeader(getString(R.string.home_this_month_label)))
            items.add(HomeListItem.AddButton)
        }

        adapter.submitList(items)
    }

    private fun showExpenseDialog(existingExpense: Expense?) {
        // PRESENTS A FORM FOR ADDING OR EDITING AN EXPENSE.
        val state = viewModel.uiState.value

        val dialogView = layoutInflater.inflate(R.layout.dialog_expense_entry, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.input_expense_name)
        val dateInput = dialogView.findViewById<EditText>(R.id.input_expense_date)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.input_expense_category)
        val amountInput = dialogView.findViewById<EditText>(R.id.input_expense_amount)
        val receiptPreview = dialogView.findViewById<ImageView>(R.id.receipt_preview)
        val attachButton = dialogView.findViewById<View>(R.id.button_attach_receipt)
        val removeReceiptButton = dialogView.findViewById<View>(R.id.button_remove_receipt)

        val categoryOptions = mutableListOf<Pair<Long?, String>>().apply {
            add(null to getString(R.string.home_category_none_option))
            state.categories.forEach { add(it.id to it.name) }
        }
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categoryOptions.map { it.second }
        )
        categorySpinner.adapter = spinnerAdapter

        var selectedDate = existingExpense?.localDate ?: LocalDate.now()
        var selectedCategoryId = existingExpense?.categoryId
        var selectedReceiptUri: Uri? = existingExpense?.receiptUri?.takeIf { it.isNotBlank() }?.toUri()

        nameInput.setText(existingExpense?.name.orEmpty())
        dateInput.setText(selectedDate.format(dayFormatter))
        amountInput.setText(existingExpense?.amount?.let { plainFormatter.format(it) } ?: "")

        val preselectIndex = categoryOptions.indexOfFirst { it.first == selectedCategoryId }.takeIf { it >= 0 } ?: 0
        categorySpinner.setSelection(preselectIndex)
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCategoryId = categoryOptions[position].first
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // NO ACTION REQUIRED WHEN NOTHING SELECTED.
            }
        }

        fun updateReceiptPreview(uri: Uri?) {
            // TOGGLES RECEIPT THUMBNAIL VISIBILITY.
            if (uri != null) {
                receiptPreview.visibility = View.VISIBLE
                receiptPreview.setImageURI(uri)
                removeReceiptButton.visibility = View.VISIBLE
            } else {
                receiptPreview.visibility = View.GONE
                removeReceiptButton.visibility = View.GONE
            }
        }

        updateReceiptPreview(selectedReceiptUri)

        dateInput.setOnClickListener {
            // OPENS A DATE PICKER SO USERS CAN CHOOSE A DAY.
            val current = selectedDate
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                    dateInput.setText(selectedDate.format(dayFormatter))
                },
                current.year,
                current.monthValue - 1,
                current.dayOfMonth
            ).show()
        }

        attachButton.setOnClickListener {
            // TRIGGERS THE DOCUMENT PICKER FOR RECEIPTS.
            pendingReceiptCallback = { uri ->
                selectedReceiptUri = uri
                updateReceiptPreview(selectedReceiptUri)
            }
            receiptPicker.launch(arrayOf("image/*"))
        }

        removeReceiptButton.setOnClickListener {
            // REMOVES THE CURRENTLY ATTACHED RECEIPT.
            selectedReceiptUri = null
            updateReceiptPreview(null)
        }

        val dialogTitle = if (existingExpense == null) {
            getString(R.string.home_add_expense_title)
        } else {
            getString(R.string.home_edit_expense_title)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(R.string.home_dialog_confirm, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            // VALIDATES INPUT BEFORE DISMISSING THE DIALOG.
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val name = nameInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (name.isEmpty() || amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), R.string.home_invalid_expense_inputs, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (existingExpense == null) {
                    viewModel.addExpense(
                        name = name,
                        amount = amount,
                        date = selectedDate,
                        categoryId = selectedCategoryId,
                        receiptUri = selectedReceiptUri?.toString()
                    )
                } else {
                    viewModel.updateExpense(
                        expenseId = existingExpense.id,
                        name = name,
                        amount = amount,
                        date = selectedDate,
                        categoryId = selectedCategoryId,
                        receiptUri = selectedReceiptUri?.toString()
                    )
                }
                dialog.dismiss()
            }
        }

        if (existingExpense != null) {
            // ENABLES DELETION WHEN EDITING AN EXISTING EXPENSE.
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.home_dialog_delete)) { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.home_delete_expense_title)
                    .setMessage(R.string.home_delete_expense_message)
                    .setPositiveButton(R.string.home_dialog_delete) { _, _ ->
                        viewModel.removeExpense(existingExpense.id)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        dialog.show()
    }

    private fun formatCurrency(value: Double): String {
        // CONVERTS A DOUBLE TO A STRING WITH CURRENCY SYMBOLS.
        val absolute = abs(value)
        val formatted = currencyFormatter.format(absolute)
        return if (value < 0) "-R$formatted" else "R$formatted"
    }

    override fun onDestroyView() {
        // CLEANS UP THE VIEW BINDING REFERENCE.
        super.onDestroyView()
        _binding = null
    }
}





