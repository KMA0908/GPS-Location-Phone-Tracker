package com.nhn.gps.location.phone.tracker.ui.zone

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneType
import com.nhn.gps.location.phone.tracker.databinding.ItemZoneLocalBinding

class ZoneAdapter(
    private val onClick: (Zone) -> Unit,
    private val onActionClick: ((Zone) -> Unit)? = null
) :
    ListAdapter<Zone, ZoneAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemZoneLocalBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick,
        onActionClick
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemZoneLocalBinding,
        private val onClick: (Zone) -> Unit,
        private val onActionClick: ((Zone) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Zone) = with(binding) {
            tvZoneName.text = item.name
            tvZoneAddress.text = if (item.address.isNotBlank()) {
                "${item.address} • ${item.radiusMeters}m"
            } else {
                "${item.radiusMeters}m"
            }
            
            // Set gradient background and icon based on type
            when(item.type) {
                ZoneType.HOME -> {
                    layoutIcon.setBackgroundResource(R.drawable.bg_zone_home)
                    ivZoneIcon.setImageResource(R.drawable.ic_home_zone)
                }
                ZoneType.SCHOOL -> {
                    layoutIcon.setBackgroundResource(R.drawable.bg_zone_school)
                    ivZoneIcon.setImageResource(R.drawable.ic_school_zone)
                }
                ZoneType.WORK -> {
                    layoutIcon.setBackgroundResource(R.drawable.bg_zone_work)
                    ivZoneIcon.setImageResource(R.drawable.ic_bag_zone)
                }
                else -> {
                    layoutIcon.setBackgroundResource(R.drawable.bg_zone_home)
                    ivZoneIcon.setImageResource(R.drawable.ic_home_zone)
                }
            }

            root.setOnClickListener { onClick(item) }
            btnAction.setOnClickListener { onActionClick?.invoke(item) ?: onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Zone>() {
            override fun areItemsTheSame(oldItem: Zone, newItem: Zone) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Zone, newItem: Zone) = oldItem == newItem
        }
    }
}
