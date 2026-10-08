package com.urfeed.app.domain.model

enum class SearchContentType {
    POST,
    STATUS
}

data class RankedSearchItem(
    val contentId: String,
    val contentType: SearchContentType,
    val matchingTag: Tag? = null,
    val relevanceScore: Float
)

data class SearchResult(
    val query: String,
    val posts: List<SocialPost> = emptyList(),
    val statuses: List<Status> = emptyList(),
    val matchedTags: List<Tag> = emptyList(),
    val categories: List<String> = emptyList(),
    val rankedItems: List<RankedSearchItem> = emptyList()
)
