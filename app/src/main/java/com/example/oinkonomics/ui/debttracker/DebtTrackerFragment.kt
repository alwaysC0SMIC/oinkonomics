package com.example.oinkonomics.ui.debttracker

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.oinkonomics.R
import com.example.oinkonomics.auth.AuthActivity
import com.example.oinkonomics.data.Debt
import com.example.oinkonomics.data.OinkonomicsRepository
import com.example.oinkonomics.data.SessionManager
import com.example.oinkonomics.databinding.FragmentDebttrackerBinding
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

// DEBT TRACKER IMPLEMENTATION WITH DYNAMIC LIST
class DebtTrackerFragment : Fragment() {

    private var _binding: FragmentDebttrackerBinding? = null
    private val binding get() = _binding!!

    private val sessionManager by lazy { SessionManager(requireContext()) }
    private val currencyFormatter = DecimalFormat("#,##0.00")
    private val plainFormatter = DecimalFormat("0.##")
    private val dayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    private lateinit var adapter: DebtsListAdapter

    private val viewModel: DebtTrackerViewModel by viewModels {
        val userId = sessionManager.getLoggedInUserId() ?: -1
        DebtTrackerViewModel.Factory(OinkonomicsRepository(requireContext()), userId)
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
        _binding = FragmentDebttrackerBinding.inflate(inflater, container, false)
        setupRecycler()
        observeViewModel()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun setupRecycler() {
        adapter = DebtsListAdapter(
            onAdd = { showDebtDialog(null) },
            onClicked = { entry ->
                val debt = viewModel.uiState.value.debts.firstOrNull { it.id == entry.id }
                if (debt != null) showDebtDialog(debt)
            }
        )
        val rv = binding.root.findViewById<RecyclerView>(R.id.debtsRecyclerView)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.sessionExpired) {
                        sessionManager.clearSession()
                        startActivity(Intent(requireContext(), AuthActivity::class.java))
                        requireActivity().finish()
                        return@collect
                    }
                    if (state.errorMessage != null) {
                        Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    binding.textDebttracker.text = "R${currencyFormatter.format(state.totalDebt)}"
                    val progress = binding.progressBar
                    val max = state.totalDebt.coerceAtLeast(1.0)
                    progress.max = 1000
                    progress.progress = ((state.totalPaid / max) * 1000).toInt()
                    binding.paidLabel.text = "R${currencyFormatter.format(state.totalPaid)} paid"
                    binding.leftLabel.text = "R${currencyFormatter.format(state.totalLeft)} left"

                    val items = mutableListOf<DebtListItem>()
                    items.add(DebtListItem.AddButton)
                    state.debts.forEach { d ->
                        items.add(
                            DebtListItem.Entry(
                                id = d.id,
                                name = d.name,
                                totalFormatted = "R${currencyFormatter.format(d.totalAmount)}",
                                outstandingLabel = "R${currencyFormatter.format(d.outstandingAmount)} outstanding",
                                dueLabel = "Due ${d.dueDate.format(dayFormatter)}",
                                progress = (d.paidRatio * 100).toFloat()
                            )
                        )
                    }
                    adapter.submitList(items)
                }
            }
        }
    }

    private fun showDebtDialog(existing: Debt?) {
        val view = layoutInflater.inflate(R.layout.dialog_debt_entry, null)
        val nameInput = view.findViewById<EditText>(R.id.input_debt_name)
        val totalInput = view.findViewById<EditText>(R.id.input_debt_total)
        val paidInput = view.findViewById<EditText>(R.id.input_debt_paid)
        val dueInput = view.findViewById<EditText>(R.id.input_debt_due)

        var selectedDue = existing?.dueDate ?: LocalDate.now()
        nameInput.setText(existing?.name.orEmpty())
        totalInput.setText(existing?.totalAmount?.let { plainFormatter.format(it) } ?: "")
        paidInput.setText(existing?.paidAmount?.let { plainFormatter.format(it) } ?: "")
        dueInput.setText(selectedDue.format(dayFormatter))

        dueInput.setOnClickListener {
            val cur = selectedDue
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDue = LocalDate.of(y, m + 1, d)
                dueInput.setText(selectedDue.format(dayFormatter))
            }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
        }

        val dialogTitle = if (existing == null) getString(R.string.debt_add_title) else getString(R.string.debt_edit_title)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(view)
            .setPositiveButton(R.string.home_dialog_confirm, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val name = nameInput.text.toString().trim()
                val total = totalInput.text.toString().toDoubleOrNull()
                val paid = paidInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isEmpty() || total == null || total <= 0 || paid < 0 || paid > total) {
                    Toast.makeText(requireContext(), R.string.debt_invalid_inputs, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (existing == null) {
                    viewModel.addDebt(name, total, paid, selectedDue)
                } else {
                    viewModel.updateDebt(existing.copy(name = name, totalAmount = total, paidAmount = paid, dueDateIso = selectedDue.toString()))
                }
                dialog.dismiss()
            }
        }

        if (existing != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.home_dialog_delete)) { _, _ ->
                viewModel.removeDebt(existing.id)
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

