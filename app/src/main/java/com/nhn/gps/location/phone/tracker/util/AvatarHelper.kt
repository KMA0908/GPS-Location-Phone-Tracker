package com.nhn.gps.location.phone.tracker.util

import androidx.annotation.DrawableRes
import com.nhn.gps.location.phone.tracker.R

data class LocalAvatar(
    val key: String,
    @DrawableRes val drawableRes: Int
)

object AvatarHelper {

    const val DEFAULT_AVATAR_KEY = "avatar_01"
    private const val LEGACY_DEFAULT_AVATAR_KEY = "avatar_default"

    val avatars: List<LocalAvatar> = listOf(
        LocalAvatar("avatar_01", R.drawable.ic_avt_1),
        LocalAvatar("avatar_02", R.drawable.ic_avt_2),
        LocalAvatar("avatar_03", R.drawable.ic_avt_3),
        LocalAvatar("avatar_04", R.drawable.ic_avt_4),
        LocalAvatar("avatar_05", R.drawable.ic_avt_5),
        LocalAvatar("avatar_06", R.drawable.ic_avt_6),
        LocalAvatar("avatar_07", R.drawable.ic_avt_7),
        LocalAvatar("avatar_08", R.drawable.ic_avt_8),
        LocalAvatar("avatar_09", R.drawable.ic_avt_9),
    )

    private val avatarMap = avatars.associate { it.key to it.drawableRes }
        .toMutableMap().apply {
            put(LEGACY_DEFAULT_AVATAR_KEY, R.drawable.ic_avt_1)
        }

    fun normalizeKey(avatarKey: String?): String = when {
        avatarKey == LEGACY_DEFAULT_AVATAR_KEY -> DEFAULT_AVATAR_KEY
        avatars.any { it.key == avatarKey } -> avatarKey.orEmpty()
        else -> DEFAULT_AVATAR_KEY
    }

    @DrawableRes
    fun getDrawableRes(avatarKey: String?): Int? {
        if (avatarKey.isNullOrEmpty()) return null
        return avatarMap[avatarKey]
    }
}
