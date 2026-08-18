package com.nhn.gps.location.phone.tracker.ui.location

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.databinding.ItemFriendSearchHistoryBinding
import com.nhn.gps.location.phone.tracker.util.loadAvatar

class FriendSearchHistoryAdapter(
    private val onFriendClick: (FriendLocation) -> Unit,
    private val onRemoveClick: (FriendLocation) -> Unit,
) : ListAdapter<FriendLocation, FriendSearchHistoryAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemFriendSearchHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val binding: ItemFriendSearchHistoryBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        fun bind(friend: FriendLocation) = with(binding) {
            tvName.text = friend.name
            tvAddress.text = "ID: ${friend.id}"
            imgAvatar.loadAvatar(friend.avatarKey, friend.avatarUrl)
            root.setOnClickListener { onFriendClick(friend) }
            btnRemove.setOnClickListener { onRemoveClick(friend) }
        }
    }
    private object Diff : DiffUtil.ItemCallback<FriendLocation>() {
        override fun areItemsTheSame(a: FriendLocation, b: FriendLocation) = a.id == b.id
        override fun areContentsTheSame(a: FriendLocation, b: FriendLocation) = a == b
    }
}
