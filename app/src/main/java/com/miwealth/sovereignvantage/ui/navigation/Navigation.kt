package com.miwealth.sovereignvantage.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.miwealth.sovereignvantage.ui.login.LoginScreen
import com.miwealth.sovereignvantage.ui.dashboard.DashboardScreen
import com.miwealth.sovereignvantage.ui.trading.TradingScreen
import com.miwealth.sovereignvantage.ui.portfolio.PortfolioScreen
import com.miwealth.sovereignvantage.ui.settings.SettingsScreen
import com.miwealth.sovereignvantage.education.ui.EducationScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object Trading : Screen("trading")
    object Portfolio : Screen("portfolio")
    object Education : Screen("education")
    object Settings : Screen("settings")
}

@Composable
fun SovereignVantageNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToTrading = { navController.navigate(Screen.Trading.route) },
                onNavigateToPortfolio = { navController.navigate(Screen.Portfolio.route) },
                onNavigateToEducation = { navController.navigate(Screen.Education.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        
        composable(Screen.Trading.route) {
            TradingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Portfolio.route) {
            PortfolioScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Education.route) {
            EducationScreen(
                onLessonClick = { /* Future: navigate to lesson detail */ }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
