package com.nhn.gps.location.phone.tracker.ui.friend

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.databinding.ItemFriendBinding
import com.nhn.gps.location.phone.tracker.util.loadAvatar

class FriendAdapter(
    private val showMoreButton: Boolean = true,
    private val onItemClick: (FriendLocation) -> Unit,
    private val onMoreClick: ((FriendLocation) -> Unit)? = null
) : ListAdapter<FriendLocation, FriendAdapter.FriendViewHolder>(FriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendViewHolder(private val binding: ItemFriendBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(friend: FriendLocation) = with(binding) {
            tvName.text = friend.name
            tvAddress.text = "ID: ${friend.id}"
            
            imgAvatar.loadAvatar(friend.avatarKey, friend.avatarUrl)
            
            if (showMoreButton) {
                btnMore.visibility = android.view.View.VISIBLE
                tvLastSeen.visibility = android.view.View.GONE
                btnMore.setOnClickListener { onMoreClick?.invoke(friend) }
            } else {
                btnMore.visibility = android.view.View.GONE
                tvLastSeen.visibility = android.view.View.VISIBLE
                tvLastSeen.text = com.nhn.gps.location.phone.tracker.util.TimeAgo.formatPresence(
                    timestamp = friend.updatedAt,
                    hasOnline = friend.hasOnline,
                    trackingAvailable = friend.trackingAvailable,
                )
            }

            root.setOnClickListener { onItemClick(friend) }
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
