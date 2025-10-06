package com.example.oinkonomics.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.oinkonomics.R

// DEFINES THE DIFFERENT ROW TYPES IN THE HOME LIST.
sealed class HomeListItem {
    data class MonthHeader(val title: String) : HomeListItem()
    data object AddButton : HomeListItem()
    data class ExpenseItem(
        val id: Long,
        val name: String,
        val amountFormatted: String,
        val dateLabel: String,
        val categoryName: String
    ) : HomeListItem()
}

// RENDERS HOME TAB LIST ITEMS AND HANDLES INTERACTIONS.
class HomeListAdapter(
    private val onAddExpense: () -> Unit,
    private val onExpenseClicked: (HomeListItem.ExpenseItem) -> Unit
) : ListAdapter<HomeListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ADD = 1
        private const val VIEW_TYPE_EXPENSE = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<HomeListItem>() {
            override fun areItemsTheSame(oldItem: HomeListItem, newItem: HomeListItem): Boolean {
                // CHECKS IDENTITY BASED ON ROW TYPE AND IDENTIFIER.
                return when {
                    oldItem is HomeListItem.MonthHeader && newItem is HomeListItem.MonthHeader ->
                        oldItem.title == newItem.title
                    oldItem is HomeListItem.AddButton && newItem is HomeListItem.AddButton -> true
                    oldItem is HomeListItem.ExpenseItem && newItem is HomeListItem.ExpenseItem ->
                        oldItem.id == newItem.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: HomeListItem, newItem: HomeListItem): Boolean {
                // RELIES ON DATA CLASS EQUALITY FOR CONTENT CHECKS.
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        // MAPS ITEMS TO THEIR CORRESPONDING VIEW TYPES.
        return when (getItem(position)) {
            is HomeListItem.MonthHeader -> VIEW_TYPE_HEADER
            is HomeListItem.AddButton -> VIEW_TYPE_ADD
            is HomeListItem.ExpenseItem -> VIEW_TYPE_EXPENSE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // INFLATES THE PROPER VIEW HOLDER FOR THE REQUESTED TYPE.
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> MonthHeaderViewHolder(
                inflater.inflate(R.layout.item_expense_month_header, parent, false)
            )
            VIEW_TYPE_ADD -> AddButtonViewHolder(
                inflater.inflate(R.layout.item_expense_add_button, parent, false),
                onAddExpense
            )
            else -> ExpenseViewHolder(
                inflater.inflate(R.layout.item_expense_entry, parent, false),
                onExpenseClicked
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // DISPATCHES BINDING TO THE CORRECT VIEW HOLDER.
        when (val item = getItem(position)) {
            is HomeListItem.MonthHeader -> (holder as MonthHeaderViewHolder).bind(item)
            is HomeListItem.AddButton -> Unit
            is HomeListItem.ExpenseItem -> (holder as ExpenseViewHolder).bind(item)
        }
    }

    private class MonthHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.month_label)

        fun bind(item: HomeListItem.MonthHeader) {
            // DISPLAYS THE MONTH HEADER LABEL.
            titleText.text = item.title
        }
    }

    private class AddButtonViewHolder(view: View, onClick: () -> Unit) : RecyclerView.ViewHolder(view) {
        init {
            // INVOKES THE ADD CALLBACK WHEN THE TILE IS PRESSED.
            view.setOnClickListener { onClick() }
        }
    }

    private class ExpenseViewHolder(
        view: View,
        private val onClick: (HomeListItem.ExpenseItem) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.expense_name)
        private val amountText: TextView = view.findViewById(R.id.expense_amount)
        private val dateText: TextView = view.findViewById(R.id.expense_date)
        private val categoryText: TextView = view.findViewById(R.id.expense_category)

        private var currentItem: HomeListItem.ExpenseItem? = null

        init {
            // DELEGATES CLICK EVENTS TO THE SUPPLIED LAMBDA.
            view.setOnClickListener {
                currentItem?.let(onClick)
            }
        }

        fun bind(item: HomeListItem.ExpenseItem) {
            // POPULATES THE LIST ROW WITH EXPENSE DETAILS.
            currentItem = item
            nameText.text = item.name
            amountText.text = item.amountFormatted
            dateText.text = item.dateLabel
            categoryText.text = item.categoryName
        }
    }
}
