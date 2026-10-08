package com.urfeed.app.data.repository

import android.content.Context
import com.urfeed.app.data.local.AuthPreferences
import com.urfeed.app.data.remote.ApiService
import com.urfeed.app.data.remote.AuthRequest
import com.urfeed.app.data.remote.LoginRequest
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class RemoteAuthRepository(context: Context, private val api: ApiService) : AuthRepository {
    private val preferences = AuthPreferences(context.applicationContext)

    override val currentUser: Flow<User?> = preferences.currentUser
    override val isAuthenticated: Flow<Boolean> = preferences.isAuthenticated
    override val selectedInterests: Flow<List<String>> = preferences.selectedInterests

    override suspend fun initialize(): User? {
        val token = currentToken() ?: return null
        return try {
            val user = api.me().toDomain()
            preferences.saveSession(token, user)
            user
        } catch (error: retrofit2.HttpException) {
            if (error.code() == 401) preferences.clearSession()
            preferences.currentUser.first()
        } catch (_: java.io.IOException) {
            preferences.currentUser.first()
        }
    }

    override suspend fun currentToken(): String? = preferences.accessToken.first()

    override suspend fun login(username: String, password: String): User? {
        val response = api.login(LoginRequest(username, password))
        val user = response.user.toDomain()
        preferences.saveSession(response.accessToken, user)
        return user
    }

    override suspend fun register(fullName: String, username: String, email: String, password: String): User? {
        val response = api.register(AuthRequest(username = username, email = email, password = password, displayName = fullName))
        val user = response.user.toDomain()
        preferences.saveSession(response.accessToken, user)
        return user
    }

    override suspend fun saveSelectedInterests(interests: List<String>) {
        preferences.saveSelectedInterests(interests)
        runCatching {
            api.updateProfile(mapOf("interests" to interests))
        }.onFailure { if (it !is java.io.IOException) throw it }
    }

    override suspend fun logout() = preferences.clearSession()
}
