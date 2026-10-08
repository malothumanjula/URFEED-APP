package com.urfeed.app.domain.model

import androidx.compose.ui.graphics.Color

data class StoryItem(
    val id: String,
    val name: String,
    val accent: Color,
    val isMe: Boolean = false
)

data class SocialPost(
    val id: String,
    val author: String,
    val handle: String,
    val time: String,
    val text: String,
    val tags: List<String> = emptyList(),
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val hasImage: Boolean = false,
    val authorId: String = "",
    val profileImage: String? = null,
    val imageUrl: String? = null,
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    val isLiked: Boolean = false,
    val isShared: Boolean = false,
    val canonicalTags: List<Tag> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class PostVisibility {
    PUBLIC,
    FOLLOWERS
}

data class Status(
    val id: String,
    val userId: String,
    val username: String,
    val profileImage: String? = null,
    val content: String,
    val mediaUrl: String? = null,
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + STATUS_TTL_MILLIS,
    val tags: List<String> = emptyList(),
    val isViewed: Boolean = false,
    val canonicalTags: List<Tag> = emptyList()
)

const val STATUS_TTL_MILLIS = 24 * 60 * 60 * 1000L

fun Status.isActiveAt(now: Long): Boolean = expiresAt > now

data class UserNotification(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val type: NotificationType
)

enum class NotificationType {
    FOLLOW,
    LIKE,
    COMMENT,
    CLOSE_FRIENDS
}

data class TopicCard(
    val title: String,
    val count: String,
    val accent: Color
)
