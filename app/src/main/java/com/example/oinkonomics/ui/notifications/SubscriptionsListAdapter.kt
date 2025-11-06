package com.example.oinkonomics.ui.notifications

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.oinkonomics.R

// Row models for subscriptions list
sealed class SubscriptionListItem {
	data object AddButton : SubscriptionListItem()
	data class Entry(
		val id: Long,
		val name: String,
		val amountFormatted: String,
		val renewLabel: String,
		val iconUri: String?
	) : SubscriptionListItem()
}

class SubscriptionsListAdapter(
	private val onAdd: () -> Unit,
	private val onClicked: (SubscriptionListItem.Entry) -> Unit
) : ListAdapter<SubscriptionListItem, RecyclerView.ViewHolder>(Diff) {

	companion object {
		private const val TYPE_ADD = 0
		private const val TYPE_ENTRY = 1

		private val Diff = object : DiffUtil.ItemCallback<SubscriptionListItem>() {
			override fun areItemsTheSame(oldItem: SubscriptionListItem, newItem: SubscriptionListItem): Boolean {
				return when {
					oldItem is SubscriptionListItem.AddButton && newItem is SubscriptionListItem.AddButton -> true
					oldItem is SubscriptionListItem.Entry && newItem is SubscriptionListItem.Entry -> oldItem.id == newItem.id
					else -> false
				}
			}

			override fun areContentsTheSame(oldItem: SubscriptionListItem, newItem: SubscriptionListItem): Boolean = oldItem == newItem
		}
	}

	override fun getItemViewType(position: Int): Int = when (getItem(position)) {
		is SubscriptionListItem.AddButton -> TYPE_ADD
		is SubscriptionListItem.Entry -> TYPE_ENTRY
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return when (viewType) {
			TYPE_ADD -> AddViewHolder(inflater.inflate(R.layout.item_subscription_add_button, parent, false), onAdd)
			else -> EntryViewHolder(inflater.inflate(R.layout.item_subscription_entry, parent, false), onClicked)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = getItem(position)) {
			is SubscriptionListItem.AddButton -> Unit
			is SubscriptionListItem.Entry -> (holder as EntryViewHolder).bind(item)
		}
	}

	private class AddViewHolder(view: View, onClick: () -> Unit) : RecyclerView.ViewHolder(view) {
		init { view.setOnClickListener { onClick() } }
	}

	private class EntryViewHolder(
		view: View,
		private val onClick: (SubscriptionListItem.Entry) -> Unit
	) : RecyclerView.ViewHolder(view) {
		private val icon: ImageView = view.findViewById(R.id.sub_icon)
		private val name: TextView = view.findViewById(R.id.sub_name)
		private val amount: TextView = view.findViewById(R.id.sub_amount)
		private val renews: TextView = view.findViewById(R.id.sub_renews)
		private var current: SubscriptionListItem.Entry? = null

		init { view.setOnClickListener { current?.let(onClick) } }

		fun bind(item: SubscriptionListItem.Entry) {
			current = item
			name.text = item.name
			amount.text = item.amountFormatted
			renews.text = item.renewLabel
			if (!item.iconUri.isNullOrBlank()) {
				icon.setImageURI(item.iconUri.toUri())
			} else {
				icon.setImageResource(R.drawable.circle_gray)
			}
		}
	}
}
