package com.nhn.gpstracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    }
}
