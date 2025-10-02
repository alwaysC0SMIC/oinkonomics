package com.example.oinkonomics.ui.dashboard

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
import com.example.oinkonomics.R

data class BudgetCategory(
    var name: String,
    var maxAmount: Double,
    var spentAmount: Double = 0.0,
    val colorResId: Int
)

class DashboardFragment : Fragment() {

    private val budgetCategories = ArrayList<BudgetCategory>()
    private lateinit var gridLayout: GridLayout
    private lateinit var totalSpentTextView: TextView
    private lateinit var totalLeftTextView: TextView
    private lateinit var totalProgressBar: android.widget.ProgressBar
    private lateinit var addCategoryButton: SquareLinearLayout

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
    private var nextColorIndex = 0

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

        addCategoryButton = layoutInflater.inflate(
            R.layout.item_add_category,
            gridLayout,
            false
        ) as SquareLinearLayout
        configureAddButton()
        gridLayout.addView(addCategoryButton)

        updateTotalProgress()

        return root
    }

    private fun configureAddButton() {
        val neutralColor = ContextCompat.getColor(requireContext(), R.color.text_grey)
        val ring = addCategoryButton.findViewById<RadialProgressView>(R.id.add_progress_ring)
        ring.setColors(neutralColor)
        ring.setProgress(0f, 1f)
        addCategoryButton.setOnClickListener {
            showAddCategoryDialog()
        }
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
                    addCategory(name, maxAmount)
                } else {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCategory(name: String, maxAmount: Double) {
        val colorResId = categoryColors[nextColorIndex % categoryColors.size]
        nextColorIndex++

        val category = BudgetCategory(name, maxAmount, 0.0, colorResId)
        budgetCategories.add(category)

        val categoryView = layoutInflater.inflate(R.layout.item_budget_category, gridLayout, false)
        bindCategoryView(categoryView, category)

        val insertIndex = gridLayout.indexOfChild(addCategoryButton)
        gridLayout.addView(categoryView, insertIndex)
        updateTotalProgress()
        ensureAddButtonLast()
    }

    private fun ensureAddButtonLast() {
        gridLayout.removeView(addCategoryButton)
        gridLayout.addView(addCategoryButton)
    }

    private fun showEditCategoryDialog(category: BudgetCategory, viewIndex: Int) {
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
                val name = nameEditText.text.toString().trim()
                val maxAmount = maxAmountEditText.text.toString().toDoubleOrNull()
                val spentAmount = spentAmountEditText.text.toString().toDoubleOrNull()

                if (name.isEmpty() || maxAmount == null || spentAmount == null) {
                    Toast.makeText(requireContext(), R.string.invalid_category_inputs, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                category.name = name
                category.maxAmount = maxAmount
                category.spentAmount = spentAmount

                val categoryView = gridLayout.getChildAt(viewIndex)
                bindCategoryView(categoryView, category)
                updateTotalProgress()
                dialog.dismiss()
            }
        }

        removeButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.remove_category)
                .setMessage(R.string.remove_category_confirmation)
                .setPositiveButton(R.string.remove) { _, _ ->
                    removeCategory(viewIndex)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        dialog.show()
    }

    private fun removeCategory(index: Int) {
        if (index < 0 || index >= budgetCategories.size) return
        budgetCategories.removeAt(index)
        gridLayout.removeViewAt(index)
        updateTotalProgress()
        updateAllCategoryViews()
        ensureAddButtonLast()
    }

    private fun bindCategoryView(view: View, category: BudgetCategory) {
        val nameTextView = view.findViewById<TextView>(R.id.category_name)
        val amountTextView = view.findViewById<TextView>(R.id.category_amount)
        val radialProgressView = view.findViewById<RadialProgressView>(R.id.category_progress_ring)

        nameTextView.text = category.name
        amountTextView.text = "R${category.spentAmount} / R${category.maxAmount}"

        val max = if (category.maxAmount <= 0) 1.0 else category.maxAmount
        radialProgressView.setProgress(category.spentAmount.toFloat(), max.toFloat())

        val progressColor = ContextCompat.getColor(requireContext(), category.colorResId)
        radialProgressView.setColors(progressColor)

        view.setOnClickListener {
            val index = gridLayout.indexOfChild(view)
            if (index >= 0 && index < budgetCategories.size) {
                showEditCategoryDialog(budgetCategories[index], index)
            }
        }
    }

    private fun updateAllCategoryViews() {
        for (i in 0 until budgetCategories.size) {
            val category = budgetCategories[i]
            val categoryView = gridLayout.getChildAt(i)
            bindCategoryView(categoryView, category)
        }
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
