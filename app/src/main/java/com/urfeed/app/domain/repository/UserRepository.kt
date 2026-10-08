package com.urfeed.app.domain.repository

import com.urfeed.app.domain.model.User

interface UserRepository {
    suspend fun getUsers(): List<User>
    suspend fun updateProfile(user: User)
    suspend fun setFollowing(userId: String, following: Boolean)
    suspend fun getFollowingIds(userId: String): Set<String>
    suspend fun getFollowerIds(userId: String): Set<String>
}
