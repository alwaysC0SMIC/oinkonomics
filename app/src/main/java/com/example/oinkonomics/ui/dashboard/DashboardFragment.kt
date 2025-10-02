package com.example.oinkonomics.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.oinkonomics.R

class DashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)

        val gridLayout = root.findViewById<GridLayout>(R.id.categories_grid)
        val addCategoryButton = root.findViewById<LinearLayout>(R.id.add_category_button)

        addCategoryButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Add New Category")
                .setMessage("Do you want to add a new budget category?")
                .setPositiveButton("Confirm") { _, _ ->
                    val newCategory = inflater.inflate(R.layout.item_budget_category, gridLayout, false)
                    gridLayout.addView(newCategory, gridLayout.childCount - 1)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return root
    }
}