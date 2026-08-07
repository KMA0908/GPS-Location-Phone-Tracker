package com.nhn.gps.location.phone.tracker.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.databinding.ItemExploreVerticalCardBinding

class ExploreCardAdapter(
    private val onClick: (FamousPlaceModel) -> Unit,
    private val onBindPhoto: (FamousPlaceModel, ImageView) -> Unit
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

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.binding.ivThumbnail)
    }

    inner class ViewHolder(val binding: ItemExploreVerticalCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FamousPlaceModel) = with(binding) {
            tvName.text = item.name
            tvLocation.text = item.location
            ivThumbnail.setImageResource(R.drawable.ic_paris) // Placeholder
            onBindPhoto(item, ivThumbnail)

            tvAttribution.text = item.photoMetadata?.attributions ?: ""
            tvAttribution.visibility = if (tvAttribution.text.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE

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
