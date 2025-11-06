package com.example.oinkonomics.ui.notifications

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
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
import com.example.oinkonomics.data.OinkonomicsRepository
import com.example.oinkonomics.data.SessionManager
import com.example.oinkonomics.data.Subscription
import com.example.oinkonomics.databinding.FragmentNotificationsBinding
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

// SUBSCRIPTIONS TAB IMPLEMENTATION.
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val sessionManager by lazy { SessionManager(requireContext()) }

    private val dayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    private val currencyFormatter = DecimalFormat("#,##0.00")
    private val plainFormatter = DecimalFormat("0.##")

    private var pendingIconCallback: ((Uri?) -> Unit)? = null
    private val iconPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
            }
        }
        pendingIconCallback?.invoke(uri)
        pendingIconCallback = null
    }

    private val viewModel: NotificationsViewModel by viewModels {
        val userId = sessionManager.getLoggedInUserId() ?: -1
        NotificationsViewModel.Factory(OinkonomicsRepository(requireContext()), userId)
    }

    private lateinit var adapter: SubscriptionsListAdapter

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
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        setupRecycler()
        observeViewModel()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun setupRecycler() {
        adapter = SubscriptionsListAdapter(
            onAdd = { showSubscriptionDialog(null) },
            onClicked = { entry ->
                val sub = viewModel.uiState.value.subscriptions.firstOrNull { it.id == entry.id }
                if (sub != null) showSubscriptionDialog(sub)
            }
        )
        val rv = binding.root.findViewById<RecyclerView>(R.id.subscriptionsRecyclerView)
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
                    val totalView = binding.textNotifications
                    totalView.text = "R${currencyFormatter.format(state.monthlyTotal)}"
                    val items = mutableListOf<SubscriptionListItem>()
                    items.add(SubscriptionListItem.AddButton)
                    val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
                    state.subscriptions.sortedBy { it.localDate.dayOfMonth }.forEach { sub ->
                        items.add(
                            SubscriptionListItem.Entry(
                                id = sub.id,
                                name = sub.name,
                                amountFormatted = "R${currencyFormatter.format(sub.amount)}",
                                renewLabel = "Renews ${sub.localDate.format(dayFmt)}",
                                iconUri = sub.iconUri
                            )
                        )
                    }
                    adapter.submitList(items)
                }
            }
        }
    }

    private fun showSubscriptionDialog(existing: Subscription?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_subscription_entry, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.input_subscription_name)
        val amountInput = dialogView.findViewById<EditText>(R.id.input_subscription_amount)
        val dateInput = dialogView.findViewById<EditText>(R.id.input_subscription_date)
        val iconPreview = dialogView.findViewById<ImageView>(R.id.icon_preview)
        val chooseIconButton = dialogView.findViewById<View>(R.id.button_choose_icon)
        val removeIconButton = dialogView.findViewById<View>(R.id.button_remove_icon)

        var selectedDate = existing?.localDate ?: LocalDate.now()
        var selectedIcon: Uri? = existing?.iconUri?.takeIf { it.isNotBlank() }?.toUri()

        nameInput.setText(existing?.name.orEmpty())
        amountInput.setText(existing?.amount?.let { plainFormatter.format(it) } ?: "")
        dateInput.setText(selectedDate.format(dayFormatter))

        fun updateIcon(uri: Uri?) {
            if (uri != null) {
                iconPreview.visibility = View.VISIBLE
                iconPreview.setImageURI(uri)
                removeIconButton.visibility = View.VISIBLE
            } else {
                iconPreview.visibility = View.GONE
                removeIconButton.visibility = View.GONE
            }
        }
        updateIcon(selectedIcon)

        dateInput.setOnClickListener {
            val cur = selectedDate
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate = LocalDate.of(y, m + 1, d)
                dateInput.setText(selectedDate.format(dayFormatter))
            }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
        }

        chooseIconButton.setOnClickListener {
            pendingIconCallback = { uri ->
                selectedIcon = uri
                updateIcon(selectedIcon)
            }
            iconPicker.launch(arrayOf("image/*"))
        }
        removeIconButton.setOnClickListener {
            selectedIcon = null
            updateIcon(null)
        }

        val dialogTitle = if (existing == null) getString(R.string.subs_add_title) else getString(R.string.subs_edit_title)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(R.string.home_dialog_confirm, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.setOnClickListener {
                val name = nameInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (name.isEmpty() || amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), R.string.home_invalid_expense_inputs, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (existing == null) {
                    viewModel.addSubscription(name, amount, selectedDate, selectedIcon?.toString())
                } else {
                    viewModel.updateSubscription(
                        existing.copy(
                            name = name,
                            amount = amount,
                            dateIso = selectedDate.toString(),
                            iconUri = selectedIcon?.toString()
                        )
                    )
                }
                dialog.dismiss()
            }
        }

        if (existing != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.home_dialog_delete)) { _, _ ->
                viewModel.removeSubscription(existing.id)
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}