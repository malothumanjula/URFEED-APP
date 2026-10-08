package com.urfeed.app.data.repository

import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.repository.UserRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MockUserRepository : UserRepository {
    private val mutex = Mutex()
    private var currentUser = User()
    private val users = mutableListOf(
        currentUser,
        User(id = "aisha", fullName = "Aisha Khan", username = "aisha", bio = "AI builder sharing experiments and learnings.", followersCount = 1830, followingCount = 294, postsCount = 86, interests = listOf("AI", "Python", "Startups")),
        User(id = "rohan", fullName = "Rohan Mehta", username = "rohan", bio = "Product designer. Better flows, one detail at a time.", followersCount = 940, followingCount = 510, postsCount = 54, interests = listOf("Design", "Product", "Technology")),
        User(id = "maya", fullName = "Maya Patel", username = "maya", bio = "Photographer, traveler, and curious maker.", followersCount = 2100, followingCount = 380, postsCount = 112, interests = listOf("Photography", "Travel", "Food"))
    )
    private val followingByUser = mutableMapOf("local-user" to mutableSetOf("aisha"))
    private val followerIds = mutableMapOf(
        "local-user" to mutableSetOf("rohan", "maya"),
        "aisha" to mutableSetOf("local-user")
    )

    override suspend fun getUsers(): List<User> = mutex.withLock {
        users.map { user ->
            if (user.id == currentUser.id) currentUser else user
        }
    }

    override suspend fun updateProfile(user: User) {
        mutex.withLock {
            val previousUserId = currentUser.id
            if (previousUserId != user.id) {
                followingByUser[user.id] = followingByUser.remove(previousUserId) ?: mutableSetOf()
                followerIds.values.forEach { followers ->
                    if (previousUserId in followers) {
                        followers.remove(previousUserId)
                        followers.add(user.id)
                    }
                }
                followerIds[user.id] = followerIds.remove(previousUserId) ?: mutableSetOf("rohan", "maya")
            }
            currentUser = user
            if (user.id !in followerIds) {
                followerIds[user.id] = mutableSetOf("rohan", "maya")
                followerIds[user.id]?.forEach { followerIds.getOrPut(it) { mutableSetOf() }.add(user.id) }
            }
            users.removeAll { it.id == user.id || it.id == previousUserId }
            users.add(0, user)
        }
    }

    override suspend fun setFollowing(userId: String, following: Boolean) {
        mutex.withLock {
            val myFollowing = followingByUser.getOrPut(currentUser.id) { mutableSetOf() }
            val wasFollowing = userId in myFollowing
            if (wasFollowing == following) return@withLock
            if (following) myFollowing.add(userId) else myFollowing.remove(userId)
            if (following) followerIds.getOrPut(userId) { mutableSetOf() }.add(currentUser.id)
            else followerIds[userId]?.remove(currentUser.id)
            users.indices.forEach { index ->
                val user = users[index]
                if (user.id == userId) {
                    users[index] = user.copy(followersCount = (user.followersCount + if (following) 1 else -1).coerceAtLeast(0))
                }
            }
            currentUser = currentUser.copy(
                followingCount = (currentUser.followingCount + if (following) 1 else -1).coerceAtLeast(0)
            )
            val currentIndex = users.indexOfFirst { it.id == currentUser.id }
            if (currentIndex >= 0) users[currentIndex] = currentUser
        }
    }

    override suspend fun getFollowingIds(userId: String): Set<String> = mutex.withLock {
        followingByUser[userId]?.toSet() ?: emptySet()
    }

    override suspend fun getFollowerIds(userId: String): Set<String> = mutex.withLock {
        followerIds[userId]?.toSet() ?: emptySet()
    }
}
