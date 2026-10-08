package com.urfeed.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.feature.content.ContentViewModel
import com.urfeed.app.ui.components.FollowButton
import com.urfeed.app.ui.components.ProfileHeader
import com.urfeed.app.ui.components.TagChip
import com.urfeed.app.ui.components.UserProfileCard
import com.urfeed.app.ui.components.SocialPostCard

@Composable
fun ProfileScreen(navController: NavController, viewModel: ProfileViewModel, contentViewModel: ContentViewModel) {
    val state by viewModel.uiState.collectAsState()
    val content by contentViewModel.uiState.collectAsState()
    val user = state.currentUser
    val userPosts = content.posts.filter { it.authorId == user.id }
    val displayUser = if (userPosts.isNotEmpty()) user.copy(postsCount = userPosts.size) else user
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfileHeader(displayUser)
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ProfileStats(displayUser, onFollowers = { navController.navigate("connections/${user.id}/followers") }, onFollowing = { navController.navigate("connections/${user.id}/following") })
        Button(onClick = { navController.navigate("profile/edit") }, modifier = Modifier.fillMaxWidth()) { Text("Edit Profile") }
        Text("Interests", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { user.interests.forEach { TagChip(it) } }
        Text("Posts", style = MaterialTheme.typography.titleLarge)
        ProfilePosts(userPosts, navController, contentViewModel)
    }
}

@Composable
fun OtherUserProfileScreen(userId: String, viewModel: ProfileViewModel, navController: NavController, contentViewModel: ContentViewModel) {
    val state by viewModel.uiState.collectAsState()
    val content by contentViewModel.uiState.collectAsState()
    val user = state.users.firstOrNull { it.id == userId } ?: return
    val userPosts = content.posts.filter { it.authorId == user.id }
    val displayUser = if (userPosts.isNotEmpty()) user.copy(postsCount = userPosts.size) else user
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfileHeader(displayUser)
        ProfileStats(displayUser, onFollowers = { navController.navigate("connections/${user.id}/followers") }, onFollowing = { navController.navigate("connections/${user.id}/following") })
        FollowButton(following = user.id in state.followingIds, onClick = { viewModel.toggleFollow(user.id) }, modifier = Modifier.fillMaxWidth())
        Text("Interests", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { user.interests.forEach { TagChip(it) } }
        Text("Posts", style = MaterialTheme.typography.titleLarge)
        ProfilePosts(userPosts, navController, contentViewModel)
    }
}

@Composable
fun EditProfileScreen(viewModel: ProfileViewModel, onSaved: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val user = state.currentUser
    var fullName by remember(user.id) { mutableStateOf(user.fullName) }
    var username by remember(user.id) { mutableStateOf(user.username) }
    var bio by remember(user.id) { mutableStateOf(user.bio) }
    var interests by remember(user.id) { mutableStateOf(user.interests.joinToString(", ")) }
    var profileImage by remember(user.id) { mutableStateOf(user.profileImage.orEmpty()) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        profileImage = uri?.toString().orEmpty()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Edit profile", style = MaterialTheme.typography.headlineMedium)
        Text("Profile picture placeholder", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (profileImage.isBlank()) "Choose profile picture" else "Change profile picture")
        }
        OutlinedTextField(value = profileImage, onValueChange = { profileImage = it }, label = { Text("Image URL (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(value = interests, onValueChange = { interests = it }, label = { Text("Interests, comma separated") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                viewModel.updateProfile(fullName, username, bio, interests.split(",").map(String::trim).filter(String::isNotBlank), profileImage.ifBlank { null })
                onSaved()
            },
            enabled = fullName.isNotBlank() && username.length >= 3,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save changes") }
    }
}

@Composable
fun ConnectionsScreen(userId: String, type: String, viewModel: ProfileViewModel, navController: NavController) {
    val state by viewModel.uiState.collectAsState()
    val title = if (type == "followers") "Followers" else "Following"
    val connectionIds = if (type == "following") state.followingByUser[userId].orEmpty() else state.followersByUser[userId].orEmpty()
    androidx.compose.runtime.LaunchedEffect(userId, type) { viewModel.loadConnections(userId, type) }
    val users = state.users.filter { it.id in connectionIds && it.id != userId }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        users.forEach { user ->
            UserProfileCard(
                user = user,
                following = user.id in state.followingIds,
                onClick = { navController.navigate("user/${user.id}") },
                onFollowClick = { viewModel.toggleFollow(user.id) }
            )
        }
        if (users.isEmpty()) Text("No ${title.lowercase()} yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileStats(user: User, onFollowers: () -> Unit, onFollowing: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatItem("Followers", user.followersCount.toString(), onFollowers)
        StatItem("Following", user.followingCount.toString(), onFollowing)
        StatItem("Posts", user.postsCount.toString())
    }
}

@Composable
private fun ProfilePosts(posts: List<SocialPost>, navController: NavController, contentViewModel: ContentViewModel) {
    if (posts.isEmpty()) {
        Text("No posts yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        posts.forEach { post ->
            SocialPostCard(
                post = post,
                modifier = Modifier.padding(bottom = 10.dp),
                onProfileClick = { navController.navigate("user/${post.authorId}") },
                onLike = { contentViewModel.toggleLike(post.id) },
                onComment = { text -> contentViewModel.addComment(post.id, text) },
                onShare = { contentViewModel.sharePost(post.id) },
                onTagClick = { tag -> navController.navigate("tag/${tag.removePrefix("#")}") }
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, onClick: () -> Unit = {}) {
    Column(modifier = Modifier.clickable(onClick = onClick).padding(6.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
