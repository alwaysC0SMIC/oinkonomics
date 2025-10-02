package com.example.oinkonomics.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.oinkonomics.R

data class BudgetCategory(val name: String, val maxAmount: Double, var spentAmount: Double = 0.0)

class DashboardFragment : Fragment() {

    private val budgetCategories = ArrayList<BudgetCategory>()
    private lateinit var gridLayout: GridLayout
    private lateinit var totalSpentTextView: TextView
    private lateinit var totalLeftTextView: TextView
    private lateinit var totalProgressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)

        gridLayout = root.findViewById(R.id.categories_grid)
        totalSpentTextView = root.findViewById(R.id.total_spent_text)
        totalLeftTextView = root.findViewById(R.id.total_left_text)
        totalProgressBar = root.findViewById(R.id.total_progress_bar)
        val addCategoryButton = root.findViewById<LinearLayout>(R.id.add_category_button)

        addCategoryButton.setOnClickListener {
            showAddCategoryDialog()
        }

        updateTotalProgress()

        return root
    }

    private fun showAddCategoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)

        AlertDialog.Builder(requireContext())
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val nameEditText = dialogView.findViewById<EditText>(R.id.edit_text_category_name)
                val maxAmountEditText = dialogView.findViewById<EditText>(R.id.edit_text_max_amount)

                val name = nameEditText.text.toString().trim()
                val maxAmount = maxAmountEditText.text.toString().toDoubleOrNull()

                if (name.isNotEmpty() && maxAmount != null) {
                    addCategory(BudgetCategory(name, maxAmount))
                } else {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCategory(category: BudgetCategory) {
        budgetCategories.add(category)
        val categoryView = layoutInflater.inflate(R.layout.item_budget_category, gridLayout, false)

        updateCategoryView(categoryView, category)

        categoryView.setOnClickListener {
            showEditSpentDialog(category)
        }

        gridLayout.addView(categoryView, gridLayout.childCount - 1)
        updateTotalProgress()
    }

    private fun showEditSpentDialog(category: BudgetCategory) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_spent, null)
        val spentAmountEditText = dialogView.findViewById<EditText>(R.id.edit_text_spent_amount)
        spentAmountEditText.setText(category.spentAmount.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Spent Amount")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val spentAmount = spentAmountEditText.text.toString().toDoubleOrNull()
                if (spentAmount != null) {
                    category.spentAmount = spentAmount
                    updateAllCategoryViews()
                    updateTotalProgress()
                } else {
                    Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAllCategoryViews() {
        for (i in 0 until budgetCategories.size) {
            val category = budgetCategories[i]
            val view = gridLayout.getChildAt(i)
            updateCategoryView(view, category)
        }
    }

    private fun updateCategoryView(view: View, category: BudgetCategory) {
        val nameTextView = view.findViewById<TextView>(R.id.category_name)
        val amountTextView = view.findViewById<TextView>(R.id.category_amount)
        val progressBar = view.findViewById<ProgressBar>(R.id.category_progress)

        nameTextView.text = category.name
        amountTextView.text = "R${category.spentAmount} / R${category.maxAmount}"
        progressBar.max = category.maxAmount.toInt()
        progressBar.progress = category.spentAmount.toInt()
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
        totalProgressBar.max = totalMax.toInt()
        totalProgressBar.progress = totalSpent.toInt()
    }
}