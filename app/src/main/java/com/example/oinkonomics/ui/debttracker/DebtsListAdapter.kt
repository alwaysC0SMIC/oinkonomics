package com.example.oinkonomics.ui.debttracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.oinkonomics.R

sealed class DebtListItem {
	data object AddButton : DebtListItem()
	data class Entry(
		val id: Long,
		val name: String,
		val totalFormatted: String,
		val outstandingLabel: String,
		val dueLabel: String,
		val progress: Float
	) : DebtListItem()
}

class DebtsListAdapter(
	private val onAdd: () -> Unit,
	private val onClicked: (DebtListItem.Entry) -> Unit
) : ListAdapter<DebtListItem, RecyclerView.ViewHolder>(Diff) {
	companion object {
		private const val TYPE_ADD = 0
		private const val TYPE_ENTRY = 1

		private val Diff = object : DiffUtil.ItemCallback<DebtListItem>() {
			override fun areItemsTheSame(oldItem: DebtListItem, newItem: DebtListItem): Boolean {
				return when {
					oldItem is DebtListItem.AddButton && newItem is DebtListItem.AddButton -> true
					oldItem is DebtListItem.Entry && newItem is DebtListItem.Entry -> oldItem.id == newItem.id
					else -> false
				}
			}
			override fun areContentsTheSame(oldItem: DebtListItem, newItem: DebtListItem): Boolean = oldItem == newItem
		}
	}

	override fun getItemViewType(position: Int): Int = when (getItem(position)) {
		is DebtListItem.AddButton -> TYPE_ADD
		is DebtListItem.Entry -> TYPE_ENTRY
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return when (viewType) {
			TYPE_ADD -> AddViewHolder(inflater.inflate(R.layout.item_debt_add_button, parent, false), onAdd)
			else -> EntryViewHolder(inflater.inflate(R.layout.item_debt_entry, parent, false), onClicked)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = getItem(position)) {
			is DebtListItem.AddButton -> Unit
			is DebtListItem.Entry -> (holder as EntryViewHolder).bind(item)
		}
	}

	private class AddViewHolder(view: View, onClick: () -> Unit) : RecyclerView.ViewHolder(view) {
		init { view.setOnClickListener { onClick() } }
	}

    private class EntryViewHolder(
		view: View,
		private val onClick: (DebtListItem.Entry) -> Unit
	) : RecyclerView.ViewHolder(view) {
		private val name: TextView = view.findViewById(R.id.debt_name)
		private val total: TextView = view.findViewById(R.id.debt_total)
		private val outstanding: TextView = view.findViewById(R.id.debt_outstanding)
		private val due: TextView = view.findViewById(R.id.debt_due)
		private val progress: CircularProgressView = view.findViewById(R.id.debt_progress)
		private var current: DebtListItem.Entry? = null

		init { view.setOnClickListener { current?.let(onClick) } }

		fun bind(item: DebtListItem.Entry) {
			current = item
			name.text = item.name
			total.text = item.totalFormatted
			outstanding.text = item.outstandingLabel
			due.text = item.dueLabel
			progress.setProgress(item.progress)
		}
	}
}
