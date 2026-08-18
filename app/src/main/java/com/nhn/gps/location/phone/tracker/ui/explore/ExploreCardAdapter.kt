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
import com.nhn.gps.location.phone.tracker.databinding.ItemExploreBinding
import com.nhn.gps.location.phone.tracker.databinding.ItemExploreVerticalCardBinding

class ExploreCardAdapter(
    private val onClick: (FamousPlaceModel) -> Unit,
    private val onBindPhoto: (FamousPlaceModel, ImageView) -> Unit,
    private val layoutMode: LayoutMode = LayoutMode.VERTICAL
) : ListAdapter<FamousPlaceModel, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    enum class LayoutMode {
        VERTICAL,
        HOME_HORIZONTAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (layoutMode == LayoutMode.VERTICAL) {
            val binding = ItemExploreVerticalCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            VerticalViewHolder(binding)
        } else {
            val binding = ItemExploreBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            HomeHorizontalViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is VerticalViewHolder) {
            holder.bind(item)
        } else if (holder is HomeHorizontalViewHolder) {
            holder.bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        val imageView = if (holder is VerticalViewHolder) {
            holder.binding.ivThumbnail
        } else if (holder is HomeHorizontalViewHolder) {
            holder.binding.ivThumbnail
        } else null
        
        imageView?.let {
            Glide.with(holder.itemView.context).clear(it)
        }
    }

    inner class VerticalViewHolder(val binding: ItemExploreVerticalCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FamousPlaceModel) = with(binding) {
            tvName.text = item.name
            ivThumbnail.setImageResource(item.imageRes.takeIf { it != 0 } ?: R.drawable.ic_paris)
            onBindPhoto(item, ivThumbnail)

            root.setOnClickListener { onClick(item) }
        }
    }

    inner class HomeHorizontalViewHolder(val binding: ItemExploreBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FamousPlaceModel) = with(binding) {
            tvName.text = item.name
            ivThumbnail.setImageResource(item.imageRes.takeIf { it != 0 } ?: R.drawable.ic_paris)
            onBindPhoto(item, ivThumbnail)

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
