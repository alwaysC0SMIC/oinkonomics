package com.example.oinkonomics.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.oinkonomics.R
import com.example.oinkonomics.auth.AuthActivity
import com.example.oinkonomics.data.BudgetCategory
import com.example.oinkonomics.data.MissingUserException
import com.example.oinkonomics.data.OinkonomicsRepository
import com.example.oinkonomics.data.SessionManager
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private val budgetCategories = mutableListOf<BudgetCategory>()
    private lateinit var gridLayout: GridLayout
    private lateinit var totalSpentTextView: TextView
    private lateinit var totalLeftTextView: TextView
    private lateinit var totalProgressBar: android.widget.ProgressBar
    private lateinit var addCategoryButton: SquareLinearLayout

    private lateinit var repository: OinkonomicsRepository
    private lateinit var sessionManager: SessionManager
    private var userId: Long? = null

    private val categoryColors by lazy {
        listOf(
            R.color.category_green,
            R.color.category_pink,
            R.color.category_orange,
            R.color.category_blue,
            R.color.category_aqua,
            R.color.category_lilac
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(requireContext())
        userId = sessionManager.getLoggedInUserId()
        if (userId == null) {
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)

        repository = OinkonomicsRepository(requireContext())

        gridLayout = root.findViewById(R.id.categories_grid)
        totalSpentTextView = root.findViewById(R.id.total_spent_text)
        totalLeftTextView = root.findViewById(R.id.total_left_text)
        totalProgressBar = root.findViewById(R.id.total_progress_bar)

        addCategoryButton = layoutInflater.inflate(
            R.layout.item_add_category,
            gridLayout,
            false
        ) as SquareLinearLayout
        configureAddButton()

        loadCategories()

        return root
    }

    override fun onResume() {
        super.onResume()
        if (this::gridLayout.isInitialized) {
            loadCategories()
        }
    }

    private fun configureAddButton() {
        val neutralColor = ContextCompat.getColor(requireContext(), R.color.text_grey)
        val ring = addCategoryButton.findViewById<RadialProgressView>(R.id.add_progress_ring)
        ring.setColors(neutralColor)
        ring.setProgress(0f, 1f)
        addCategoryButton.setOnClickListener { showAddCategoryDialog() }
    }

    private fun showAddCategoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)

        AlertDialog.Builder(requireContext())
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    addButton.setOnClickListener {
                        val nameField = dialogView.findViewById<EditText>(R.id.edit_text_category_name)
                        val maxAmountField = dialogView.findViewById<EditText>(R.id.edit_text_max_amount)

                        val name = nameField.text.toString().trim()
                        val maxAmount = maxAmountField.text.toString().toDoubleOrNull()

                        if (name.isEmpty() || maxAmount == null) {
                            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        addCategory(name, maxAmount)
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun addCategory(name: String, maxAmount: Double) {
        val currentUserId = userId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val newCategory = repository.createBudgetCategory(currentUserId, name, maxAmount)
                budgetCategories.add(newCategory)
                renderCategories()
                updateTotalProgress()
            } catch (ex: MissingUserException) {
                handleExpiredSession()
            } catch (ex: Exception) {
                Toast.makeText(
                    requireContext(),
                    ex.message ?: getString(R.string.error_saving_category),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun renderCategories() {
        if (addCategoryButton.parent != null) {
            (addCategoryButton.parent as ViewGroup).removeView(addCategoryButton)
        }
        gridLayout.removeAllViews()
        budgetCategories.forEach { category ->
            val categoryView = layoutInflater.inflate(R.layout.item_budget_category, gridLayout, false)
            bindCategoryView(categoryView, category)
            gridLayout.addView(categoryView)
        }
        gridLayout.addView(addCategoryButton)
    }

    private fun showEditCategoryDialog(category: BudgetCategory, cardView: View) {
        val currentUserId = userId ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_category, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.edit_text_category_name)
        val maxAmountEditText = dialogView.findViewById<EditText>(R.id.edit_text_max_amount)
        val spentAmountEditText = dialogView.findViewById<EditText>(R.id.edit_text_spent_amount)
        val removeButton = dialogView.findViewById<Button>(R.id.button_remove_category)

        nameEditText.setText(category.name)
        maxAmountEditText.setText(category.maxAmount.toString())
        spentAmountEditText.setText(category.spentAmount.toString())

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_category_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val index = budgetCategories.indexOfFirst { it.id == category.id }
                if (index == -1) {
                    dialog.dismiss()
                    return@setOnClickListener
                }

                val name = nameEditText.text.toString().trim()
                val maxAmount = maxAmountEditText.text.toString().toDoubleOrNull()
                val spentAmount = spentAmountEditText.text.toString().toDoubleOrNull()

                if (name.isEmpty() || maxAmount == null || spentAmount == null) {
                    Toast.makeText(requireContext(), R.string.invalid_category_inputs, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val updatedCategory = budgetCategories[index].copy(
                    name = name,
                    maxAmount = maxAmount,
                    spentAmount = spentAmount
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    repository.updateBudgetCategory(updatedCategory)
                    budgetCategories[index] = updatedCategory
                    bindCategoryView(cardView, updatedCategory)
                    updateTotalProgress()
                    dialog.dismiss()
                }
            }
        }

        removeButton.setOnClickListener {
            val index = budgetCategories.indexOfFirst { it.id == category.id }
            if (index == -1) {
                dialog.dismiss()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.remove_category)
                .setMessage(R.string.remove_category_confirmation)
                .setPositiveButton(R.string.remove) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.deleteBudgetCategory(category.id, currentUserId)
                        budgetCategories.removeAt(index)
                        renderCategories()
                        updateTotalProgress()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        dialog.show()
    }

    private fun bindCategoryView(view: View, category: BudgetCategory) {
        val nameTextView = view.findViewById<TextView>(R.id.category_name)
        val amountTextView = view.findViewById<TextView>(R.id.category_amount)
        val radialProgressView = view.findViewById<RadialProgressView>(R.id.category_progress_ring)

        nameTextView.text = category.name
        amountTextView.text = "R${category.spentAmount} / R${category.maxAmount}"

        val max = if (category.maxAmount <= 0) 1.0 else category.maxAmount
        radialProgressView.setProgress(category.spentAmount.toFloat(), max.toFloat())

        val index = budgetCategories.indexOfFirst { it.id == category.id }
        val colorResId = if (index >= 0) {
            categoryColors[index % categoryColors.size]
        } else {
            categoryColors.first()
        }
        radialProgressView.setColors(ContextCompat.getColor(requireContext(), colorResId))

        view.setOnClickListener {
            showEditCategoryDialog(category, view)
        }
    }

    private fun loadCategories() {
        val currentUserId = userId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val categories = repository.getBudgetCategories(currentUserId)
                budgetCategories.clear()
                budgetCategories.addAll(categories)
                renderCategories()
                updateTotalProgress()
            } catch (ex: MissingUserException) {
                handleExpiredSession()
            }
        }
    }

    private fun handleExpiredSession() {
        if (!isAdded) {
            return
        }
        sessionManager.clearSession()
        userId = null
        Toast.makeText(requireContext(), getString(R.string.error_session_expired), Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireContext(), AuthActivity::class.java))
        requireActivity().finish()
    }

    private fun updateTotalProgress() {
        var totalSpent = 0.0
        var totalMax = 0.0
        for (category in budgetCategories) {
            totalSpent += category.spentAmount
            totalMax += category.maxAmount
        }

        val totalLeft = totalMax - totalSpent

        totalSpentTextView.text = "Total Spent"
        totalLeftTextView.text = "R${totalLeft} left"
        val maxValue = totalMax.toInt().coerceAtLeast(1)
        totalProgressBar.max = maxValue
        totalProgressBar.progress = totalSpent.toInt().coerceIn(0, maxValue)
    }
}
