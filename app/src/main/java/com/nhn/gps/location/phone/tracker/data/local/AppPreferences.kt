package com.nhn.gps.location.phone.tracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
)

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val appOpenCount: Flow<Int> = context.appDataStore.safeData.map { preferences ->
        preferences[APP_OPEN_COUNT] ?: 0
    }

    suspend fun incrementAppOpenCount() {
        context.appDataStore.edit { preferences ->
            preferences[APP_OPEN_COUNT] = (preferences[APP_OPEN_COUNT] ?: 0) + 1
        }
    }

    suspend fun currentAppOpenCount(): Int = appOpenCount.first()

    val isLocationEnabled: Flow<Boolean> = context.appDataStore.safeData.map { preferences ->
        preferences[LOCATION_ENABLED] ?: false
    }

    val isCameraEnabled: Flow<Boolean> = context.appDataStore.safeData.map { preferences ->
        preferences[CAMERA_ENABLED] ?: false
    }

    val isNotificationEnabled: Flow<Boolean> = context.appDataStore.safeData.map { preferences ->
        preferences[NOTIFICATION_ENABLED] ?: false
    }

    suspend fun setLocationEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[LOCATION_ENABLED] = enabled
        }
    }

    suspend fun setCameraEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[CAMERA_ENABLED] = enabled
        }
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[NOTIFICATION_ENABLED] = enabled
        }
    }

    val isPermissionShown: Flow<Boolean> = context.appDataStore.safeData.map { preferences ->
        preferences[PERMISSION_SHOWN] ?: false
    }

    suspend fun setPermissionShown(shown: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[PERMISSION_SHOWN] = shown
        }
    }

    private val DataStore<Preferences>.safeData: Flow<Preferences>
        get() = data.catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }

    private companion object {
        val APP_OPEN_COUNT = intPreferencesKey("app_open_count")
        val LOCATION_ENABLED = booleanPreferencesKey("location_enabled")
        val CAMERA_ENABLED = booleanPreferencesKey("camera_enabled")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val PERMISSION_SHOWN = booleanPreferencesKey("permission_shown")
    }
}
