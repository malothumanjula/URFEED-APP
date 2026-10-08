package com.urfeed.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.urfeed.app.data.repository.LocalCanonicalTagRepository
import com.urfeed.app.data.repository.LocalSearchRepository
import com.urfeed.app.data.repository.LocalSemanticSearchService
import com.urfeed.app.data.repository.LocalTagNormalizationService
import com.urfeed.app.data.repository.RemoteSearchRepository
import com.urfeed.app.data.remote.NetworkModule
import com.urfeed.app.domain.model.SearchResult
import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.repository.CanonicalTagRepository
import com.urfeed.app.domain.repository.SearchRepository
import com.urfeed.app.domain.repository.SearchService
import com.urfeed.app.domain.repository.TagNormalizationService
import com.urfeed.app.feature.content.ContentUiState
import com.urfeed.app.feature.profile.ProfileUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

 data class SearchUiState(
    val query: String = "",
    val result: SearchResult? = null,
    val isLoading: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val selectedType: String = "All",
    val selectedCategory: String? = null,
    val selectedTag: Tag? = null,
    val relatedTags: List<Tag> = emptyList(),
    val userResults: List<User> = emptyList(),
    val userSearchQuery: String = "",
    val isSearchingUsers: Boolean = false,
    val errorMessage: String? = null
)

class SearchViewModel(
    private val repository: SearchRepository = LocalSearchRepository(),
    private val tags: CanonicalTagRepository = LocalCanonicalTagRepository(),
    private val normalizer: TagNormalizationService = LocalTagNormalizationService(tags),
    private val semanticSearch: SearchService = LocalSemanticSearchService(tags, normalizer),
    private val remoteUsers: (suspend (String) -> List<User>)? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var searchRequestId = 0L
    private var userSearchRequestId = 0L

    fun searchUsersRemote(query: String) {
        val requestId = ++userSearchRequestId
        if (query.isBlank() || remoteUsers == null) {
            _uiState.value = _uiState.value.copy(userResults = emptyList(), userSearchQuery = query, isSearchingUsers = false)
            return
        }
        _uiState.value = _uiState.value.copy(userSearchQuery = query, isSearchingUsers = true)
        viewModelScope.launch {
            runCatching { remoteUsers.invoke(query) }.onSuccess { users ->
                if (requestId == userSearchRequestId) _uiState.value = _uiState.value.copy(userResults = users, isSearchingUsers = false)
            }.onFailure {
                if (requestId == userSearchRequestId) _uiState.value = _uiState.value.copy(isSearchingUsers = false, userResults = emptyList())
            }
        }
    }

    fun search(query: String, content: ContentUiState, profile: ProfileUiState) {
        val requestId = ++searchRequestId
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(query = "", result = null, selectedTag = null)
            return
        }
        _uiState.value = _uiState.value.copy(query = cleanQuery, result = null, isLoading = true, selectedTag = null, errorMessage = null)
        viewModelScope.launch {
            val result = repository.searchContent(
                query = cleanQuery,
                posts = content.posts,
                statuses = content.statuses,
                currentUserId = profile.currentUser.id,
                followingIds = profile.followingIds,
                now = content.now
            )
            if (requestId == searchRequestId) {
                val recent = (listOf(cleanQuery) + _uiState.value.recentSearches.filterNot { it.equals(cleanQuery, true) }).take(8)
                _uiState.value = _uiState.value.copy(query = cleanQuery, result = result, isLoading = false, recentSearches = recent)
            }
        }
    }

    fun searchTag(tagValue: String, content: ContentUiState, profile: ProfileUiState) {
        val tag = normalizer.normalize(tagValue) ?: return
        val related = tags.relatedTags(tag, 8)
        _uiState.value = _uiState.value.copy(selectedTag = tag, relatedTags = related)
        search(tag.name, content, profile)
        _uiState.value = _uiState.value.copy(selectedTag = tag, relatedTags = related)
    }

    fun setTypeFilter(type: String) { _uiState.value = _uiState.value.copy(selectedType = type) }
    fun setCategoryFilter(category: String?) { _uiState.value = _uiState.value.copy(selectedCategory = category) }

    fun searchUsers(query: String, users: List<User>, currentUserId: String): List<User> {
        val terms = query.trim().removePrefix("@").lowercase()
            .split(Regex("[^a-z0-9+#.]+"))
            .filter { it.length > 1 && it !in setOf("people", "learning", "learners", "who", "about", "interested", "in", "the") }
        if (terms.isEmpty()) return emptyList()
        val semanticTagIds = semanticSearch.matchedTags(query).map { it.first.id }.toSet()
        return users.filter { user ->
            user.id != currentUserId && (
                terms.any { term -> user.username.contains(term, true) || user.fullName.contains(term, true) } ||
                    user.interests.any { interest -> normalizer.normalize(interest)?.id in semanticTagIds }
                )
        }
    }

    fun availableCategories(): List<String> = tags.allTags().map(Tag::category).distinct().sorted()

    fun suggestedTopics(): List<Tag> = listOf("AI Agents", "Python", "Placements", "Cricket", "Travel Photography")
        .mapNotNull { name -> tags.allTags().firstOrNull { it.name == name } }

    fun trendingTopics(): List<Tag> = listOf("Artificial Intelligence", "Python", "Cricket", "Startups", "Photography")
        .mapNotNull { name -> tags.allTags().firstOrNull { it.name == name } }

    fun visiblePosts(result: SearchResult): List<com.urfeed.app.domain.model.SocialPost> {
        val state = _uiState.value
        return result.posts.filter { post ->
            (state.selectedType == "All" || state.selectedType == "Posts") &&
                (state.selectedCategory == null || (post.canonicalTags + post.tags.mapNotNull(normalizer::normalize))
                    .any { it.category == state.selectedCategory })
        }
    }

    fun visibleStatuses(result: SearchResult): List<com.urfeed.app.domain.model.Status> {
        val state = _uiState.value
        return result.statuses.filter { status ->
            (state.selectedType == "All" || state.selectedType == "Statuses") &&
                (state.selectedCategory == null || (status.canonicalTags + status.tags.mapNotNull(normalizer::normalize))
                    .any { it.category == state.selectedCategory })
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val api = NetworkModule.api(context)
            val remote = RemoteSearchRepository(api)
            val repository = com.urfeed.app.data.repository.HybridSearchRepository(remote)
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(repository, remoteUsers = { query -> remote.searchUsers(query) }) as T
        }
    }
}
