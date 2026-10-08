package com.urfeed.app.feature.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.feature.content.ContentViewModel
import com.urfeed.app.feature.profile.ProfileViewModel
import com.urfeed.app.ui.components.SearchBar
import com.urfeed.app.ui.components.SocialPostCard
import com.urfeed.app.ui.components.StatusBubble
import com.urfeed.app.ui.components.TopicChip

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController, profileViewModel: ProfileViewModel, contentViewModel: ContentViewModel) {
    val profile by profileViewModel.uiState.collectAsState()
    val content by contentViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf("For You") }
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val statuses = contentViewModel.activeStatuses()
    val following = profile.followingIds
    val filteredPosts = when {
        query.isNotBlank() -> contentViewModel.searchPosts(query)
        selectedTab == "Following" -> content.posts.filter { it.authorId in following || it.authorId == profile.currentUser.id }
        else -> content.posts
    }.filter { post -> selectedTopic == null || post.tags.any { it.equals(selectedTopic, true) } }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("URFeed", style = MaterialTheme.typography.headlineMedium)
        Text("Stay curious.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        SearchBar(value = query, onValueChange = { query = it }, placeholder = "Search people and posts")
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                StatusBubble(
                    name = "Your status", accent = MaterialTheme.colorScheme.primary, isMe = true,
                    profileImage = profile.currentUser.profileImage,
                    onClick = { navController.navigate("create") }
                )
            }
            items(statuses) { status ->
                val user = profile.users.firstOrNull { it.id == status.userId }
                StatusBubble(
                    name = status.username,
                    accent = MaterialTheme.colorScheme.tertiary,
                    isViewed = status.isViewed,
                    profileImage = status.profileImage ?: user?.profileImage,
                    onClick = { navController.navigate("status/${status.id}") }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Topics", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("#ArtificialIntelligence", "#Python", "#Design", "#Travel").forEach { topic ->
                TopicChip(label = topic, selected = selectedTopic == topic, onClick = { selectedTopic = if (selectedTopic == topic) null else topic })
            }
        }
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = if (selectedTab == "For You") 0 else 1) {
            listOf("For You", "Following").forEach { tab ->
                Tab(selected = selectedTab == tab, onClick = {
                    selectedTab = tab
                    contentViewModel.refresh(following = tab == "Following")
                }, text = { Text(tab) })
            }
        }
        Spacer(Modifier.height(14.dp))
        content.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            androidx.compose.material3.TextButton(onClick = { contentViewModel.refresh(following = selectedTab == "Following") }) { Text("Retry") }
        }
        if (content.isLoading) Text("Loading feed...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filteredPosts.isEmpty()) {
            Text("No posts match this view yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            filteredPosts.forEach { post ->
                FeedPost(post, navController, contentViewModel)
            }
        }
    }
}

@Composable
private fun FeedPost(post: SocialPost, navController: NavController, contentViewModel: ContentViewModel) {
    SocialPostCard(
        post = post,
        modifier = Modifier.padding(bottom = 14.dp),
        onProfileClick = { if (post.authorId.isNotBlank()) navController.navigate("user/${post.authorId}") },
        onLike = { contentViewModel.toggleLike(post.id) },
        onComment = { text -> contentViewModel.addComment(post.id, text) },
        onShare = { contentViewModel.sharePost(post.id) },
        onTagClick = { tag -> navController.navigate("tag/${tag.removePrefix("#")}") }
    )
}
