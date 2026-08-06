package com.nhn.gps.location.phone.tracker.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.databinding.ItemFamousPlaceBinding

class FamousPlaceAdapter(
    private val onClick: (FamousPlaceModel) -> Unit,
    private val onFavoriteClick: (FamousPlaceModel) -> Unit
) : ListAdapter<FamousPlaceModel, FamousPlaceAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFamousPlaceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFamousPlaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FamousPlaceModel) = with(binding) {
            tvName.text = item.name
            tvLocation.text = item.location
            ivThumbnail.setImageResource(item.imageRes)
            ratingBar.rating = item.rating
            tvReviewCount.text = "(${item.reviewCount} reviews)"
            tvDistance.text = "${item.distanceKm} km away"
            
            // Note: In a real app, we would update ivFavorite icon based on item.isFavorite
            // But for now, we follow the requirement to just bind and handle clicks.

            root.setOnClickListener { onClick(item) }
            ivFavorite.setOnClickListener { onFavoriteClick(item) }
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
