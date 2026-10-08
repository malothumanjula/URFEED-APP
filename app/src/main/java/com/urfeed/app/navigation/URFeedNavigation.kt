package com.urfeed.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.urfeed.app.feature.auth.AuthViewModel
import com.urfeed.app.feature.auth.InterestSelectionScreen
import com.urfeed.app.feature.auth.LoginScreen
import com.urfeed.app.feature.auth.RegisterScreen
import com.urfeed.app.feature.auth.SplashScreen
import com.urfeed.app.feature.create.CreateScreen
import com.urfeed.app.feature.explore.ExploreScreen
import com.urfeed.app.feature.home.HomeScreen
import com.urfeed.app.feature.notifications.NotificationsScreen
import com.urfeed.app.feature.profile.ProfileScreen
import com.urfeed.app.feature.profile.ProfileViewModel
import com.urfeed.app.feature.profile.EditProfileScreen
import com.urfeed.app.feature.profile.OtherUserProfileScreen
import com.urfeed.app.feature.profile.ConnectionsScreen
import com.urfeed.app.feature.content.ContentViewModel
import com.urfeed.app.feature.home.StatusViewerScreen
import com.urfeed.app.feature.explore.SearchViewModel
import com.urfeed.app.feature.explore.TagResultsScreen
import com.urfeed.app.ui.components.BottomNavigationBar

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Interests : Screen("interests")
    object Home : Screen("home")
    object Explore : Screen("explore")
    object Create : Screen("create")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
}

@Composable
fun URFeedNavHost(authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val authState by authViewModel.uiState.collectAsState()
    val profileViewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = ProfileViewModel.Factory(context))
    val contentViewModel: ContentViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = ContentViewModel.Factory(context))
    val searchViewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = SearchViewModel.Factory(context))
    val profileState by profileViewModel.uiState.collectAsState()
    LaunchedEffect(authState.currentUser) {
        authState.currentUser?.let {
            profileViewModel.setCurrentUser(it)
            contentViewModel.setCurrentUser(it)
        }
    }
    LaunchedEffect(profileState.currentUser) {
        contentViewModel.setCurrentUser(profileState.currentUser)
    }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val bottomBarRoutes = setOf(
        Screen.Home.route,
        Screen.Explore.route,
        Screen.Create.route,
        Screen.Notifications.route,
        Screen.Profile.route
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                BottomNavigationBar(
                    selected = currentRoute ?: Screen.Home.route,
                    onItemSelected = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onContinueToLogin = { navController.navigate(Screen.Login.route) },
                    onContinueToHome = { navController.navigate(Screen.Home.route) }
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onRegisterClick = { navController.navigate(Screen.Register.route) }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    authViewModel = authViewModel,
                    onRegisterSuccess = {
                        navController.navigate(Screen.Interests.route) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onLoginClick = { navController.navigate(Screen.Login.route) }
                )
            }
            composable(Screen.Interests.route) {
                InterestSelectionScreen(
                    authViewModel = authViewModel,
                    onContinue = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Interests.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Home.route) { HomeScreen(navController, profileViewModel, contentViewModel) }
            composable(Screen.Explore.route) { ExploreScreen(navController, profileViewModel, contentViewModel, searchViewModel) }
            composable(Screen.Create.route) { CreateScreen(contentViewModel) }
            composable(Screen.Notifications.route) { NotificationsScreen(navController) }
            composable(Screen.Profile.route) { ProfileScreen(navController, profileViewModel, contentViewModel) }
            composable("profile/edit") {
                EditProfileScreen(profileViewModel) { navController.popBackStack() }
            }
            composable(
                route = "status/{statusId}",
                arguments = listOf(navArgument("statusId") { type = NavType.StringType })
            ) { entry ->
                val statusId = entry.arguments?.getString("statusId")
                val status = contentViewModel.activeStatuses().firstOrNull { it.id == statusId }
                if (status != null) {
                    StatusViewerScreen(status, contentViewModel, { navController.popBackStack() }) { tag ->
                        navController.navigate("tag/${tag.removePrefix("#")}")
                    }
                }
            }
            composable(
                route = "tag/{tagSlug}",
                arguments = listOf(navArgument("tagSlug") { type = NavType.StringType })
            ) { entry ->
                TagResultsScreen(
                    entry.arguments?.getString("tagSlug").orEmpty(),
                    navController,
                    profileViewModel,
                    contentViewModel,
                    searchViewModel
                )
            }
            composable(
                route = "user/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                OtherUserProfileScreen(entry.arguments?.getString("userId").orEmpty(), profileViewModel, navController, contentViewModel)
            }
            composable(
                route = "connections/{userId}/{type}",
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType }
                )
            ) { entry ->
                ConnectionsScreen(
                    entry.arguments?.getString("userId").orEmpty(),
                    entry.arguments?.getString("type").orEmpty(),
                    profileViewModel,
                    navController
                )
            }
        }

        if (authState.authStatus == com.urfeed.app.feature.auth.AuthStatus.AUTHENTICATED && currentRoute == Screen.Splash.route) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
                launchSingleTop = true
            }
        }

        if (authState.authStatus == com.urfeed.app.feature.auth.AuthStatus.UNAUTHENTICATED && currentRoute == Screen.Splash.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
