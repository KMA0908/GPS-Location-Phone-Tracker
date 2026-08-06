package com.nhn.gps.location.phone.tracker.ui.zone

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.databinding.ItemAlertHeaderBinding
import com.nhn.gps.location.phone.tracker.databinding.ItemZoneAlertLocalBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZoneAlertAdapter(private val onClick: (ZoneAlert) -> Unit) :
    ListAdapter<Any, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position) is String) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderHolder(ItemAlertHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemHolder(ItemZoneAlertLocalBinding.inflate(inflater, parent, false), onClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderHolder && item is String) {
            holder.bind(item)
        } else if (holder is ItemHolder && item is ZoneAlert) {
            holder.bind(item)
        }
    }

    class HeaderHolder(private val binding: ItemAlertHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvHeader.text = title
        }
    }

    class ItemHolder(
        private val binding: ItemZoneAlertLocalBinding,
        private val onClick: (ZoneAlert) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ZoneAlert) = with(binding) {
            tvAlertTitle.text = if (item.isEnter) "${item.userName} entered ${item.zoneName}" else "${item.userName} left ${item.zoneName}"
            tvAlertSubtitle.text = "${item.status.label} zone · ${FORMAT.format(Date(item.time))}"
            tvAlertType.text = if (item.isEnter) "IN" else "OUT"
            
            val color = if (item.isEnter) COLOR_ENTER else COLOR_LEAVE
            viewIconBg.backgroundTintList = ColorStateList.valueOf(color).withAlpha(40)
            tvAlertType.setTextColor(color)

            root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1
        const val COLOR_ENTER = 0xFF35C759.toInt()
        const val COLOR_LEAVE = 0xFF4A89BD.toInt()
        val FORMAT = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())
        val DIFF = object : DiffUtil.ItemCallback<Any>() {
            override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
                if (oldItem is String && newItem is String) return oldItem == newItem
                if (oldItem is ZoneAlert && newItem is ZoneAlert) return oldItem.id == newItem.id
                return false
            }
            override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
                return if (oldItem is String && newItem is String) oldItem == newItem
                else if (oldItem is ZoneAlert && newItem is ZoneAlert) oldItem == newItem
                else false
            }
        }
    }
}
