package com.nhn.gps.location.phone.tracker.ui.friend

internal object FriendCode {
    private const val PREFIX = "gps_friend:"

    fun encode(installationId: String): String = installationId.trim()

    fun parse(value: String): String? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        val id = if (normalized.startsWith(PREFIX, ignoreCase = true)) {
            normalized.substring(PREFIX.length).trim()
        } else {
            normalized
        }
        if (id.isBlank() || ':' in id || '/' in id || '.' in id) return null
        return id
    }
}
