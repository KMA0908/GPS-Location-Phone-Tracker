package com.nhn.gps.location.phone.tracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nhn.gps.location.phone.tracker.data.model.FavoritePlaceRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
)

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val favoritePlacesFlow: Flow<List<FavoritePlaceRef>> = context.appDataStore.safeData.map { preferences ->
        val json = preferences[FAVORITE_PLACES_JSON] ?: "[]"
        try {
            val array = JSONArray(json)
            val list = mutableListOf<FavoritePlaceRef>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                if (id.isNotBlank()) {
                    list.add(FavoritePlaceRef(id, obj.optLong("savedAt")))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun toggleFavoritePlace(placeId: String) {
        if (placeId.isBlank()) return
        context.appDataStore.edit { preferences ->
            val json = preferences[FAVORITE_PLACES_JSON] ?: "[]"
            val list = try {
                val array = JSONArray(json)
                val temp = mutableListOf<FavoritePlaceRef>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank()) {
                        temp.add(FavoritePlaceRef(id, obj.optLong("savedAt")))
                    }
                }
                temp
            } catch (e: Exception) {
                mutableListOf()
            }

            val existingIndex = list.indexOfFirst { it.id == placeId }
            if (existingIndex != -1) {
                list.removeAt(existingIndex)
            } else {
                list.add(0, FavoritePlaceRef(placeId, System.currentTimeMillis()))
            }

            val newArray = JSONArray()
            list.forEach { ref ->
                val obj = JSONObject()
                obj.put("id", ref.id)
                obj.put("savedAt", ref.savedAt)
                newArray.put(obj)
            }
            preferences[FAVORITE_PLACES_JSON] = newArray.toString()
        }
    }

    data class FriendSearchHistoryEntry(
        val friendId: String,
        val searchedAt: Long
    )

    val friendSearchHistoryFlow: Flow<List<FriendSearchHistoryEntry>> = context.appDataStore.safeData.map { preferences ->
        val json = preferences[FRIEND_SEARCH_HISTORY_JSON] ?: "[]"
        try {
            val array = JSONArray(json)
            val list = mutableListOf<FriendSearchHistoryEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("friendId")
                if (id.isNotBlank()) {
                    list.add(FriendSearchHistoryEntry(id, obj.optLong("searchedAt")))
                }
            }
            list.distinctBy { it.friendId }
                .sortedByDescending { it.searchedAt }
                .take(2)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun recordFriendSearch(friendId: String) {
        if (friendId.isBlank()) return
        context.appDataStore.edit { preferences ->
            val json = preferences[FRIEND_SEARCH_HISTORY_JSON] ?: "[]"
            val list = try {
                val array = JSONArray(json)
                val temp = mutableListOf<FriendSearchHistoryEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("friendId")
                    if (id.isNotBlank()) {
                        temp.add(FriendSearchHistoryEntry(id, obj.optLong("searchedAt")))
                    }
                }
                temp
            } catch (e: Exception) {
                mutableListOf()
            }

            list.removeAll { it.friendId == friendId }
            list.add(0, FriendSearchHistoryEntry(friendId, System.currentTimeMillis()))
            
            val newList = list.take(2)
            val newArray = JSONArray()
            newList.forEach { entry ->
                val obj = JSONObject()
                obj.put("friendId", entry.friendId)
                obj.put("searchedAt", entry.searchedAt)
                newArray.put(obj)
            }
            preferences[FRIEND_SEARCH_HISTORY_JSON] = newArray.toString()
        }
    }

    suspend fun removeFriendSearchHistory(friendId: String) {
        if (friendId.isBlank()) return
        context.appDataStore.edit { preferences ->
            val json = preferences[FRIEND_SEARCH_HISTORY_JSON] ?: "[]"
            val list = try {
                val array = JSONArray(json)
                val temp = mutableListOf<FriendSearchHistoryEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("friendId")
                    if (id.isNotBlank()) {
                        temp.add(FriendSearchHistoryEntry(id, obj.optLong("searchedAt")))
                    }
                }
                temp
            } catch (e: Exception) {
                mutableListOf()
            }

            list.removeAll { it.friendId == friendId }
            
            val newArray = JSONArray()
            list.forEach { entry ->
                val obj = JSONObject()
                obj.put("friendId", entry.friendId)
                obj.put("searchedAt", entry.searchedAt)
                newArray.put(obj)
            }
            preferences[FRIEND_SEARCH_HISTORY_JSON] = newArray.toString()
        }
    }

    suspend fun clearFriendSearchHistory() {
        context.appDataStore.edit { preferences ->
            preferences.remove(FRIEND_SEARCH_HISTORY_JSON)
        }
    }

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

    val userName: Flow<String> = context.appDataStore.safeData.map { preferences ->
        preferences[USER_NAME] ?: ""
    }

    suspend fun setUserName(name: String) {
        context.appDataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }

    suspend fun setUserPhone(phone: String) {
        context.appDataStore.edit { preferences ->
            preferences[USER_PHONE] = phone
        }
    }

    val userAvatar: Flow<String> = context.appDataStore.safeData.map { preferences ->
        preferences[USER_AVATAR] ?: ""
    }

    val userAvatarKey: Flow<String> = context.appDataStore.safeData.map { preferences ->
        preferences[USER_AVATAR_KEY] ?: ""
    }

    suspend fun setUserAvatar(avatarUrl: String) {
        context.appDataStore.edit { preferences ->
            preferences[USER_AVATAR] = avatarUrl
        }
    }

    suspend fun setUserAvatarKey(avatarKey: String) {
        context.appDataStore.edit { preferences ->
            preferences[USER_AVATAR_KEY] = avatarKey
        }
    }

    val userId: Flow<String?> = context.appDataStore.safeData.map { preferences ->
        preferences[USER_ID]
    }

    suspend fun setUserId(id: String) {
        context.appDataStore.edit { preferences ->
            preferences[USER_ID] = id
        }
    }

    val zonesJson: Flow<String> = context.appDataStore.safeData.map { preferences ->
        preferences[ZONES_JSON] ?: "[]"
    }

    val zoneAlertsJson: Flow<String> = context.appDataStore.safeData.map { preferences ->
        preferences[ZONE_ALERTS_JSON] ?: "[]"
    }

    val zoneStatesJson: Flow<String> = context.appDataStore.safeData.map { preferences ->
        preferences[ZONE_STATES_JSON] ?: "{}"
    }

    suspend fun saveZoneData(zones: String, alerts: String, states: String) {
        context.appDataStore.edit { preferences ->
            preferences[ZONES_JSON] = zones
            preferences[ZONE_ALERTS_JSON] = alerts
            preferences[ZONE_STATES_JSON] = states
        }
    }

    suspend fun setZonesJson(json: String) {
        context.appDataStore.edit { it[ZONES_JSON] = json }
    }

    suspend fun setZoneAlertsJson(json: String) {
        context.appDataStore.edit { it[ZONE_ALERTS_JSON] = json }
    }

    suspend fun setZoneStatesJson(json: String) {
        context.appDataStore.edit { it[ZONE_STATES_JSON] = json }
    }

    val selectedLanguage: Flow<String> = context.appDataStore.safeData.map { preferences ->
        preferences[SELECTED_LANGUAGE] ?: "en"
    }

    suspend fun saveSelectedLanguage(languageCode: String) {
        context.appDataStore.edit { preferences ->
            preferences[SELECTED_LANGUAGE] = languageCode
        }
    }

    suspend fun getSelectedLanguage(): String = selectedLanguage.first()

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
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
        val USER_AVATAR_KEY = stringPreferencesKey("user_avatar_key")
        val USER_ID = stringPreferencesKey("user_id")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val ZONES_JSON = stringPreferencesKey("zones_json")
        val ZONE_ALERTS_JSON = stringPreferencesKey("zone_alerts_json")
        val ZONE_STATES_JSON = stringPreferencesKey("zone_states_json")
        val FAVORITE_PLACES_JSON = stringPreferencesKey("favorite_places_json")
        val FRIEND_SEARCH_HISTORY_JSON = stringPreferencesKey("friend_search_history_json")
    }
}
