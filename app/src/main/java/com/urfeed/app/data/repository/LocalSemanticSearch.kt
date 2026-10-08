package com.urfeed.app.data.repository

import com.urfeed.app.domain.model.PostVisibility
import com.urfeed.app.domain.model.RankedSearchItem
import com.urfeed.app.domain.model.SearchContentType
import com.urfeed.app.domain.model.SearchResult
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.model.isActiveAt
import com.urfeed.app.domain.repository.CanonicalTagRepository
import com.urfeed.app.domain.repository.SearchRepository
import com.urfeed.app.domain.repository.SearchService
import com.urfeed.app.domain.repository.TagNormalizationService
import kotlin.math.max

class LocalSemanticSearchService(
    private val tags: CanonicalTagRepository = LocalCanonicalTagRepository(),
    private val normalization: TagNormalizationService = LocalTagNormalizationService(tags)
) : SearchService {
    private val stopWords = setOf("a", "an", "the", "in", "on", "for", "to", "of", "about", "things", "people", "learning", "preparation", "preparing", "projects", "project", "with", "and", "or", "how", "what")

    override fun matchedTags(query: String): List<Pair<Tag, Float>> {
        val queryKey = key(query)
        if (queryKey.isBlank()) return emptyList()
        val terms = query.lowercase().split(Regex("[^a-z0-9+#.]+"))
            .filter { it.isNotBlank() && it !in stopWords }
        val direct = linkedMapOf<String, Pair<Tag, Float>>()
        tags.allTags().forEach { tag ->
            val aliases = (tag.aliases + tag.name + tag.slug).distinct()
            val exact = aliases.any { key(it) == queryKey }
            val phrase = aliases.any { alias -> key(alias).length >= 3 && queryKey.contains(key(alias)) }
            val tokenMatch = terms.any { term ->
                aliases.any { alias -> key(alias) == key(term) } || key(tag.name) == key(term)
            }
            val score = when { exact -> 100f; phrase -> 88f; tokenMatch -> 82f; else -> 0f }
            if (score > 0) direct[tag.id] = tag to score
        }

        val expanded = direct.toMutableMap()
        direct.values.filter { it.second >= 82f }.forEach { (seed, seedScore) ->
            tags.relatedTags(seed, 30).forEach { related ->
                val score = minOf(74f, seedScore - 12f)
                if ((expanded[related.id]?.second ?: 0f) < score) expanded[related.id] = related to score
            }
            semanticExpansions(seed).forEach { related ->
                val score = minOf(72f, seedScore - 14f)
                if ((expanded[related.id]?.second ?: 0f) < score) expanded[related.id] = related to score
            }
        }

        val categoryNames = setOf("technology", "education", "career", "business", "sports", "entertainment", "travel", "lifestyle")
        val requestedCategories = terms.filter { it in categoryNames }
            .map { it.replaceFirstChar { character -> character.titlecase() } }.toSet()
        tags.allTags().filter { it.category in requestedCategories }.forEach { categoryTag ->
            if ((expanded[categoryTag.id]?.second ?: 0f) < 52f) expanded[categoryTag.id] = categoryTag to 52f
        }
        return expanded.values.sortedByDescending { it.second }
    }

    override fun score(query: String, tags: List<Tag>, displayTags: List<String>, text: String, createdAt: Long): Pair<Float, Tag?> {
        val matches = matchedTags(query).associateBy { it.first.id }
        val normalizedTags = (tags + normalization.normalizeAll(displayTags)).distinctBy(Tag::id)
        var score = 0f
        var match: Tag? = null
        normalizedTags.forEach { tag ->
            val tagScore = matches[tag.id]?.second ?: 0f
            if (tagScore > score) {
                score = tagScore
                match = tag
            }
        }
        val categoryNames = matchedTags(query).map { it.first.category }.toSet()
        if (score < 50f && normalizedTags.any { it.category in categoryNames }) score = 50f
        val normalizedText = key(text)
        val queryTerms = query.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 && it !in stopWords }
        if (queryTerms.any { normalizedText.contains(key(it)) } && score < 35f) score = 35f
        val ageDays = ((System.currentTimeMillis() - createdAt).coerceAtLeast(0L) / 86_400_000L).toFloat()
        val recency = (5f - ageDays * 0.05f).coerceIn(0f, 5f)
        return if (score > 0f) (score + recency) to match else 0f to null
    }

    private fun semanticExpansions(tag: Tag): List<Tag> {
        val names = when (tag.name) {
            "AI Agents", "Agentic AI", "Artificial Intelligence", "LangChain", "LangGraph" ->
                listOf("Artificial Intelligence", "AI Agents", "Agentic AI", "AI Automation", "LangChain", "LangGraph")
            "Python", "Programming" -> listOf("Python", "Programming", "Data Structures", "Algorithms", "Machine Learning", "Data Science")
            "Placements", "College", "Interviews" -> listOf("College", "Placements", "Interviews", "Career", "Resume", "Internships", "Data Structures")
            else -> emptyList()
        }
        return names.mapNotNull { name -> tags.allTags().firstOrNull { it.name == name } }
    }

    private fun key(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)
}

class LocalSearchRepository(
    private val service: SearchService = LocalSemanticSearchService()
) : SearchRepository {
    override suspend fun searchContent(
        query: String,
        posts: List<SocialPost>,
        statuses: List<Status>,
        currentUserId: String,
        followingIds: Set<String>,
        now: Long
    ): SearchResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return SearchResult(query)
        val visiblePosts = posts.filter { post ->
            post.visibility == PostVisibility.PUBLIC || post.authorId == currentUserId || post.authorId in followingIds
        }
        val visibleStatuses = statuses.filter { status ->
            status.isActiveAt(max(now, System.currentTimeMillis())) &&
                (status.visibility == PostVisibility.PUBLIC || status.userId == currentUserId || status.userId in followingIds)
        }
        val postScores = visiblePosts.mapNotNull { post ->
            val (score, matchedTag) = service.score(cleanQuery, post.canonicalTags, post.tags, post.text, post.createdAt)
            if (score > 0f) RankedSearchItem(post.id, SearchContentType.POST, matchedTag, score) to post else null
        }
        val statusScores = visibleStatuses.mapNotNull { status ->
            val (score, matchedTag) = service.score(cleanQuery, status.canonicalTags, status.tags, status.content, status.createdAt)
            if (score > 0f) RankedSearchItem(status.id, SearchContentType.STATUS, matchedTag, score) to status else null
        }
        val ranked = (postScores.map { it.first } + statusScores.map { it.first }).sortedByDescending(RankedSearchItem::relevanceScore)
        val postsById = postScores.associate { it.second.id to it.second }
        val statusesById = statusScores.associate { it.second.id to it.second }
        val resultPosts = ranked.filter { it.contentType == SearchContentType.POST }.mapNotNull { postsById[it.contentId] }
        val resultStatuses = ranked.filter { it.contentType == SearchContentType.STATUS }.mapNotNull { statusesById[it.contentId] }
        val matchedTags = service.matchedTags(cleanQuery).map { it.first }.distinctBy(Tag::id)
        return SearchResult(
            query = cleanQuery,
            posts = resultPosts,
            statuses = resultStatuses,
            matchedTags = matchedTags,
            categories = matchedTags.map(Tag::category).distinct(),
            rankedItems = ranked
        )
    }
}
