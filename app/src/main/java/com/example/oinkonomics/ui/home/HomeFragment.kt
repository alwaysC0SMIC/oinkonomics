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
                // Ignore if permission cannot be persisted (e.g. from gallery shortcut)
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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupRecyclerView()
        observeViewModel()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    private fun setupRecyclerView() {
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
                val position = parent.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION) {
                    outRect.bottom = spacing
                }
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.errorMessage != null) {
                        Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    updateHeader(state.totalSpent, state.totalBudget)
                    renderExpenses(state.expenses, state.categories)
                }
            }
        }
    }

    private fun updateHeader(totalSpent: Double, totalBudget: Double) {
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

    private fun renderExpenses(expenses: List<Expense>, categories: List<BudgetCategory>) {
        val categoryNames = categories.associate { it.id to it.name }
        val sortedExpenses = expenses.sortedWith(
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
                            ?: getString(R.string.home_unknown_category)
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
        val state = viewModel.uiState.value
        if (state.categories.isEmpty()) {
            Toast.makeText(requireContext(), R.string.home_no_categories_warning, Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_expense_entry, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.input_expense_name)
        val dateInput = dialogView.findViewById<EditText>(R.id.input_expense_date)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.input_expense_category)
        val amountInput = dialogView.findViewById<EditText>(R.id.input_expense_amount)
        val receiptPreview = dialogView.findViewById<ImageView>(R.id.receipt_preview)
        val attachButton = dialogView.findViewById<View>(R.id.button_attach_receipt)
        val removeReceiptButton = dialogView.findViewById<View>(R.id.button_remove_receipt)

        val categoryOptions = state.categories.map { it.id to it.name }
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categoryOptions.map { it.second }
        )
        categorySpinner.adapter = spinnerAdapter

        var selectedDate = existingExpense?.localDate ?: LocalDate.now()
        var selectedCategoryId = existingExpense?.categoryId ?: categoryOptions.first().first
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
                // no-op
            }
        }

        fun updateReceiptPreview(uri: Uri?) {
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
            pendingReceiptCallback = { uri ->
                selectedReceiptUri = uri
                updateReceiptPreview(selectedReceiptUri)
            }
            receiptPicker.launch(arrayOf("image/*"))
        }

        removeReceiptButton.setOnClickListener {
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
        val absolute = abs(value)
        val formatted = currencyFormatter.format(absolute)
        return if (value < 0) "-R$formatted" else "R$formatted"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}





