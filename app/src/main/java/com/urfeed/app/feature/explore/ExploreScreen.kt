package com.urfeed.app.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.urfeed.app.domain.model.SearchResult
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.model.Tag
import com.urfeed.app.domain.model.User
import com.urfeed.app.feature.content.ContentViewModel
import com.urfeed.app.feature.profile.ProfileViewModel
import com.urfeed.app.ui.components.SocialPostCard
import com.urfeed.app.ui.components.StatusBubble
import com.urfeed.app.ui.components.TagChip
import com.urfeed.app.ui.components.UserProfileCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    contentViewModel: ContentViewModel,
    searchViewModel: SearchViewModel
) {
    val profile by profileViewModel.uiState.collectAsState()
    val content by contentViewModel.uiState.collectAsState()
    val search by searchViewModel.uiState.collectAsState()
    var query by remember { mutableStateOf(search.query) }
    var mode by remember { mutableStateOf("Content") }
    LaunchedEffect(query, mode) {
        if (mode == "Users") searchViewModel.searchUsersRemote(query)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Explore", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What are you interested in?") },
            placeholder = { Text("AI agents, Python projects, college placements...") },
            singleLine = true
        )
        Button(
            onClick = {
                if (mode == "Content") searchViewModel.search(query, content, profile)
            },
            enabled = query.isNotBlank() && mode == "Content",
            modifier = Modifier.fillMaxWidth()
        ) { Text("Search") }
        TabRow(selectedTabIndex = if (mode == "Content") 0 else 1) {
            listOf("Content", "Users").forEach { tab ->
                Tab(selected = mode == tab, onClick = { mode = tab }, text = { Text(tab) })
            }
        }

        if (mode == "Users") {
            if (query.isNotBlank()) {
                val users = if (search.userSearchQuery == query && (search.userResults.isNotEmpty() || search.isSearchingUsers)) search.userResults
                    else searchViewModel.searchUsers(query, profile.users, profile.currentUser.id)
                Text("People", style = MaterialTheme.typography.titleLarge)
                if (search.isSearchingUsers) Text("Searching people...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                users.forEach { user ->
                    UserProfileCard(
                        user = user,
                        following = user.id in profile.followingIds,
                        onClick = { navController.navigate("user/${user.id}") },
                        onFollowClick = { profileViewModel.toggleFollow(user.id) }
                    )
                }
                if (users.isEmpty()) Text("No matching people", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Search names or usernames", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (search.isLoading) {
            Text("Searching topics and content...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (search.result == null) {
            Text("Recent searches", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                search.recentSearches.forEach { term ->
                    FilterChip(selected = false, onClick = { query = term; searchViewModel.search(term, content, profile) }, label = { Text(term) })
                }
            }
            Text("Suggested topics", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                searchViewModel.suggestedTopics().forEach { tag ->
                    TagChip(tagLabel(tag), onClick = { navController.navigate("tag/${tag.slug}") })
                }
            }
            Text("Trending topics", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                searchViewModel.trendingTopics().forEach { tag ->
                    TagChip(tagLabel(tag), onClick = { navController.navigate("tag/${tag.slug}") })
                }
            }
            Text("Categories", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                searchViewModel.availableCategories().forEach { category ->
                    FilterChip(selected = false, onClick = { query = category; searchViewModel.search(category, content, profile) }, label = { Text(category) })
                }
            }
        } else {
            val result = requireNotNull(search.result)
            SearchFilters(searchViewModel, search.selectedType, search.selectedCategory, searchViewModel.availableCategories())
            Text("Search results for \"${result.query}\"", style = MaterialTheme.typography.titleLarge)
            Text("Matched topics", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.matchedTags.take(12).forEach { tag ->
                    TagChip(tagLabel(tag), onClick = { navController.navigate("tag/${tag.slug}") })
                }
            }
            SearchResults(result, searchViewModel, navController, contentViewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagResultsScreen(
    tagSlug: String,
    navController: NavController,
    profileViewModel: ProfileViewModel,
    contentViewModel: ContentViewModel,
    searchViewModel: SearchViewModel
) {
    val profile by profileViewModel.uiState.collectAsState()
    val content by contentViewModel.uiState.collectAsState()
    val search by searchViewModel.uiState.collectAsState()
    LaunchedEffect(tagSlug, content.posts, content.statuses, content.now, profile.followingIds) {
        searchViewModel.searchTag(tagSlug, content, profile)
    }
    val selectedTag = search.selectedTag
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(selectedTag?.name ?: tagSlug, style = MaterialTheme.typography.headlineMedium)
        Text("Related topics", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            search.relatedTags.forEach { tag ->
                TagChip(tagLabel(tag), onClick = { navController.navigate("tag/${tag.slug}") })
            }
        }
        search.result?.let { result ->
            SearchFilters(searchViewModel, search.selectedType, search.selectedCategory, searchViewModel.availableCategories())
            SearchResults(result, searchViewModel, navController, contentViewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFilters(viewModel: SearchViewModel, selectedType: String, selectedCategory: String?, categories: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("All", "Posts", "Statuses").forEach { type ->
            FilterChip(selected = selectedType == type, onClick = { viewModel.setTypeFilter(type) }, label = { Text(type) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selectedCategory == null, onClick = { viewModel.setCategoryFilter(null) }, label = { Text("All categories") })
        categories.forEach { category ->
            FilterChip(selected = selectedCategory == category, onClick = { viewModel.setCategoryFilter(category) }, label = { Text(category) })
        }
    }
}

@Composable
private fun SearchResults(result: SearchResult, searchViewModel: SearchViewModel, navController: NavController, contentViewModel: ContentViewModel) {
    val posts = searchViewModel.visiblePosts(result)
    val statuses = searchViewModel.visibleStatuses(result)
    if (posts.isEmpty() && statuses.isEmpty()) {
        Text("No matching content found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (posts.isNotEmpty()) {
        Text("Posts", style = MaterialTheme.typography.titleLarge)
        posts.forEach { post ->
            SocialPostCard(
                post = post,
                modifier = Modifier.padding(bottom = 10.dp),
                onProfileClick = { if (post.authorId.isNotBlank()) navController.navigate("user/${post.authorId}") },
                onLike = { contentViewModel.toggleLike(post.id) },
                        onComment = { text -> contentViewModel.addComment(post.id, text) },
                onShare = { contentViewModel.sharePost(post.id) },
                onTagClick = { tag -> navController.navigate("tag/${tag.removePrefix("#")}") }
            )
        }
    }
    if (statuses.isNotEmpty()) {
        Text("Statuses", style = MaterialTheme.typography.titleLarge)
        statuses.forEach { status ->
            StatusResult(status, navController)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusResult(status: Status, navController: NavController) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("@${status.username}", style = MaterialTheme.typography.titleSmall)
        Text(status.content, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            status.tags.forEach { tag -> TagChip(tag, onClick = { navController.navigate("tag/${tag.removePrefix("#")}") }) }
        }
        Button(onClick = { navController.navigate("status/${status.id}") }) { Text("View status") }
    }
}

private fun tagLabel(tag: Tag): String = "#${tag.name.filter(Char::isLetterOrDigit)}"
