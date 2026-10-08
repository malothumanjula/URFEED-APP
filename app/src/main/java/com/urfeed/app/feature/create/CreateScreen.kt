package com.urfeed.app.feature.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.urfeed.app.domain.model.PostVisibility
import com.urfeed.app.domain.model.Tag
import com.urfeed.app.feature.content.ContentViewModel
import com.urfeed.app.ui.components.TagChip
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(contentViewModel: ContentViewModel) {
    var selectedType by remember { mutableStateOf("POST") }
    var postText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var postMedia by remember { mutableStateOf<String?>(null) }
    var statusMedia by remember { mutableStateOf<String?>(null) }
    var postVisibility by remember { mutableStateOf(PostVisibility.PUBLIC) }
    var statusVisibility by remember { mutableStateOf(PostVisibility.PUBLIC) }
    var customTag by remember { mutableStateOf("") }
    val state by contentViewModel.uiState.collectAsState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let { if (selectedType == "POST") postMedia = it else statusMedia = it }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Create", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("POST", "STATUS").forEach { type ->
                FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type) })
            }
        }
        Text(
            if (selectedType == "POST") "Create a post" else "Create a status · expires in 24 hours",
            style = MaterialTheme.typography.titleLarge
        )
        OutlinedTextField(
            value = if (selectedType == "POST") postText else statusText,
            onValueChange = { text ->
                if (selectedType == "POST") {
                    postText = text
                    contentViewModel.updateTagSuggestions(text)
                } else {
                    statusText = text
                    contentViewModel.updateTagSuggestions(text)
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            minLines = if (selectedType == "POST") 5 else 4,
            label = { Text(if (selectedType == "POST") "What's on your mind?" else "Share a moment") }
        )
        OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
            androidx.compose.material3.Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
            Text(if ((if (selectedType == "POST") postMedia else statusMedia) == null) "Add image" else "Image selected")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Visibility", style = MaterialTheme.typography.labelLarge)
            PostVisibility.values().forEach { visibility ->
                FilterChip(
                    selected = (if (selectedType == "POST") postVisibility else statusVisibility) == visibility,
                    onClick = { if (selectedType == "POST") postVisibility = visibility else statusVisibility = visibility },
                    label = { Text(visibility.name.lowercase(Locale.getDefault()).replaceFirstChar(Char::uppercase)) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("AI Suggested Topics", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { contentViewModel.regenerateTagSuggestions(if (selectedType == "POST") postText else statusText) }) { Text("Regenerate") }
        }
        if (state.isAnalyzingTags) Text("Analyzing topics...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.suggestedTags.isEmpty()) {
            Text("Suggestions appear as you write.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.suggestedTags.forEach { suggestion ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(canonicalTagLabel(suggestion.tag))
                        Text("${(suggestion.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { contentViewModel.removeSuggestedTag(suggestion.tag.id) }) { Text("×") }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = customTag, onValueChange = { customTag = it }, modifier = Modifier.weight(1f), label = { Text("Custom tag") }, singleLine = true)
            Button(onClick = {
                val tag = "#${customTag.trim().removePrefix("#").replace(" ", "")}".takeIf { it.length > 1 }
                tag?.let { contentViewModel.addCustomTag(it) }
                customTag = ""
            }) { Text("Add") }
        }
        state.creationMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
            androidx.compose.runtime.LaunchedEffect(it) {
                kotlinx.coroutines.delay(1800)
                contentViewModel.clearCreationMessage()
            }
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (selectedType == "POST") {
                    contentViewModel.createPost(postText, postMedia, postVisibility, state.suggestedTags.map { it.tag }.distinctBy(Tag::id))
                    postText = ""
                    postMedia = null
                } else {
                    contentViewModel.createStatus(statusText, statusMedia, statusVisibility, state.suggestedTags.map { it.tag }.distinctBy(Tag::id))
                    statusText = ""
                    statusMedia = null
                }
                contentViewModel.updateTagSuggestions("")
            },
            enabled = if (selectedType == "POST") postText.isNotBlank() else statusText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (selectedType == "POST") "Publish post" else "Share status") }
    }
}

private fun canonicalTagLabel(tag: Tag): String = "#${tag.name.filter(Char::isLetterOrDigit)}"
