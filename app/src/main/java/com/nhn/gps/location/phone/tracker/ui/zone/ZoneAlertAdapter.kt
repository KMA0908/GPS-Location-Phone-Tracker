package com.nhn.gps.location.phone.tracker.ui.zone

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.databinding.ItemZoneAlertLocalBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZoneAlertAdapter(private val onClick: (ZoneAlert) -> Unit) :
    ListAdapter<ZoneAlert, ZoneAlertAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemZoneAlertLocalBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemZoneAlertLocalBinding,
        private val onClick: (ZoneAlert) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ZoneAlert) = with(binding) {
            tvAlertTitle.text = if (item.isEnter) "${item.userName} entered ${item.zoneName}" else "${item.userName} left ${item.zoneName}"
            tvAlertSubtitle.text = "${item.status.label} zone · ${FORMAT.format(Date(item.time))}"
            tvAlertType.text = if (item.isEnter) "IN" else "OUT"
            root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val FORMAT = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())
        val DIFF = object : DiffUtil.ItemCallback<ZoneAlert>() {
            override fun areItemsTheSame(oldItem: ZoneAlert, newItem: ZoneAlert) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ZoneAlert, newItem: ZoneAlert) = oldItem == newItem
        }
    }
}
