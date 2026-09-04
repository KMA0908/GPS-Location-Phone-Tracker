package com.nhn.gps.location.phone.tracker.data.local

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-owned identity used by the profile/friend feature.
 *
 * No Firebase Authentication account is created. A random ID is stable for the
 * lifetime of one installation and is cleared on uninstall because app backup
 * is disabled. A reinstall therefore starts a new, unrelated profile.
 */
@Singleton
class InstallationIdentity @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun getOrCreateId(): String = synchronized(this) {
        preferences.getString(KEY_INSTALLATION_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: createId().also { id ->
                preferences.edit().putString(KEY_INSTALLATION_ID, id).apply()
            }
    }

    /**
     * A private proof of ownership for this installation ID. It is never shown
     * as a friend code and is only sent to App Check protected Cloud Functions.
     */
    fun getOrCreateOwnerSecret(): String = synchronized(this) {
        preferences.getString(KEY_OWNER_SECRET, null)
            ?.takeIf { it.length >= MIN_OWNER_SECRET_LENGTH }
            ?: ByteArray(OWNER_SECRET_BYTES).let { bytes ->
                SecureRandom().nextBytes(bytes)
                Base64.encodeToString(
                    bytes,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                )
            }.also { secret ->
                preferences.edit().putString(KEY_OWNER_SECRET, secret).apply()
            }
    }

    /**
     * Starts a brand-new no-login profile if an extremely unlikely ID collision
     * is detected or if an older build left an inaccessible identity behind.
     */
    fun rotateForNewProfile(): String = synchronized(this) {
        val replacement = UUID.randomUUID().toString().uppercase(Locale.ROOT)
        check(
            preferences.edit()
                .putString(KEY_INSTALLATION_ID, replacement)
                .remove(KEY_OWNER_SECRET)
                .commit(),
        ) { "Could not persist replacement installation identity" }
        replacement
    }

    private fun createId(): String = UUID.randomUUID().toString().uppercase(Locale.ROOT)

    private companion object {
        const val PREFERENCES_NAME = "installation_identity"
        const val KEY_INSTALLATION_ID = "installation_id"
        const val KEY_OWNER_SECRET = "owner_secret"
        const val OWNER_SECRET_BYTES = 32
        const val MIN_OWNER_SECRET_LENGTH = 32
    }
}
