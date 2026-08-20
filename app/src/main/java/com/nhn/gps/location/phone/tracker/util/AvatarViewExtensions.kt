package com.nhn.gps.location.phone.tracker.util

import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.nhn.gps.location.phone.tracker.R

fun ImageView.loadAvatar(
    avatarKey: String?,
    avatarUrl: String?,
    localPath: String? = null,
    @DrawableRes fallbackRes: Int = R.drawable.ic_avt
) {
    if (!localPath.isNullOrBlank()) {
        Glide.with(this).clear(this)
        Glide.with(this)
            .load(localPath)
            .circleCrop()
            .placeholder(fallbackRes)
            .error(fallbackRes)
            .into(this)
        return
    }

    val localRes = AvatarHelper.getDrawableRes(avatarKey)

    if (localRes != null) {
        // Clear any pending Glide request to prevent image flicker or wrong image display
        Glide.with(this).clear(this)
        setImageResource(localRes)
    } else if (!avatarUrl.isNullOrEmpty() && avatarUrl != "null") {
        Glide.with(this)
            .load(avatarUrl)
            .circleCrop()
            .placeholder(fallbackRes)
            .error(fallbackRes)
            .into(this)
    } else {
        Glide.with(this).clear(this)
        setImageResource(fallbackRes)
    }
}
