package com.urfeed.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.urfeed.app.data.local.AuthPreferences
import com.urfeed.app.data.remote.NetworkModule
import com.urfeed.app.data.repository.RemoteUserRepository
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

data class ProfileUiState(
    val currentUser: User = User(),
    val users: List<User> = emptyList(),
    val followingIds: Set<String> = emptySet(),
    val followersByUser: Map<String, Set<String>> = emptyMap(),
    val followingByUser: Map<String, Set<String>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val repository: UserRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val users = repository.getUsers()
                val currentUser = users.firstOrNull() ?: User()
                val followers = repository.getFollowerIds(currentUser.id)
                val followings = repository.getFollowingIds(currentUser.id)
                ProfileUiState(
                    currentUser = currentUser, users = users, followingIds = followings,
                    followersByUser = mapOf(currentUser.id to followers),
                    followingByUser = mapOf(currentUser.id to followings)
                )
            }.onSuccess { _uiState.value = it }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Profiles are temporarily unavailable.") }
        }
    }

    fun setCurrentUser(user: User) {
        _uiState.value = _uiState.value.copy(currentUser = user)
    }

    fun updateProfile(fullName: String, username: String, bio: String, interests: List<String>, profileImage: String?) {
        val updated = _uiState.value.currentUser.copy(
            fullName = fullName.trim(),
            username = username.trim().removePrefix("@"),
            bio = bio.trim(),
            interests = interests,
            profileImage = profileImage
        )
        viewModelScope.launch {
            runCatching { repository.updateProfile(updated) }
                .onSuccess { refresh() }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = "Profile changes could not be saved.") }
        }
    }

    fun loadConnections(userId: String, type: String) {
        viewModelScope.launch {
            runCatching {
                if (type == "followers") repository.getFollowerIds(userId) else repository.getFollowingIds(userId)
            }.onSuccess { ids ->
                val state = _uiState.value
                _uiState.value = if (type == "followers") state.copy(followersByUser = state.followersByUser + (userId to ids))
                else state.copy(followingByUser = state.followingByUser + (userId to ids))
            }
        }
    }

    fun toggleFollow(userId: String) {
        if (userId == _uiState.value.currentUser.id) return
        viewModelScope.launch {
            runCatching { repository.setFollowing(userId, userId !in _uiState.value.followingIds) }
                .onSuccess { refresh() }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = "Follow state could not be updated.") }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val preferences = AuthPreferences(context.applicationContext)
            val repository = RemoteUserRepository(preferences, NetworkModule.api(context))
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository) as T
        }
    }

}
