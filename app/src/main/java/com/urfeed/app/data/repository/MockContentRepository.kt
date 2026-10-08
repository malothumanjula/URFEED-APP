package com.urfeed.app.data.repository

import com.urfeed.app.domain.model.PostVisibility
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MockContentRepository : ContentRepository {
    private val mutex = Mutex()
    private val _posts = MutableStateFlow(
        listOf(
            SocialPost(
                id = "post-aisha", author = "Aisha Khan", handle = "aisha", authorId = "aisha", time = "12m",
                text = "Built an AI agent using Python and LangChain. The workflow feels incredibly smooth for rapid prototyping.",
                tags = listOf("#ArtificialIntelligence", "#AIAgents", "#AgenticAI", "#Python", "#LangChain"),
                likes = 183, comments = 31, shares = 12, hasImage = true
            ),
            SocialPost(
                id = "post-rohan", author = "Rohan Mehta", handle = "rohan", authorId = "rohan", time = "1h",
                text = "Designing better onboarding flows for social apps; small interface details create trust fast.",
                tags = listOf("#UXDesign", "#ProductDesign", "#Startups"), likes = 96, comments = 18, shares = 9
            )
        )
    )
    private val _statuses = MutableStateFlow(
        listOf(
            Status(id = "status-aisha", userId = "aisha", username = "aisha", content = "Shipping a new experiment today", tags = listOf("#AI")),
            Status(id = "status-rohan", userId = "rohan", username = "rohan", content = "Sketching a new idea", isViewed = true),
            Status(id = "status-expired", userId = "maya", username = "maya", content = "Expired status", createdAt = 1L, expiresAt = 2L)
        )
    )

    override val posts: StateFlow<List<SocialPost>> = _posts.asStateFlow()
    override val statuses: StateFlow<List<Status>> = _statuses.asStateFlow()

    override suspend fun refresh(following: Boolean) = Unit

    override suspend fun createPost(post: SocialPost) = mutex.withLock {
        _posts.value = listOf(post) + _posts.value
    }

    override suspend fun createStatus(status: Status) = mutex.withLock {
        _statuses.value = listOf(status) + _statuses.value
    }

    override suspend fun toggleLike(postId: String) = mutex.withLock {
        _posts.value = _posts.value.map { post ->
            if (post.id == postId) post.copy(isLiked = !post.isLiked, likes = (post.likes + if (post.isLiked) -1 else 1).coerceAtLeast(0)) else post
        }
    }

    override suspend fun addComment(postId: String, content: String) = mutex.withLock {
        _posts.value = _posts.value.map { post -> if (post.id == postId) post.copy(comments = post.comments + 1) else post }
    }

    override suspend fun sharePost(postId: String) = mutex.withLock {
        _posts.value = _posts.value.map { post ->
            if (post.id == postId) post.copy(isShared = true, shares = post.shares + if (post.isShared) 0 else 1) else post
        }
    }

    override suspend fun markStatusViewed(statusId: String) = mutex.withLock {
        _statuses.value = _statuses.value.map { status -> if (status.id == statusId) status.copy(isViewed = true) else status }
    }
}
