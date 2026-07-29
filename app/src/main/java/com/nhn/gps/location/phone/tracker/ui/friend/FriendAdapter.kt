package com.nhn.gps.location.phone.tracker.ui.friend

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.databinding.ItemFriendBinding

class FriendAdapter(
    private val onItemClick: (FriendLocation) -> Unit
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
