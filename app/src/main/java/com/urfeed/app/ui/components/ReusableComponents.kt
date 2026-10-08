package com.urfeed.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urfeed.app.domain.model.NotificationType
import com.urfeed.app.domain.model.SocialPost
import com.urfeed.app.domain.model.TopicCard
import com.urfeed.app.domain.model.UserNotification
import com.urfeed.app.domain.model.User
import coil.compose.AsyncImage

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search topics and people"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun TopicChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
fun StatusBubble(
    name: String,
    accent: Color,
    isMe: Boolean = false,
    isViewed: Boolean = false,
    profileImage: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
                .border(
                    width = if (!isViewed || isMe) 2.dp else 1.dp,
                    color = if (!isViewed || isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!profileImage.isNullOrBlank()) {
                AsyncImage(model = profileImage, contentDescription = "$name profile picture", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(CircleShape))
            } else {
                Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Text(text = if (name.firstOrNull()?.isLetter() == true) name.first().uppercase() else "+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
        Text(
            text = if (name.length > 8) name.take(8) + "..." else name,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SocialPostCard(
    post: SocialPost,
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onLike: () -> Unit = {},
    onComment: (String) -> Unit = {},
    onShare: () -> Unit = {},
    onTagClick: (String) -> Unit = {}
) {
    var commentDialogVisible by remember(post.id) { mutableStateOf(false) }
    var commentText by remember(post.id) { mutableStateOf("") }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onProfileClick)) {
                UserAvatar(User(id = post.authorId, fullName = post.author, username = post.handle, profileImage = post.profileImage), size = 42.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(post.author, style = MaterialTheme.typography.titleMedium)
                    Text("@${post.handle} · ${post.time}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(post.text, style = MaterialTheme.typography.bodyLarge)

            if (post.hasImage || post.imageUrl != null) {
                if (post.imageUrl != null) {
                    AsyncImage(model = post.imageUrl, contentDescription = "Post image", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)))
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
            }

            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                post.tags.forEach { tag -> TagChip(tag = tag, onClick = { onTagClick(tag) }) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "${post.likes}", onClick = onLike, selected = post.isLiked)
                ActionButton(Icons.AutoMirrored.Filled.Comment, "${post.comments}", onClick = { commentDialogVisible = true })
                ActionButton(Icons.Default.Share, if (post.isShared) "Shared" else "${post.shares}", onClick = onShare, selected = post.isShared)
            }
        }
    }
    if (commentDialogVisible) {
        AlertDialog(
            onDismissRequest = { commentDialogVisible = false },
            title = { Text("Comment on @${post.handle}'s post") },
            text = {
                TextField(value = commentText, onValueChange = { commentText = it }, placeholder = { Text("Write a comment") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (commentText.isNotBlank()) onComment(commentText.trim())
                    commentText = ""
                    commentDialogVisible = false
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { commentDialogVisible = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}, selected: Boolean = false) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun BottomNavigationBar(
    selected: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem("home", Icons.Default.Home, "Home", selected == "home", onItemSelected)
            NavBarItem("explore", Icons.Outlined.Explore, "Explore", selected == "explore", onItemSelected)
            NavBarItem("create", Icons.Default.Add, "Create", selected == "create", onItemSelected, isPrimary = true)
            NavBarItem("notifications", Icons.Default.Notifications, "Notifications", selected == "notifications", onItemSelected)
            NavBarItem("profile", Icons.Default.Person, "Profile", selected == "profile", onItemSelected)
        }
    }
}

@Composable
fun NavBarItem(
    route: String,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onItemSelected: (String) -> Unit,
    isPrimary: Boolean = false
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (isPrimary) MaterialTheme.colorScheme.primary else Color.Transparent
    val iconTint = if (isPrimary) Color.White else color

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background.copy(alpha = if (isPrimary) 1f else 0f))
            .clickable { onItemSelected(route) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(if (isPrimary) 26.dp else 22.dp))
            if (!isPrimary) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileHeader(
    name: String,
    username: String,
    bio: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(42.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(name, style = MaterialTheme.typography.headlineSmall)
                Text("@$username", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(bio, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun UserAvatar(user: User, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 72.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (!user.profileImage.isNullOrBlank()) {
            AsyncImage(model = user.profileImage, contentDescription = "${user.fullName} profile picture",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().size(size).clip(CircleShape))
        } else {
            Text(text = user.fullName.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ProfileHeader(user: User, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            UserAvatar(user = user)
            Column {
                Text(user.fullName, style = MaterialTheme.typography.headlineSmall)
                Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(user.bio, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FollowButton(following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (following) {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text("Following ✓") }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text("Follow") }
    }
}

@Composable
fun UserProfileCard(
    user: User,
    following: Boolean,
    onClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(user = user, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(user.fullName, style = MaterialTheme.typography.titleSmall)
                Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FollowButton(following = following, onClick = onFollowClick)
        }
    }
}

@Composable
fun NotificationItem(notification: UserNotification, modifier: Modifier = Modifier) {
    val icon = when (notification.type) {
        NotificationType.FOLLOW -> Icons.Default.Person
        NotificationType.LIKE -> Icons.Default.FavoriteBorder
        NotificationType.COMMENT -> Icons.AutoMirrored.Filled.Comment
        NotificationType.CLOSE_FRIENDS -> Icons.Default.Visibility
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(notification.title, style = MaterialTheme.typography.titleMedium)
                Text(notification.message, style = MaterialTheme.typography.bodyMedium)
            }
            Text(notification.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Text(text, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
fun TopicCardItem(item: TopicCard, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = item.accent.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item.count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        trailingIcon = trailingIcon
    )
}
