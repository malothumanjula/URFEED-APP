package com.urfeed.app.data.remote

import com.google.gson.annotations.SerializedName
import com.urfeed.app.domain.model.PostVisibility
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.model.User

 data class UserDto(
    val id: String,
    val username: String,
    val email: String? = null,
    @SerializedName("display_name") val displayName: String,
    val bio: String = "",
    @SerializedName("profile_image_url") val profileImageUrl: String? = null,
    val interests: List<String> = emptyList(),
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("posts_count") val postsCount: Int = 0
) {
    fun toDomain() = User(
        id = id, fullName = displayName, username = username, email = email.orEmpty(),
        profileImage = profileImageUrl, bio = bio, followersCount = followersCount,
        followingCount = followingCount, postsCount = postsCount, interests = interests
    )
}

data class TagDto(
    val id: String,
    val slug: String,
    val name: String,
    val category: String,
    val subcategory: String? = null,
    val description: String = "",
    val confidence: Float = 1f,
    val source: String = "AI"
) {
    fun toDomain() = Tag(id, name, slug, category, subcategory, description, listOf(name))
}

data class AuthRequest(val username: String, val email: String? = null, val password: String, @SerializedName("display_name") val displayName: String? = null)
data class LoginRequest(@SerializedName("username_or_email") val usernameOrEmail: String, val password: String)
data class AuthResponse(@SerializedName("access_token") val accessToken: String, val tokenType: String, val user: UserDto)
data class FollowResponse(val following: Boolean)

data class PostCreateRequest(val content: String, @SerializedName("image_url") val imageUrl: String?, val visibility: String, val tags: List<String>)
data class StatusCreateRequest(val content: String, @SerializedName("image_url") val imageUrl: String?, val visibility: String, val tags: List<String>)

data class PostDto(
    val id: String,
    val author: UserDto,
    val content: String,
    @SerializedName("image_url") val imageUrl: String?,
    val visibility: String,
    @SerializedName("created_at") val createdAt: String,
    val tags: List<TagDto> = emptyList(),
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("liked_by_me") val likedByMe: Boolean = false,
    @SerializedName("comments_count") val commentsCount: Int = 0
) {
    fun toDomain() = SocialPost(
        id = id, author = author.displayName, handle = author.username, authorId = author.id,
        profileImage = author.profileImageUrl, time = "now", text = content,
        tags = tags.map { "#${it.name.filter(Char::isLetterOrDigit)}" }, canonicalTags = tags.map(TagDto::toDomain),
        likes = likesCount, comments = commentsCount, imageUrl = imageUrl, hasImage = imageUrl != null,
        visibility = runCatching { PostVisibility.valueOf(visibility) }.getOrDefault(PostVisibility.PUBLIC),
        isLiked = likedByMe
    )
}

data class StatusDto(
    val id: String,
    val author: UserDto,
    val content: String,
    @SerializedName("image_url") val imageUrl: String?,
    val visibility: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("expires_at") val expiresAt: String,
    val tags: List<TagDto> = emptyList()
) {
    fun toDomain() = Status(
        id = id, userId = author.id, username = author.username, profileImage = author.profileImageUrl,
        content = content, mediaUrl = imageUrl,
        visibility = runCatching { PostVisibility.valueOf(visibility) }.getOrDefault(PostVisibility.PUBLIC),
        createdAt = runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis()),
        expiresAt = runCatching { java.time.Instant.parse(expiresAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis()),
        tags = tags.map { "#${it.name.filter(Char::isLetterOrDigit)}" }, canonicalTags = tags.map(TagDto::toDomain)
    )
}

data class SearchResponseDto(
    val query: String,
    val posts: List<PostDto>,
    val statuses: List<StatusDto>,
    @SerializedName("matched_tags") val matchedTags: List<TagDto>,
    val categories: List<String>
)
