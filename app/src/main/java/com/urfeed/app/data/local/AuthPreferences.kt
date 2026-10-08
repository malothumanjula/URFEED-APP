package com.urfeed.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.urfeed.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATA_STORE_NAME = "urfeed_session"

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

class AuthPreferences(private val context: Context) {
    private object Keys {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val FULL_NAME = stringPreferencesKey("full_name")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val BIO = stringPreferencesKey("bio")
        val PROFILE_IMAGE = stringPreferencesKey("profile_image")
        val INTERESTS = stringPreferencesKey("interests")
    }

    val currentUser: Flow<User?> = context.authDataStore.data.map { prefs ->
        val isLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: !prefs[Keys.TOKEN].isNullOrBlank()
        if (!isLoggedIn) return@map null

        User(
            id = prefs[Keys.USER_ID] ?: "local-user",
            fullName = prefs[Keys.FULL_NAME] ?: "Guest User",
            username = prefs[Keys.USERNAME] ?: "guest",
            email = prefs[Keys.EMAIL] ?: "guest@urfeed.app",
            bio = prefs[Keys.BIO] ?: "Welcome to URFeed.",
            profileImage = prefs[Keys.PROFILE_IMAGE],
            interests = (prefs[Keys.INTERESTS] ?: "").split("|").filter { it.isNotBlank() }
        )
    }

    val isAuthenticated: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[Keys.IS_LOGGED_IN] ?: !prefs[Keys.TOKEN].isNullOrBlank()
    }

    val accessToken: Flow<String?> = context.authDataStore.data.map { it[Keys.TOKEN] }

    val selectedInterests: Flow<List<String>> = context.authDataStore.data.map { prefs ->
        (prefs[Keys.INTERESTS] ?: "").split("|").filter { it.isNotBlank() }
    }

    suspend fun saveSession(user: User) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = true
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.FULL_NAME] = user.fullName
            prefs[Keys.USERNAME] = user.username
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.BIO] = user.bio
            user.profileImage?.let { prefs[Keys.PROFILE_IMAGE] = it } ?: prefs.remove(Keys.PROFILE_IMAGE)
            prefs[Keys.INTERESTS] = user.interests.joinToString(separator = "|")
        }
    }

    suspend fun saveSession(token: String, user: User) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = true
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.FULL_NAME] = user.fullName
            prefs[Keys.USERNAME] = user.username
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.BIO] = user.bio
            user.profileImage?.let { prefs[Keys.PROFILE_IMAGE] = it } ?: prefs.remove(Keys.PROFILE_IMAGE)
            prefs[Keys.INTERESTS] = user.interests.joinToString(separator = "|")
        }
    }

    suspend fun updateUser(user: User) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.FULL_NAME] = user.fullName
            prefs[Keys.USERNAME] = user.username
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.BIO] = user.bio
            user.profileImage?.let { prefs[Keys.PROFILE_IMAGE] = it } ?: prefs.remove(Keys.PROFILE_IMAGE)
            prefs[Keys.INTERESTS] = user.interests.joinToString(separator = "|")
        }
    }

    suspend fun saveSelectedInterests(interests: List<String>) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.INTERESTS] = interests.joinToString(separator = "|")
        }
    }

    suspend fun clearSession() {
        context.authDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
