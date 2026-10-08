package com.urfeed.app.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urfeed.app.data.local.AuthPreferences
import com.urfeed.app.data.repository.RemoteAuthRepository
import com.urfeed.app.data.remote.NetworkModule
import com.urfeed.app.domain.model.User
import com.urfeed.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthStatus {
    LOADING,
    UNAUTHENTICATED,
    AUTHENTICATED,
    SUCCESS,
    ERROR
}

data class AuthUiState(
    val authStatus: AuthStatus = AuthStatus.LOADING,
    val isLoading: Boolean = false,
    val currentUser: User? = null,
    val errorMessage: String? = null,
    val selectedInterests: List<String> = emptyList()
)

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, authStatus = AuthStatus.LOADING)
            val user = runCatching { repository.initialize() }.getOrNull()
            _uiState.value = _uiState.value.copy(currentUser = user, isLoading = false,
                authStatus = if (user != null) AuthStatus.AUTHENTICATED else AuthStatus.UNAUTHENTICATED)
        }
        viewModelScope.launch {
            repository.selectedInterests.collect { selected ->
                _uiState.value = _uiState.value.copy(selectedInterests = selected)
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val user = runCatching { repository.login(username, password) }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(isLoading = false, authStatus = AuthStatus.ERROR, errorMessage = error.toUserMessage())
                return@launch
            }
            if (user != null) {
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isLoading = false,
                    authStatus = AuthStatus.AUTHENTICATED,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    authStatus = AuthStatus.ERROR,
                    errorMessage = "Invalid credentials."
                )
            }
        }
    }

    fun register(fullName: String, username: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val user = runCatching { repository.register(fullName, username, email, password) }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(isLoading = false, authStatus = AuthStatus.ERROR, errorMessage = error.toUserMessage())
                return@launch
            }
            if (user != null) {
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isLoading = false,
                    authStatus = AuthStatus.SUCCESS,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    authStatus = AuthStatus.ERROR,
                    errorMessage = "Unable to create account right now."
                )
            }
        }
    }

    fun saveSelectedInterests(interests: List<String>) {
        viewModelScope.launch {
            repository.saveSelectedInterests(interests)
            _uiState.value = _uiState.value.copy(selectedInterests = interests)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = AuthUiState(authStatus = AuthStatus.UNAUTHENTICATED)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = RemoteAuthRepository(context, NetworkModule.api(context))
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
    }
}

private fun Throwable.toUserMessage(): String = when (this) {
    is java.io.IOException -> "Cannot reach URFeed right now. Check your connection and try again."
    is retrofit2.HttpException -> when (code()) {
        401 -> "Invalid username/email or password."
        409 -> "That username or email is already registered."
        else -> "URFeed could not complete the request. Please try again."
    }
    else -> "Something went wrong. Please try again."
}
