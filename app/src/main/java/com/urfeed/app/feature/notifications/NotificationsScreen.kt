package com.urfeed.app.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.urfeed.app.domain.model.NotificationType
import com.urfeed.app.domain.model.UserNotification
import com.urfeed.app.ui.components.NotificationItem

@Composable
fun NotificationsScreen(navController: NavController) {
    val notifications = listOf(
        UserNotification("1", "Someone followed you", "Aisha started following your updates.", "2m", NotificationType.FOLLOW),
        UserNotification("2", "Someone liked your post", "Rohan liked your AI workflow post.", "14m", NotificationType.LIKE),
        UserNotification("3", "Someone commented on your post", "Maya commented: This is inspiring!", "1h", NotificationType.COMMENT),
        UserNotification("4", "Someone added you to Close Friends", "Priya added you to her close friends list.", "2h", NotificationType.CLOSE_FRIENDS)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium)
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            items(notifications) { notification ->
                NotificationItem(notification = notification)
            }
        }
    }
}
