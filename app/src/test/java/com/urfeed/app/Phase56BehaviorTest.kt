package com.urfeed.app

import com.urfeed.app.data.repository.LocalCanonicalTagRepository
import com.urfeed.app.data.repository.LocalSearchRepository
import com.urfeed.app.data.repository.LocalTagNormalizationService
import com.urfeed.app.data.repository.MockAiTagService
import com.urfeed.app.domain.model.PostVisibility
import com.urfeed.app.domain.model.SearchContentType
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.STATUS_TTL_MILLIS
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.model.isActiveAt
import com.urfeed.app.data.repository.MockUserRepository
import com.urfeed.app.feature.explore.SearchViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase56BehaviorTest {
    private val normalizer = LocalTagNormalizationService()

    @Test
    fun aiAgentPythonAndLangChainAnalysisIsCanonicalAndConfident() = runBlocking {
        val analysis = MockAiTagService(normalizer).generateTags("Built an autonomous AI assistant using Python and LangChain.")

        assertEquals(
            listOf("Artificial Intelligence", "AI Agents", "Agentic AI", "LangChain", "Python"),
            analysis.tags.map { it.tag.name }
        )
        assertTrue(analysis.tags.all { it.confidence in 0f..1f })
        assertEquals("Technology", analysis.category)
        assertTrue("Python" in analysis.entities)
        assertTrue("LangChain" in analysis.entities)
    }

    @Test
    fun placementAndCricketExamplesProduceExpectedTopics() = runBlocking {
        val service = MockAiTagService(normalizer)
        val placements = service.generateTags("Preparing for campus placements and practicing data structures.").tags.map { it.tag.name }.toSet()
        val cricket = service.generateTags("India won an exciting cricket match today.").tags.map { it.tag.name }

        assertTrue(placements.containsAll(setOf("Education", "Career", "Placements", "Data Structures", "Programming")))
        assertEquals(listOf("Sports", "Cricket"), cricket)
    }

    @Test
    fun emptyAndUnknownInputsReturnNoInventedTags() = runBlocking {
        val service = MockAiTagService(normalizer)

        assertTrue(service.generateTags("").tags.isEmpty())
        assertTrue(service.generateTags("A quiet afternoon with nothing specific.").tags.isEmpty())
    }

    @Test
    fun aliasesCanonicalizeAndDuplicateAliasesCollapse() {
        assertEquals("Artificial Intelligence", normalizer.normalize("AI")?.name)
        assertEquals("Artificial Intelligence", normalizer.normalize("A.I.")?.name)
        assertEquals("Artificial Intelligence", normalizer.normalize("Artificial-Intelligence")?.name)
        assertEquals("AI Agents", normalizer.normalize("AI Agent")?.name)
        assertEquals("AI Agents", normalizer.normalize("LLM Agents")?.name)
        assertEquals(1, normalizer.normalizeAll(listOf("AI", "A.I.", "Artificial Intelligence")).size)
    }

    @Test
    fun customTagsRemainAvailableAndVisibilityIsNotAContentTag() {
        val custom = normalizer.normalize("My Community Topic")

        assertEquals("My Community Topic", custom?.name)
        assertEquals(emptyList<Tag>(), normalizer.normalizeAll(listOf("PUBLIC", "FOLLOWERS", "CLOSE_FRIENDS")))
    }

    @Test
    fun aiAgentQueryFindsRelatedCanonicalTopicsWithoutExactText() = runBlocking {
        val tags = LocalCanonicalTagRepository()
        val repository = LocalSearchRepository()
        val langChain = requireNotNull(normalizer.normalize("LangChain"))
        val unrelated = requireNotNull(normalizer.normalize("Cricket"))
        val result = repository.searchContent(
            query = "AI agents",
            posts = listOf(
                post("agent-post", "Orchestrated a tool-using autonomous workflow.", listOf(requireNotNull(normalizer.normalize("AI Agents")))),
                post("langchain-post", "Built a chain for local experiments.", listOf(langChain)),
                post("cricket-post", "A close match went to the final over.", listOf(unrelated))
            ),
            statuses = emptyList(), currentUserId = "me", followingIds = emptySet(), now = System.currentTimeMillis()
        )

        assertTrue(result.posts.map(SocialPost::id).containsAll(listOf("agent-post", "langchain-post")))
        assertFalse(result.posts.any { it.id == "cricket-post" })
        assertEquals("agent-post", result.posts.first().id)
        assertTrue(result.matchedTags.any { it.name == "Agentic AI" })
        assertEquals(SearchContentType.POST, result.rankedItems.first().contentType)
        assertTrue(tags.allTags().isNotEmpty())
    }

    @Test
    fun pythonAndPlacementQueriesExpandRelatedTags() = runBlocking {
        val repository = LocalSearchRepository()
        val python = requireNotNull(normalizer.normalize("Python"))
        val dsa = requireNotNull(normalizer.normalize("Data Structures"))
        val placement = requireNotNull(normalizer.normalize("Placements"))
        val interviews = requireNotNull(normalizer.normalize("Interviews"))
        val pythonResult = repository.searchContent(
            "programming in Python",
            listOf(post("python", "Built a small command-line tool.", listOf(python)), post("dsa", "Practicing problem solving.", listOf(dsa))),
            emptyList(), "me", emptySet()
        )
        val placementResult = repository.searchContent(
            "college placement preparation",
            listOf(post("placement", "Preparing for upcoming interviews.", listOf(placement, interviews))),
            emptyList(), "me", emptySet()
        )

        assertTrue(pythonResult.posts.map(SocialPost::id).containsAll(listOf("python", "dsa")))
        assertTrue(pythonResult.matchedTags.any { it.name == "Machine Learning" })
        assertTrue(placementResult.posts.any { it.id == "placement" })
        assertTrue(placementResult.matchedTags.any { it.name == "Resume" })
    }

    @Test
    fun cricketSearchFindsCricketTaggedContentAndExactTagRanksFirst() = runBlocking {
        val repository = LocalSearchRepository()
        val cricket = requireNotNull(normalizer.normalize("Cricket"))
        val sports = requireNotNull(normalizer.normalize("Sports"))
        val result = repository.searchContent(
            "cricket",
            listOf(post("exact", "A tense final over.", listOf(cricket)), post("related", "A big day for local teams.", listOf(sports))),
            emptyList(), "me", emptySet()
        )

        assertEquals("exact", result.posts.first().id)
        assertEquals("exact", result.rankedItems.first().contentId)
        assertTrue(result.posts.any { it.id == "related" })
    }

    @Test
    fun naturalLanguagePeopleLearningJavaMatchesProfileInterests() {
        val search = SearchViewModel()
        val users = listOf(
            User(id = "me", fullName = "Current User", username = "me"),
            User(id = "learner", fullName = "Mina Rao", username = "mina", interests = listOf("Java")),
            User(id = "other", fullName = "Ravi Das", username = "ravi", interests = listOf("Cricket"))
        )

        assertEquals(listOf("learner"), search.searchUsers("people learning Java", users, "me").map(User::id))
    }

    @Test
    fun searchExcludesExpiredAndInvisibleStatusesAndIncludesVisibleStatuses() = runBlocking {
        val repository = LocalSearchRepository()
        val aiAgents = requireNotNull(normalizer.normalize("AI Agents"))
        val now = System.currentTimeMillis()
        val statuses = listOf(
            status("expired", "friend", PostVisibility.PUBLIC, aiAgents, now - 2 * STATUS_TTL_MILLIS, now - 1),
            status("private-stranger", "stranger", PostVisibility.FOLLOWERS, aiAgents, now, now + STATUS_TTL_MILLIS),
            status("friend-status", "friend", PostVisibility.FOLLOWERS, aiAgents, now, now + STATUS_TTL_MILLIS),
            status("public-status", "stranger", PostVisibility.PUBLIC, aiAgents, now, now + STATUS_TTL_MILLIS)
        )
        val result = repository.searchContent("AI agents", emptyList(), statuses, "me", setOf("friend"), now)

        assertEquals(setOf("friend-status", "public-status"), result.statuses.map(Status::id).toSet())
        assertFalse(result.statuses.any { it.id == "expired" || it.id == "private-stranger" })
    }

    @Test
    fun followerOnlyPostsRespectCurrentUserRelationship() = runBlocking {
        val repository = LocalSearchRepository()
        val topic = requireNotNull(normalizer.normalize("AI Agents"))
        val restricted = post("restricted", "Private follower update.", listOf(topic)).copy(visibility = PostVisibility.FOLLOWERS, authorId = "friend")
        val own = restricted.copy(id = "own", authorId = "me")

        val strangerResults = repository.searchContent("AI agents", listOf(restricted), emptyList(), "me", emptySet())
        val followerResults = repository.searchContent("AI agents", listOf(restricted, own), emptyList(), "me", setOf("friend"))

        assertTrue(strangerResults.posts.isEmpty())
        assertEquals(setOf("restricted", "own"), followerResults.posts.map(SocialPost::id).toSet())
    }

    @Test
    fun postsAndStatusesShareCanonicalTagNormalizationAndPrivacy() = runBlocking {
        val repository = LocalSearchRepository()
        val post = post("public-post", "Tools for autonomous work.", listOf(requireNotNull(normalizer.normalize("AI Agent"))))
        val status = status("status", "me", PostVisibility.PUBLIC, requireNotNull(normalizer.normalize("AI Agent")), System.currentTimeMillis(), System.currentTimeMillis() + STATUS_TTL_MILLIS)
        val result = repository.searchContent("AI agents", listOf(post), listOf(status), "me", emptySet())

        assertEquals(listOf("#AIAgents"), post.tags)
        assertEquals("AI Agents", result.posts.single().canonicalTags.single().name)
        assertEquals("AI Agents", result.statuses.single().canonicalTags.single().name)
    }

    @Test
    fun followStateRemainsIdempotentAndUpdatesCounts() = runBlocking {
        val repository = MockUserRepository()
        repository.updateProfile(User(id = "me", fullName = "Me", username = "me"))
        val initialCount = repository.getUsers().first { it.id == "maya" }.followersCount

        repository.setFollowing("maya", true)
        repository.setFollowing("maya", true)
        assertEquals(initialCount + 1, repository.getUsers().first { it.id == "maya" }.followersCount)
        repository.setFollowing("maya", false)
        assertEquals(initialCount, repository.getUsers().first { it.id == "maya" }.followersCount)
    }

    private fun post(id: String, text: String, tags: List<Tag>) = SocialPost(
        id = id, author = "Author", handle = "author", authorId = "author", time = "now", text = text,
        tags = tags.map { "#${it.name.filter(Char::isLetterOrDigit)}" }, canonicalTags = tags, createdAt = System.currentTimeMillis()
    )

    private fun status(id: String, userId: String, visibility: PostVisibility, tag: Tag, createdAt: Long, expiresAt: Long) = Status(
        id = id, userId = userId, username = userId, content = "A local update", visibility = visibility,
        tags = listOf("#${tag.name.filter(Char::isLetterOrDigit)}"), canonicalTags = listOf(tag),
        createdAt = createdAt, expiresAt = expiresAt
    )
}
