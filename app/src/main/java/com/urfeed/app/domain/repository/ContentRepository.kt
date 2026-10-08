package com.urfeed.app.domain.repository

import com.urfeed.app.domain.model.PostVisibility
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.Status
import kotlinx.coroutines.flow.StateFlow

interface ContentRepository {
    val posts: StateFlow<List<SocialPost>>
    val statuses: StateFlow<List<Status>>
    suspend fun refresh(following: Boolean = false)
    suspend fun createPost(post: SocialPost)
    suspend fun createStatus(status: Status)
    suspend fun toggleLike(postId: String)
    suspend fun addComment(postId: String, content: String)
    suspend fun sharePost(postId: String)
    suspend fun markStatusViewed(statusId: String)
}

