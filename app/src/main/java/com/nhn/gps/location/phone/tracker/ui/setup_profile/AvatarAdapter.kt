package com.nhn.gps.location.phone.tracker.ui.setup_profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.databinding.ItemAvatarOptionBinding
import com.nhn.gps.location.phone.tracker.util.LocalAvatar

class AvatarAdapter(
    private var selectedKey: String,
    private val onAvatarSelected: (String) -> Unit
) : ListAdapter<LocalAvatar, AvatarAdapter.ViewHolder>(AvatarDiffCallback()) {

    fun setSelectedKey(key: String) {
        if (selectedKey == key) return
        val oldKey = selectedKey
        selectedKey = key
        
        currentList.forEachIndexed { index, avatar ->
            if (avatar.key == oldKey || avatar.key == key) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemAvatarOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAvatarOptionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LocalAvatar) = with(binding) {
            imgAvatar.setImageResource(item.drawableRes)
            
            val isSelected = item.key == selectedKey
            cardAvatar.strokeWidth = if (isSelected) (2 * root.resources.displayMetrics.density).toInt() else 0
            
            root.setOnClickListener {
                onAvatarSelected(item.key)
            }
        }
    }

    class AvatarDiffCallback : DiffUtil.ItemCallback<LocalAvatar>() {
        override fun areItemsTheSame(oldItem: LocalAvatar, newItem: LocalAvatar): Boolean = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: LocalAvatar, newItem: LocalAvatar): Boolean = oldItem == newItem
    }
}
