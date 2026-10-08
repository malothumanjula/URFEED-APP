package com.urfeed.app.data.repository

import com.urfeed.app.data.local.AuthPreferences
import com.urfeed.app.data.remote.ApiService
import com.urfeed.app.data.remote.UserDto
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.first

class RemoteUserRepository(private val preferences: AuthPreferences, private val api: ApiService) : UserRepository {
    private var users = emptyList<User>()

    override suspend fun getUsers(): List<User> {
        val remote = api.searchUsers("").map { it.toDomain() }
        val current = preferences.currentUser.first()
        users = (listOfNotNull(current) + remote).distinctBy(User::id)
        return users
    }

    override suspend fun updateProfile(user: User) {
        val updated = api.updateProfile(
            mapOf(
                "full_name" to user.fullName,
                "display_name" to user.fullName,
                "username" to user.username,
                "bio" to user.bio,
                "profile_image_url" to user.profileImage,
                "interests" to user.interests
            )
        ).toDomain()
        preferences.updateUser(updated)
        users = (users.filterNot { it.id == updated.id } + updated).distinctBy(User::id)
    }

    override suspend fun setFollowing(userId: String, following: Boolean) {
        if (following) api.follow(userId) else api.unfollow(userId)
        preferences.currentUser.first()?.let { current ->
            preferences.updateUser(current.copy(followingCount = (current.followingCount + if (following) 1 else -1).coerceAtLeast(0)))
        }
        users = users.map { user ->
            if (user.id == userId) user.copy(followersCount = (user.followersCount + if (following) 1 else -1).coerceAtLeast(0)) else user
        }
    }

    override suspend fun getFollowingIds(userId: String): Set<String> = api.following(userId).map(UserDto::id).toSet()

    override suspend fun getFollowerIds(userId: String): Set<String> = api.followers(userId).map(UserDto::id).toSet()

}
