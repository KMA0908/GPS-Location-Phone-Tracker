package com.nhn.gps.location.phone.tracker.ui.zone

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlertType
import com.nhn.gps.location.phone.tracker.databinding.ItemAlertHeaderBinding
import com.nhn.gps.location.phone.tracker.databinding.ItemZoneAlertLocalBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZoneAlertAdapter(
    private val onClick: (ZoneAlert) -> Unit,
    private val onDelete: (ZoneAlert) -> Unit,
) :
    ListAdapter<Any, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position) is String) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderHolder(ItemAlertHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemHolder(
                ItemZoneAlertLocalBinding.inflate(inflater, parent, false),
                onClick,
                onDelete,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is String -> (holder as HeaderHolder).bind(item)
            is ZoneAlert -> (holder as ItemHolder).bind(
                item = item,
                isFirstInGroup = position == 0 || getItem(position - 1) is String,
                isLastInGroup = position == itemCount - 1 || getItem(position + 1) is String,
            )
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
        private val onDelete: (ZoneAlert) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ZoneAlert, isFirstInGroup: Boolean, isLastInGroup: Boolean) = with(binding) {
            tvAlertTitle.text = root.context.getString(
                when (item.type) {
                    ZoneAlertType.ENTER -> R.string.zone_event_entered
                    ZoneAlertType.LEAVE -> R.string.zone_event_left
                    ZoneAlertType.NEAR_DANGEROUS -> R.string.zone_event_near_dangerous
                    ZoneAlertType.RETURNED_SAFE -> R.string.zone_event_returned_safe
                },
                item.userName,
                item.zoneName,
            )
            tvAlertSubtitle.text = FORMAT.format(Date(item.time))
            ivAlertType.setImageResource(item.iconRes())
            
            val color = if (item.isEnter) COLOR_ENTER else COLOR_LEAVE
            viewIconBg.backgroundTintList = ColorStateList.valueOf(color).withAlpha(40)

            val radius = 20f * root.resources.displayMetrics.density
            root.shapeAppearanceModel = root.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(if (isFirstInGroup) radius else 0f)
                .setTopRightCornerSize(if (isFirstInGroup) radius else 0f)
                .setBottomLeftCornerSize(if (isLastInGroup) radius else 0f)
                .setBottomRightCornerSize(if (isLastInGroup) radius else 0f)
                .build()
            (root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.bottomMargin = if (isLastInGroup) (12 * root.resources.displayMetrics.density).toInt() else 0
                root.layoutParams = params
            }
            divider.visibility = if (isLastInGroup) android.view.View.GONE else android.view.View.VISIBLE

            root.setOnClickListener { onClick(item) }
            btnMore.setOnClickListener { anchor ->
                PopupMenu(root.context, anchor).apply {
                    menu.add(0, MENU_DELETE, 0, R.string.remove)
                    setOnMenuItemClickListener { menuItem ->
                        if (menuItem.itemId == MENU_DELETE) {
                            onDelete(item)
                            true
                        } else {
                            false
                        }
                    }
                    show()
                }
            }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1
        const val COLOR_ENTER = 0xFF35C759.toInt()
        const val COLOR_LEAVE = 0xFF4A89BD.toInt()
        const val MENU_DELETE = 1
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
