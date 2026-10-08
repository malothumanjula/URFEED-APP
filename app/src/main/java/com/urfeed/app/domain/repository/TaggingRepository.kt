package com.urfeed.app.domain.repository

import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.model.TagAnalysisResult
import com.urfeed.app.domain.model.SearchResult
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.Status

interface AiTagService {
    suspend fun generateTags(content: String): TagAnalysisResult
}

interface TagNormalizationService {
    fun normalize(input: String): Tag?
    fun normalizeAll(inputs: List<String>): List<Tag>
}

interface CanonicalTagRepository {
    fun allTags(): List<Tag>
    fun findBySlug(slug: String): Tag?
    fun relatedTags(tag: Tag, limit: Int = 8): List<Tag>
}

interface SearchService {
    fun matchedTags(query: String): List<Pair<Tag, Float>>
    fun score(query: String, tags: List<Tag>, displayTags: List<String>, text: String, createdAt: Long): Pair<Float, Tag?>
}

interface SearchRepository {
    suspend fun searchContent(
        query: String,
        posts: List<SocialPost>,
        statuses: List<Status>,
        currentUserId: String,
        followingIds: Set<String>,
        now: Long = System.currentTimeMillis()
    ): SearchResult
}
