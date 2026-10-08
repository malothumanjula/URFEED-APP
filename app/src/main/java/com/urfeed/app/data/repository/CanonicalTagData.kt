package com.urfeed.app.data.repository

import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.repository.CanonicalTagRepository
import com.urfeed.app.domain.repository.TagNormalizationService

class LocalCanonicalTagRepository : CanonicalTagRepository {
    private fun tag(
        name: String,
        category: String,
        subcategory: String? = null,
        description: String = "",
        aliases: List<String> = emptyList()
    ) = Tag(
        id = slug(name), name = name, slug = slug(name), category = category,
        subcategory = subcategory, description = description, aliases = (aliases + name).distinct()
    )

    private val catalog = listOf(
        tag("Technology", "Technology"),
        tag("Artificial Intelligence", "Technology", "AI", aliases = listOf("AI", "A.I.", "Artificial-Intelligence")),
        tag("Machine Learning", "Technology", "AI", aliases = listOf("ML")),
        tag("Data Science", "Technology", "AI", aliases = listOf("DataScience")),
        tag("Programming", "Technology", "Programming", aliases = listOf("Coding", "Software Development")),
        tag("Web Development", "Technology", "Programming", aliases = listOf("Web Dev")),
        tag("Mobile Development", "Technology", "Programming", aliases = listOf("Mobile Dev")),
        tag("Cybersecurity", "Technology", aliases = listOf("Cyber Security", "InfoSec")),
        tag("Cloud Computing", "Technology", aliases = listOf("Cloud")),
        tag("Blockchain", "Technology"), tag("Robotics", "Technology"),
        tag("Internet of Things", "Technology", aliases = listOf("IoT")),
        tag("DevOps", "Technology"), tag("Databases", "Technology", aliases = listOf("Database")),
        tag("Open Source", "Technology"),
        tag("Generative AI", "Technology", "Artificial Intelligence", aliases = listOf("GenAI")),
        tag("Agentic AI", "Technology", "Artificial Intelligence", aliases = listOf("AgenticAI")),
        tag("AI Agents", "Technology", "Artificial Intelligence", aliases = listOf("AI Agent", "AI-agent", "AI-agents", "Artificial Intelligence Agents", "LLM Agents", "AIAgents")),
        tag("Large Language Models", "Technology", "Artificial Intelligence", aliases = listOf("LLM", "LLMs", "Large Language Model")),
        tag("Natural Language Processing", "Technology", "Artificial Intelligence", aliases = listOf("NLP")),
        tag("Computer Vision", "Technology", "Artificial Intelligence"),
        tag("AI Tools", "Technology", "Artificial Intelligence"),
        tag("AI Automation", "Technology", "Artificial Intelligence", aliases = listOf("AI Workflow Automation")),
        tag("Python", "Technology", "Programming", aliases = listOf("Python Programming")),
        tag("Java", "Technology", "Programming"), tag("C", "Technology", "Programming"),
        tag("C++", "Technology", "Programming", aliases = listOf("Cpp")),
        tag("JavaScript", "Technology", "Programming", aliases = listOf("JS")),
        tag("TypeScript", "Technology", "Programming", aliases = listOf("TS")),
        tag("React", "Technology", "Programming", aliases = listOf("React.js")),
        tag("Node.js", "Technology", "Programming", aliases = listOf("NodeJS", "Node")),
        tag("Spring Boot", "Technology", "Programming"), tag("Android", "Technology", "Programming"),
        tag("Data Structures", "Technology", "Programming", aliases = listOf("DS", "Data Structure")),
        tag("Algorithms", "Technology", "Programming", aliases = listOf("Algorithm")),
        tag("Competitive Programming", "Technology", "Programming", aliases = listOf("CP")),
        tag("LangChain", "Technology", "Artificial Intelligence", aliases = listOf("Lang Chain")),
        tag("LangGraph", "Technology", "Artificial Intelligence", aliases = listOf("Lang Graph")),
        tag("Education", "Education"), tag("College", "Education", aliases = listOf("University")),
        tag("School", "Education"), tag("Exams", "Education", aliases = listOf("Exam")),
        tag("Competitive Exams", "Education"), tag("Placements", "Education", "Career", aliases = listOf("Campus Placements", "Placement")),
        tag("Internships", "Education", "Career", aliases = listOf("Internship")),
        tag("Scholarships", "Education"), tag("Courses", "Education", aliases = listOf("Course")),
        tag("Certifications", "Education", aliases = listOf("Certificates")),
        tag("Research", "Education"), tag("Study Tips", "Education"),
        tag("Jobs", "Career", aliases = listOf("Job")), tag("Resume", "Career", aliases = listOf("CV")),
        tag("Interviews", "Career", aliases = listOf("Interview")), tag("Career Advice", "Career"),
        tag("Freelancing", "Career", aliases = listOf("Freelance")), tag("Remote Work", "Career"),
        tag("Networking", "Career"), tag("Professional Growth", "Career"),
        tag("Startups", "Business", aliases = listOf("Startup")),
        tag("Entrepreneurship", "Business", aliases = listOf("Entrepreneur")),
        tag("Marketing", "Business"), tag("Sales", "Business"), tag("E-Commerce", "Business", aliases = listOf("Ecommerce")),
        tag("Leadership", "Business"), tag("Innovation", "Business"), tag("Small Business", "Business"),
        tag("Sports", "Sports"), tag("Cricket", "Sports"), tag("Football", "Sports"),
        tag("Basketball", "Sports"), tag("Tennis", "Sports"), tag("Badminton", "Sports"),
        tag("Athletics", "Sports"), tag("Esports", "Sports", aliases = listOf("E-sports")),
        tag("Entertainment", "Entertainment"), tag("Movies", "Entertainment", aliases = listOf("Movie", "Film")),
        tag("Web Series", "Entertainment"), tag("Anime", "Entertainment"), tag("Gaming", "Entertainment", aliases = listOf("Games")),
        tag("Music", "Entertainment"), tag("Memes", "Entertainment"), tag("Comedy", "Entertainment"),
        tag("Books", "Entertainment", aliases = listOf("Book")), tag("Podcasts", "Entertainment", aliases = listOf("Podcast")),
        tag("Travel", "Travel"), tag("Tourism", "Travel"), tag("Destinations", "Travel"),
        tag("Adventure", "Travel"), tag("Solo Travel", "Travel"), tag("Budget Travel", "Travel"),
        tag("Travel Photography", "Travel", "Photography"),
        tag("Lifestyle", "Lifestyle"), tag("Food", "Lifestyle", aliases = listOf("Cooking", "Cuisine")),
        tag("Fitness", "Lifestyle"), tag("Fashion", "Lifestyle"), tag("Photography", "Lifestyle"),
        tag("Hobbies", "Lifestyle", aliases = listOf("Hobby")), tag("Pets", "Lifestyle"), tag("Art", "Lifestyle")
    ).distinctBy(Tag::slug)

    private val bySlug = catalog.associateBy(Tag::slug)

    override fun allTags(): List<Tag> = catalog

    override fun findBySlug(slug: String): Tag? = bySlug[slug(slug)]

    override fun relatedTags(tag: Tag, limit: Int): List<Tag> = catalog.asSequence()
        .filter { it.id != tag.id }
        .map { candidate -> candidate to relatedness(tag, candidate) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(limit.coerceAtLeast(0))
        .map { it.first }
        .toList()

    private fun relatedness(first: Tag, second: Tag): Int = when {
        first.category == second.category && first.subcategory != null && first.subcategory == second.name -> 4
        first.subcategory == second.name || second.subcategory == first.name -> 3
        first.subcategory != null && first.subcategory == second.subcategory -> 2
        first.category == second.category -> 1
        else -> 0
    }
}

class LocalTagNormalizationService(
    private val repository: CanonicalTagRepository = LocalCanonicalTagRepository()
) : TagNormalizationService {
    private val aliasIndex by lazy {
        repository.allTags().flatMap { tag -> (tag.aliases + tag.name + tag.slug).map(::key).map { it to tag } }
            .toMap()
    }

    override fun normalize(input: String): Tag? {
        val cleaned = input.trim().removePrefix("#").trim()
        if (cleaned.isBlank()) return null
        if (key(cleaned) in setOf("public", "followers", "closefriends")) return null
        aliasIndex[key(cleaned)]?.let { return it }
        val name = cleaned.split(Regex("\\s+")).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        val category = if (cleaned.contains(" ")) "Community" else "Community"
        return Tag(
            id = slug(name), name = name, slug = slug(name), category = category,
            description = "User-added topic", aliases = listOf(cleaned, name).distinct()
        )
    }

    override fun normalizeAll(inputs: List<String>): List<Tag> = inputs.mapNotNull(::normalize).distinctBy(Tag::id)

    private fun key(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)
}

private fun slug(value: String): String = value.lowercase()
    .replace("+", " plus ")
    .replace(".", " ")
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
