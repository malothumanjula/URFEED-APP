package com.urfeed.app.data.repository

import com.urfeed.app.domain.model.TagAnalysisResult
import com.urfeed.app.domain.model.TagSuggestion
import com.urfeed.app.domain.repository.AiTagService
import com.urfeed.app.domain.repository.TagNormalizationService

class MockAiTagService(
    private val normalizationService: TagNormalizationService = LocalTagNormalizationService()
) : AiTagService {
    override suspend fun generateTags(content: String): TagAnalysisResult {
        val text = content.lowercase().replace(Regex("[^a-z0-9+#.]+"), " ").trim()
        if (text.isBlank()) return TagAnalysisResult(emptyList(), null, null, 0f, emptyList())

        val raw = linkedMapOf<String, Float>()
        fun add(tag: String, confidence: Float) { raw.putIfAbsent(tag, confidence) }
        fun has(vararg terms: String): Boolean = terms.any { term ->
            Regex("(^|[^a-z0-9])${Regex.escape(term.lowercase())}([^a-z0-9]|$)").containsMatchIn(text)
        }

        when {
            has("cricket") -> { add("Sports", 0.97f); add("Cricket", 0.99f) }
            has("placement", "placements", "campus placement") || (has("campus") && has("interview")) -> {
                add("Education", 0.94f); add("Career", 0.92f); add("Placements", 0.98f)
                add("Programming", 0.84f)
                if (has("data structures", "dsa", "algorithm")) add("Data Structures", 0.9f)
                if (has("interview", "interviews")) add("Interviews", 0.89f)
                if (has("resume")) add("Resume", 0.87f)
                if (has("internship", "internships")) add("Internships", 0.85f)
            }
        }

        val aiRelated = has("ai", "a.i.", "artificial intelligence", "agent", "agents", "assistant", "autonomous", "langchain", "langgraph", "llm")
        if (aiRelated) {
            add("Artificial Intelligence", 0.97f)
            if (has("agent", "agents", "assistant", "autonomous", "langchain", "langgraph")) add("AI Agents", 0.94f)
            if (has("agentic", "autonomous", "agent", "agents", "langgraph")) add("Agentic AI", 0.91f)
            if (has("langchain")) add("LangChain", 0.90f)
            if (has("langgraph")) add("LangGraph", 0.91f)
            if (has("llm", "llms", "large language model")) add("Large Language Models", 0.9f)
            if (has("generative", "generative ai")) add("Generative AI", 0.9f)
            if (has("automation", "automate", "workflow")) add("AI Automation", 0.86f)
        }
        if (has("python")) add("Python", 0.91f)
        if (has("java")) add("Java", 0.9f)
        if (has("javascript")) add("JavaScript", 0.9f)
        if (has("machine learning", " ml ")) add("Machine Learning", 0.9f)
        if (has("data science", "data analysis")) add("Data Science", 0.88f)
        if (has("programming", "coding", "code", "developer", "development")) add("Programming", 0.83f)
        if (has("data structures", "dsa")) add("Data Structures", 0.91f)
        if (has("algorithm", "algorithms")) add("Algorithms", 0.89f)
        if (has("college", "campus", "university")) add("College", 0.87f)
        if (has("internship", "internships")) add("Internships", 0.86f)
        if (has("travel", "trip", "destination")) add("Travel", 0.9f)
        if (has("photography", "photograph")) add("Photography", 0.86f)
        if (has("design", "designer")) add("Programming", 0.72f)
        if (has("startup", "startups", "entrepreneur")) { add("Business", 0.86f); add("Startups", 0.92f) }
        if (has("food", "recipe", "cooking")) add("Food", 0.89f)

        val canonical = normalizationService.normalizeAll(raw.keys.toList())
        val suggestions = canonical.mapNotNull { tag -> raw[tag.name]?.let { TagSuggestion(tag, it) } }
        val primary = suggestions.maxByOrNull(TagSuggestion::confidence)?.tag
        val words = text.split(Regex("\\s+")).filter { it.length > 2 }.distinct().take(12)
        val entities = listOf("Python", "LangChain", "LangGraph", "Cricket", "Java")
            .filter { entity -> has(entity.lowercase()) }
        return TagAnalysisResult(
            tags = suggestions,
            category = primary?.category,
            subcategory = primary?.subcategory,
            confidence = suggestions.maxOfOrNull(TagSuggestion::confidence) ?: 0f,
            keywords = words,
            entities = entities
        )
    }
}
