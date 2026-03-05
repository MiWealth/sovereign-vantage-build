package com.miwealth.sovereignvantage.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.miwealth.sovereignvantage.ui.login.LoginScreen
import com.miwealth.sovereignvantage.ui.dashboard.DashboardScreen
import com.miwealth.sovereignvantage.ui.trading.TradingScreen
import com.miwealth.sovereignvantage.ui.wallet.WalletScreen
import com.miwealth.sovereignvantage.ui.portfolio.PortfolioScreen
import com.miwealth.sovereignvantage.ui.settings.SettingsScreen
import com.miwealth.sovereignvantage.education.ui.EducationScreen
import com.miwealth.sovereignvantage.education.ui.LessonDetailScreen
import com.miwealth.sovereignvantage.ui.wallet.CoinDetailScreen
import com.miwealth.sovereignvantage.ui.theme.*

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object Trading : Screen("trading")
    object AIBoard : Screen("aiboard")  // BUILD #110: The Octagon
    object Wallet : Screen("wallet")
    object Portfolio : Screen("portfolio")
    object Education : Screen("education")
    object Settings : Screen("settings")
    object LessonDetail : Screen("lesson/{lessonId}") {
        fun createRoute(lessonId: Int) = "lesson/$lessonId"
    }
    object CoinDetail : Screen("coin/{symbol}") {
        fun createRoute(symbol: String) = "coin/$symbol"
    }
}

/**
 * Bottom navigation tabs — persistent across all main screens.
 */
data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Home", Icons.Default.Dashboard),
    BottomNavItem(Screen.Trading, "Trading", Icons.Default.TrendingUp),
    BottomNavItem(Screen.Wallet, "Wallet", Icons.Default.AccountBalanceWallet),
    BottomNavItem(Screen.Portfolio, "Portfolio", Icons.Default.Analytics),
    BottomNavItem(Screen.Education, "Learn", Icons.Default.School)
)

// Routes that show the bottom nav bar (i.e. all post-login screens)
private val bottomNavRoutes = setOf(
    Screen.Dashboard.route,
    Screen.Trading.route,
    Screen.Wallet.route,
    Screen.Portfolio.route,
    Screen.Education.route,
    Screen.Settings.route
)

// Also show bottom nav on detail screens (matched by prefix)
private fun shouldShowBottomNav(route: String?): Boolean {
    if (route == null) return false
    if (route in bottomNavRoutes) return true
    if (route.startsWith("lesson/") || route.startsWith("coin/")) return true
    return false
}

@Composable
fun SovereignVantageNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = shouldShowBottomNav(currentRoute)

    Scaffold(
        containerColor = VintageColors.EmeraldDeep,
        bottomBar = {
            if (showBottomBar) {
                // ═══════════════════════════════════════════════════════
                // LUXURY GOLD NAVIGATION BAR
                // Brushed gold surface with emerald active icons
                // ═══════════════════════════════════════════════════════
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = VintageColors.EmeraldDark,
                    shadowElevation = 12.dp,
                    tonalElevation = 0.dp
                ) {
                    Column {
                        // Gold accent line at top of nav bar
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            VintageColors.GoldDark,
                                            VintageColors.Gold,
                                            VintageColors.GoldBright,
                                            VintageColors.Gold,
                                            VintageColors.GoldDark,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            VintageColors.EmeraldMedium,
                                            VintageColors.EmeraldDark,
                                            VintageColors.EmeraldDeep
                                        )
                                    )
                                )
                                .height(72.dp)
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.screen.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (currentRoute != item.screen.route) {
                                            navController.navigate(item.screen.route) {
                                                popUpTo(Screen.Dashboard.route) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            item.icon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(if (selected) 26.dp else 22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            item.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            letterSpacing = if (selected) 1.sp else 0.sp,
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = VintageColors.Gold,
                                        selectedTextColor = VintageColors.Gold,
                                        unselectedIconColor = VintageColors.TextTertiary,
                                        unselectedTextColor = VintageColors.TextTertiary,
                                        indicatorColor = VintageColors.GoldAlpha10
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
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
                    onNavigateToTrading = {
                        navController.navigate(Screen.Trading.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAIBoard = {
                        navController.navigate(Screen.AIBoard.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToWallet = {
                        navController.navigate(Screen.Wallet.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToPortfolio = {
                        navController.navigate(Screen.Portfolio.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToEducation = {
                        navController.navigate(Screen.Education.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Trading.route) {
                TradingScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AIBoard.route) {
                com.miwealth.sovereignvantage.ui.aiboard.AIBoardScreen()
            }

            composable(Screen.Wallet.route) {
                WalletScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCoinClick = { symbol ->
                        navController.navigate(Screen.CoinDetail.createRoute(symbol)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Portfolio.route) {
                PortfolioScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Education.route) {
                EducationScreen(
                    onLessonClick = { lessonId ->
                        navController.navigate(Screen.LessonDetail.createRoute(lessonId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Lesson Detail
            composable(
                route = Screen.LessonDetail.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.IntType })
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getInt("lessonId") ?: 1
                LessonDetailScreen(
                    lessonId = lessonId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Coin Detail
            composable(
                route = Screen.CoinDetail.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: "BTC"
                CoinDetailScreen(
                    symbol = symbol,
                    onNavigateBack = { navController.popBackStack() }
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
}
