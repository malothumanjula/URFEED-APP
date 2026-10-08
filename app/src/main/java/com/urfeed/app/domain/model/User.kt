package com.urfeed.app.domain.model

data class User(
    val id: String = "local-user",
    val fullName: String = "Aarav Sharma",
    val username: String = "aarav",
    val email: String = "aarav@example.com",
    val profileImage: String? = null,
    val bio: String = "Product designer building social experiences.",
    val followersCount: Int = 2480,
    val followingCount: Int = 412,
    val postsCount: Int = 128,
    val interests: List<String> = listOf("Technology", "AI", "Python", "Travel")
) {
    val followers: Int get() = followersCount
    val following: Int get() = followingCount
    val posts: Int get() = postsCount
}
