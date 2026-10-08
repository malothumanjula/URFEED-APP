package com.urfeed.app.data.repository

import com.urfeed.app.data.remote.ApiService
import com.urfeed.app.domain.model.SearchResult
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.repository.SearchRepository

class RemoteSearchRepository(private val api: ApiService) : SearchRepository {
    override suspend fun searchContent(
        query: String,
        posts: List<SocialPost>,
        statuses: List<Status>,
        currentUserId: String,
        followingIds: Set<String>,
        now: Long
    ): SearchResult {
        val result = api.search(query)
        return SearchResult(
            query = result.query,
            posts = result.posts.map { it.toDomain() },
            statuses = result.statuses.map { it.toDomain() },
            matchedTags = result.matchedTags.map { it.toDomain() },
            categories = result.categories
        )
    }

    suspend fun search(query: String, contentType: String = "all", tag: String? = null, page: Int = 1): SearchResult {
        val result = api.search(query, contentType, tag, page)
        return SearchResult(result.query, result.posts.map { it.toDomain() }, result.statuses.map { it.toDomain() }, result.matchedTags.map { it.toDomain() }, result.categories)
    }

    suspend fun searchUsers(query: String): List<User> = api.searchUsers(query).map { it.toDomain() }
}

class HybridSearchRepository(
    private val remote: SearchRepository,
    private val local: SearchRepository = LocalSearchRepository()
) : SearchRepository {
    override suspend fun searchContent(
        query: String,
        posts: List<SocialPost>,
        statuses: List<Status>,
        currentUserId: String,
        followingIds: Set<String>,
        now: Long
    ): SearchResult = runCatching {
        remote.searchContent(query, posts, statuses, currentUserId, followingIds, now)
    }.getOrElse {
        local.searchContent(query, posts, statuses, currentUserId, followingIds, now)
    }
}
