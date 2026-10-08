package com.urfeed.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.urfeed.app.ui.components.PrimaryButton
import com.urfeed.app.ui.components.TopicChip

private val allInterests = listOf(
    "Technology", "AI", "Machine Learning", "Data Science", "Programming", "Python",
    "Java", "Web Development", "Mobile Development", "Cybersecurity", "Education",
    "College", "Placements", "Internships", "Career", "Startups", "Business",
    "Sports", "Cricket", "Football", "Movies", "Music", "Gaming", "Travel",
    "Photography", "Food", "Books"
)

@Composable
fun SplashScreen(onContinueToLogin: () -> Unit, onContinueToHome: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            ) {
                Box(modifier = Modifier.padding(28.dp)) {
                    Text("UR", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("URFeed", style = MaterialTheme.typography.headlineLarge)
            Text("Social discovery, simplified.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            PrimaryButton(text = "Continue", onClick = onContinueToLogin)
        }
    }
}

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val uiState by authViewModel.uiState.collectAsState()
    val loginError = remember(username, password, uiState.errorMessage) {
        validateLogin(username, password) ?: uiState.errorMessage
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Text("Login to your account", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Email or username") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(icon, contentDescription = null)
                }
            }
        )

        if (loginError != null) {
            Text(loginError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(18.dp))
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            PrimaryButton(
                text = "Login",
                enabled = validateLogin(username, password) == null,
                onClick = {
                    val validation = validateLogin(username, password)
                    if (validation != null) {
                        return@PrimaryButton
                    }
                    authViewModel.login(username, password)
                    if (uiState.currentUser != null) onLoginSuccess()
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don’t have an account?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRegisterClick) {
                Text("Register")
            }
        }
    }
}

@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onLoginClick: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val uiState by authViewModel.uiState.collectAsState()
    val registerError = remember(fullName, username, email, password, confirmPassword, uiState.errorMessage) {
        validateRegister(fullName, username, email, password, confirmPassword) ?: uiState.errorMessage
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create account", style = MaterialTheme.typography.headlineMedium)
        Text("Join URFeed", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))

        registerField(label = "Full name", value = fullName, onValueChange = { fullName = it })
        registerField(label = "Username", value = username, onValueChange = { username = it })
        registerField(label = "Email", value = email, onValueChange = { email = it }, keyboardType = KeyboardType.Email)
        registerField(label = "Password", value = password, onValueChange = { password = it }, password = true, passwordVisible = passwordVisible)
        registerField(label = "Confirm password", value = confirmPassword, onValueChange = { confirmPassword = it }, password = true, passwordVisible = passwordVisible)

        if (registerError != null) {
            Text(registerError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(18.dp))
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            PrimaryButton(
                text = "Register",
                enabled = validateRegister(fullName, username, email, password, confirmPassword) == null,
                onClick = {
                    val error = validateRegister(fullName, username, email, password, confirmPassword)
                    if (error != null) {
                        return@PrimaryButton
                    }
                    authViewModel.register(fullName, username, email, password)
                    if (uiState.currentUser != null) onRegisterSuccess()
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onLoginClick) {
                Text("Login")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun InterestSelectionScreen(authViewModel: AuthViewModel, onContinue: () -> Unit) {
    val uiState by authViewModel.uiState.collectAsState()
    val selected = remember { mutableStateOf(uiState.selectedInterests.toMutableSet()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Choose topics you’re interested in", style = MaterialTheme.typography.headlineMedium)
        Text("Select multiple areas to personalize your feed.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            allInterests.forEach { interest ->
                val isSelected = selected.value.contains(interest)
                TopicChip(
                    label = interest,
                    selected = isSelected,
                    onClick = {
                        if (isSelected) selected.value.remove(interest)
                        else selected.value.add(interest)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        PrimaryButton(
            text = "Continue",
            onClick = {
                authViewModel.saveSelectedInterests(selected.value.toList())
                onContinue()
            }
        )
    }
}

private fun validateLogin(username: String, password: String): String? {
    if (username.isBlank()) return "Username is required."
    if (password.isBlank()) return "Password is required."
    if (password.length < 6) return "Password must be at least 6 characters."
    return null
}

private fun validateRegister(
    fullName: String,
    username: String,
    email: String,
    password: String,
    confirmPassword: String
): String? {
    if (fullName.isBlank()) return "Full name is required."
    if (username.isBlank()) return "Username is required."
    if (username.length < 3) return "Username must be at least 3 characters."
    if (email.isBlank() || !email.contains("@")) return "Enter a valid email."
    if (password.length < 6) return "Password must be at least 6 characters."
    if (password != confirmPassword) return "Passwords do not match."
    return null
}

@Composable
private fun registerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    passwordVisible: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None
    )
}
