package com.urfeed.app.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.urfeed.app.domain.model.Status
import com.urfeed.app.domain.model.User
import com.urfeed.app.feature.content.ContentViewModel
import com.urfeed.app.ui.components.UserAvatar
import com.urfeed.app.ui.components.TagChip
import coil.compose.AsyncImage

@Composable
fun StatusViewerScreen(status: Status, contentViewModel: ContentViewModel, onClose: () -> Unit, onTagClick: (String) -> Unit = {}) {
    val progress = remember(status.id) { Animatable(0f) }
    LaunchedEffect(status.id) {
        contentViewModel.markStatusViewed(status.id)
        progress.animateTo(1f, tween(7000))
        onClose()
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF111313))) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.25f))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(User(id = status.userId, fullName = status.username), size = 42.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("@${status.username}", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text("${((System.currentTimeMillis() - status.createdAt).coerceAtLeast(0L) / 60000L).coerceAtLeast(1L)}m", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close status", tint = Color.White) }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (status.mediaUrl != null) {
                        AsyncImage(model = status.mediaUrl, contentDescription = "Status image", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(12.dp)))
                    }
                    Text(status.content, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    status.tags.forEach { tag -> TagChip(tag, onClick = { onTagClick(tag) }) }
                }
            }
        }
    }
}
