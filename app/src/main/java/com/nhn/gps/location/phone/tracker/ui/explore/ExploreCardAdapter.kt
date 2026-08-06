package com.nhn.gps.location.phone.tracker.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.databinding.ItemExploreVerticalCardBinding

class ExploreCardAdapter(
    private val onClick: (FamousPlaceModel) -> Unit
) : ListAdapter<FamousPlaceModel, ExploreCardAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExploreVerticalCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemExploreVerticalCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FamousPlaceModel) = with(binding) {
            tvName.text = item.name
            tvLocation.text = item.location
            ivThumbnail.setImageResource(item.imageRes)
            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FamousPlaceModel>() {
            override fun areItemsTheSame(
                oldItem: FamousPlaceModel,
                newItem: FamousPlaceModel
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: FamousPlaceModel,
                newItem: FamousPlaceModel
            ): Boolean = oldItem == newItem
        }
    }
}
