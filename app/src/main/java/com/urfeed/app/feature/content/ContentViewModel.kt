package com.urfeed.app.feature.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.urfeed.app.data.remote.NetworkModule
import com.urfeed.app.data.repository.MockAiTagService
import com.urfeed.app.data.repository.LocalTagNormalizationService
import com.urfeed.app.data.repository.RemoteContentRepository
import com.urfeed.app.data.repository.LocalSemanticSearchService
import com.urfeed.app.domain.model.PostVisibility
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.model.TagSuggestion
import com.urfeed.app.domain.model.TagAnalysisResult
import com.urfeed.app.domain.model.isActiveAt
import com.urfeed.app.domain.repository.AiTagService
import com.urfeed.app.domain.repository.ContentRepository
import com.urfeed.app.domain.repository.SearchService
import com.urfeed.app.domain.repository.TagNormalizationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID

data class ContentUiState(
    val posts: List<SocialPost> = emptyList(),
    val statuses: List<Status> = emptyList(),
     val suggestedTags: List<TagSuggestion> = emptyList(),
     val tagAnalysis: TagAnalysisResult? = null,
     val isAnalyzingTags: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentUser: User = User(),
    val creationMessage: String? = null,
    val now: Long = System.currentTimeMillis()
)

class ContentViewModel(
    private val repository: ContentRepository,
    private val tagService: AiTagService = MockAiTagService(),
    private val searchService: SearchService = LocalSemanticSearchService(),
    private val tagNormalizationService: TagNormalizationService = LocalTagNormalizationService()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ContentUiState())
    val uiState: StateFlow<ContentUiState> = _uiState.asStateFlow()
    private var tagRequestId = 0L

    init {
        viewModelScope.launch { repository.posts.collect { _uiState.value = _uiState.value.copy(posts = it) } }
        viewModelScope.launch { repository.statuses.collect { _uiState.value = _uiState.value.copy(statuses = it) } }
        viewModelScope.launch { refresh() }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                _uiState.value = _uiState.value.copy(now = System.currentTimeMillis())
            }
        }
    }

    fun setCurrentUser(user: User) {
        _uiState.value = _uiState.value.copy(currentUser = user)
    }

    fun refresh(following: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { repository.refresh(following) }
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Content is temporarily unavailable. Check your connection and retry.") }
        }
    }

    fun updateTagSuggestions(text: String) {
        val requestId = ++tagRequestId
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(suggestedTags = emptyList(), tagAnalysis = null, isAnalyzingTags = false)
            return
        }
        _uiState.value = _uiState.value.copy(isAnalyzingTags = true)
        viewModelScope.launch {
            val result = tagService.generateTags(text)
            if (requestId == tagRequestId) {
                val normalized = tagNormalizationService.normalizeAll(result.tags.map { it.tag.name }).associateBy(Tag::id)
                val canonicalSuggestions = result.tags.mapNotNull { suggestion ->
                    normalized[suggestion.tag.id]?.let { suggestion.copy(tag = it) }
                }.distinctBy { it.tag.id }
                _uiState.value = _uiState.value.copy(
                    suggestedTags = canonicalSuggestions,
                    tagAnalysis = result.copy(tags = canonicalSuggestions),
                    isAnalyzingTags = false
                )
            }
        }
    }

    fun removeSuggestedTag(tag: String) {
        val updated = _uiState.value.suggestedTags.filterNot { it.tag.id == tag || it.tag.name == tag }
        _uiState.value = _uiState.value.copy(suggestedTags = updated, tagAnalysis = _uiState.value.tagAnalysis?.copy(tags = updated))
    }

    fun addCustomTag(input: String) {
        val tag = tagNormalizationService.normalize(input) ?: return
        if (_uiState.value.suggestedTags.none { it.tag.id == tag.id }) {
            val updated = _uiState.value.suggestedTags + TagSuggestion(tag, 1f)
            _uiState.value = _uiState.value.copy(suggestedTags = updated, tagAnalysis = _uiState.value.tagAnalysis?.copy(tags = updated))
        }
    }

    fun createPost(text: String, imageUrl: String?, visibility: PostVisibility, tags: List<Tag>) {
        val user = _uiState.value.currentUser
        viewModelScope.launch {
            runCatching { repository.createPost(
                SocialPost(
                    id = UUID.randomUUID().toString(), author = user.fullName, handle = user.username,
                    authorId = user.id, profileImage = user.profileImage, time = "now", text = text.trim(),
                    tags = tags.distinctBy(Tag::id).map(::displayTag), canonicalTags = tags.distinctBy(Tag::id),
                    hasImage = imageUrl != null, imageUrl = imageUrl, visibility = visibility
                )
            ) }.onSuccess { _uiState.value = _uiState.value.copy(creationMessage = "Post published", errorMessage = null) }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = "Post could not be published. Please try again.") }
        }
    }

    fun createStatus(text: String, imageUrl: String?, visibility: PostVisibility, tags: List<Tag>) {
        val user = _uiState.value.currentUser
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching { repository.createStatus(
                Status(
                    id = UUID.randomUUID().toString(), userId = user.id, username = user.username,
                    profileImage = user.profileImage, content = text.trim(), mediaUrl = imageUrl,
                    visibility = visibility, createdAt = now,
                    tags = tags.distinctBy(Tag::id).map(::displayTag), canonicalTags = tags.distinctBy(Tag::id)
                )
            ) }.onSuccess { _uiState.value = _uiState.value.copy(creationMessage = "Status shared", errorMessage = null) }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = "Status could not be shared. Please try again.") }
        }
    }

    fun toggleLike(postId: String) = runContentAction { repository.toggleLike(postId) }
    fun addComment(postId: String, content: String) = runContentAction { repository.addComment(postId, content) }
    fun sharePost(postId: String) = runContentAction { repository.sharePost(postId) }
    fun markStatusViewed(statusId: String) = runContentAction { repository.markStatusViewed(statusId) }

    private fun runContentAction(action: suspend () -> Unit) = viewModelScope.launch {
        runCatching { action() }.onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = "That action could not be completed. Check your connection and retry.")
        }
    }
    fun clearCreationMessage() { _uiState.value = _uiState.value.copy(creationMessage = null) }

    fun regenerateTagSuggestions(text: String) = updateTagSuggestions(text)

    private fun displayTag(tag: Tag): String = "#${tag.name.filter(Char::isLetterOrDigit)}"

    fun activeStatuses(now: Long = System.currentTimeMillis()): List<Status> =
        _uiState.value.statuses.filter { it.isActiveAt(now) }

    fun searchPosts(query: String): List<SocialPost> = _uiState.value.posts
        .map { post -> post to searchService.score(query, post.canonicalTags, post.tags, post.text, post.createdAt).first }
        .filter { it.second > 0f }
        .sortedByDescending { it.second }
        .map { it.first }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = RemoteContentRepository(NetworkModule.api(context))
            @Suppress("UNCHECKED_CAST")
            return ContentViewModel(repository) as T
        }
    }
}
