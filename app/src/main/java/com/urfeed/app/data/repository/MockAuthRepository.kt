package com.urfeed.app.data.repository

import android.content.Context
import com.urfeed.app.data.local.AuthPreferences
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MockAuthRepository(private val context: Context) : AuthRepository {
    private val authPreferences = AuthPreferences(context)

    override val currentUser: Flow<User?> = authPreferences.currentUser
    override val isAuthenticated: Flow<Boolean> = authPreferences.isAuthenticated
    override val selectedInterests: Flow<List<String>> = authPreferences.selectedInterests

    override suspend fun initialize(): User? {
        return currentUser.first()
    }

    override suspend fun currentToken(): String? = null

    override suspend fun login(username: String, password: String): User? {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank() || password.length < 6) return null

        val user = User(
            id = "demo-user",
            fullName = "Demo User",
            username = trimmedUsername.ifEmpty { "demo" },
            email = "demo@urfeed.app",
            bio = "Building ideas, one post at a time.",
            interests = listOf("Technology", "AI", "Travel")
        )

        authPreferences.saveSession(user)
        return user
    }

    override suspend fun register(
        fullName: String,
        username: String,
        email: String,
        password: String
    ): User? {
        val cleanName = fullName.trim()
        val cleanUsername = username.trim()
        val cleanEmail = email.trim()
        if (cleanName.isBlank() || cleanUsername.isBlank() || cleanEmail.isBlank() || password.length < 6) {
            return null
        }

        val user = User(
            fullName = cleanName,
            username = cleanUsername,
            email = cleanEmail,
            bio = "New to URFeed.",
            interests = emptyList()
        )
        authPreferences.saveSession(user)
        return user
    }

    override suspend fun saveSelectedInterests(interests: List<String>) {
        authPreferences.saveSelectedInterests(interests)
    }

    override suspend fun logout() {
        authPreferences.clearSession()
    }
}
