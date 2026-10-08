package com.urfeed.app.domain.repository

import com.urfeed.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    val isAuthenticated: Flow<Boolean>
    val selectedInterests: Flow<List<String>>

    suspend fun initialize(): User?
    suspend fun currentToken(): String?
    suspend fun login(username: String, password: String): User?
    suspend fun register(
        fullName: String,
        username: String,
        email: String,
        password: String
    ): User?
    suspend fun saveSelectedInterests(interests: List<String>)
    suspend fun logout()
}
