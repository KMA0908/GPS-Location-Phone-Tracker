package com.nhn.gps.location.phone.tracker.util

import androidx.annotation.DrawableRes
import com.nhn.gps.location.phone.tracker.R

data class LocalAvatar(
    val key: String,
    @DrawableRes val drawableRes: Int
)

object AvatarHelper {

    const val DEFAULT_AVATAR_KEY = "avatar_default"

    val avatars: List<LocalAvatar> = listOf(
        LocalAvatar("avatar_default", R.drawable.ic_profile),
        LocalAvatar("avatar_01", R.drawable.ic_avt),
        LocalAvatar("avatar_02", R.drawable.ic_avt_find_friend),
        LocalAvatar("avatar_03", R.drawable.ic_avt_location)
    )

    private val avatarMap = avatars.associate { it.key to it.drawableRes }

    @DrawableRes
    fun getDrawableRes(avatarKey: String?): Int? {
        if (avatarKey.isNullOrEmpty()) return null
        return avatarMap[avatarKey]
    }
}
