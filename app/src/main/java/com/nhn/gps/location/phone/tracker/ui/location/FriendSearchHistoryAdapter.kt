package com.nhn.gps.location.phone.tracker.ui.location

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.databinding.ItemFriendSearchHistoryBinding
import com.nhn.gps.location.phone.tracker.util.loadAvatar

class FriendSearchHistoryAdapter(
    private val onFriendClick: (FriendLocation) -> Unit,
    private val onRemoveClick: (FriendLocation) -> Unit
) : ListAdapter<FriendLocation, FriendSearchHistoryAdapter.HistoryViewHolder>(FriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemFriendSearchHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(private val binding: ItemFriendSearchHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(friend: FriendLocation) = with(binding) {
            tvName.text = friend.name
            tvId.text = "ID: ${friend.id}"
            
            imgAvatar.loadAvatar(friend.avatarKey, friend.avatarUrl)
            
            root.setOnClickListener { onFriendClick(friend) }
            btnRemoveHistory.setOnClickListener { onRemoveClick(friend) }
        }
    }

    class FriendDiffCallback : DiffUtil.ItemCallback<FriendLocation>() {
        override fun areItemsTheSame(oldItem: FriendLocation, newItem: FriendLocation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FriendLocation, newItem: FriendLocation): Boolean {
            return oldItem == newItem
        }
    }
}
