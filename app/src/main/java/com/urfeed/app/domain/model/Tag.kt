package com.urfeed.app.domain.model

data class Tag(
    val id: String,
    val name: String,
    val slug: String,
    val category: String,
    val subcategory: String? = null,
    val description: String = "",
    val aliases: List<String> = emptyList()
)

data class TagSuggestion(
    val tag: Tag,
    val confidence: Float
)

data class TagAnalysisResult(
    val tags: List<TagSuggestion>,
    val category: String?,
    val subcategory: String?,
    val confidence: Float,
    val keywords: List<String>,
    val entities: List<String> = emptyList()
)
