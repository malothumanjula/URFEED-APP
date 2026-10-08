package com.urfeed.app.data.repository

import com.urfeed.app.data.remote.ApiService
import com.urfeed.app.data.remote.PostCreateRequest
import com.urfeed.app.data.remote.StatusCreateRequest
import com.urfeed.app.domain.model.PostVisibility
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteContentRepository(private val api: ApiService) : ContentRepository {
    private val _posts = MutableStateFlow<List<SocialPost>>(emptyList())
    private val _statuses = MutableStateFlow<List<Status>>(emptyList())
    override val posts: StateFlow<List<SocialPost>> = _posts.asStateFlow()
    override val statuses: StateFlow<List<Status>> = _statuses.asStateFlow()

    override suspend fun refresh(following: Boolean) {
        val fetchedPosts = api.feed(following = following).map { it.toDomain() }
        _posts.value = if (following) fetchedPosts else fetchedPosts
        _statuses.value = api.statusFeed(following = following).map { it.toDomain() }
    }

    override suspend fun createPost(post: SocialPost) {
        val created = api.createPost(
            PostCreateRequest(post.text, post.imageUrl, post.visibility.name, post.canonicalTags.map(Tag::name))
        ).toDomain()
        _posts.value = listOf(created) + _posts.value.filterNot { it.id == created.id }
    }

    override suspend fun createStatus(status: Status) {
        val created = api.createStatus(
            StatusCreateRequest(status.content, status.mediaUrl, status.visibility.name, status.canonicalTags.map(Tag::name))
        ).toDomain()
        _statuses.value = listOf(created) + _statuses.value.filterNot { it.id == created.id }
    }

    override suspend fun toggleLike(postId: String) {
        val current = _posts.value.firstOrNull { it.id == postId } ?: return
        if (current.isLiked) api.unlike(postId) else api.like(postId)
        _posts.value = _posts.value.map { post ->
            if (post.id == postId) post.copy(isLiked = !post.isLiked, likes = (post.likes + if (post.isLiked) -1 else 1).coerceAtLeast(0)) else post
        }
    }

    override suspend fun addComment(postId: String, content: String) {
        api.comment(postId, mapOf("content" to content))
        _posts.value = _posts.value.map { post -> if (post.id == postId) post.copy(comments = post.comments + 1) else post }
    }

    override suspend fun sharePost(postId: String) {
        _posts.value = _posts.value.map { post -> if (post.id == postId && !post.isShared) post.copy(isShared = true, shares = post.shares + 1) else post }
    }

    override suspend fun markStatusViewed(statusId: String) {
        _statuses.value = _statuses.value.map { status -> if (status.id == statusId) status.copy(isViewed = true) else status }
    }
}
