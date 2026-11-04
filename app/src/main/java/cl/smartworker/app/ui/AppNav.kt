package cl.smartworker.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.smartworker.app.data.Repo
import cl.smartworker.app.data.Role
import cl.smartworker.app.ui.screens.*
import cl.smartworker.app.ui.theme.Terracota
import cl.smartworker.app.ui.theme.TerracotaSoft

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val workerTabs = listOf(
    Tab("w_home", "Inicio", Icons.Outlined.Home),
    Tab("w_metrics", "Métricas", Icons.Outlined.BarChart),
    Tab("w_alert", "Ayuda", Icons.Outlined.Shield)
)
private val supervisorTabs = listOf(
    Tab("s_summary", "Resumen", Icons.Outlined.GridView),
    Tab("s_team", "Equipo", Icons.Outlined.Groups),
    Tab("s_alerts", "Alertas", Icons.Outlined.WarningAmber)
)

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val currentUser by Repo.user.collectAsState()
    LaunchedEffect(route, currentUser) {
        if (currentUser == null && (route?.startsWith("w_") == true || route?.startsWith("s_") == true)) {
            nav.navigate("login") { popUpTo(0) }
        }
    }

    val tabs = when {
        route == null -> null
        route.startsWith("w_") -> workerTabs
        route.startsWith("s_") -> supervisorTabs
        else -> null
    }

    fun enter(role: Role) {
        val dest = if (role == Role.SUPERVISOR) "s_summary" else "w_home"
        nav.navigate(dest) { popUpTo("login") { inclusive = true } }
    }
    fun logout() {
        Repo.logout()
        nav.navigate("login") { popUpTo(0) }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.White,
        bottomBar = {
            if (tabs != null) {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                    tabs.forEach { t ->
                        // "s_detail/..." cuenta como pestaña Equipo
                        val selected = route == t.route || (t.route == "s_team" && route?.startsWith("s_detail") == true)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.goTab(t.route) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Terracota, selectedTextColor = Terracota,
                                indicatorColor = TerracotaSoft
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "login", modifier = Modifier.padding(pad)) {
            composable("login") {
                LoginScreen(onLoggedIn = { enter(it.role) }, onRegister = { nav.navigate("register") })
            }
            composable("register") {
                RegisterScreen(onRegistered = { enter(it.role) }, onLogin = { nav.popBackStack() })
            }
            // Trabajador
            composable("w_home") { WorkerHomeScreen(onGoMetrics = { nav.goTab("w_metrics") }) }
            composable("w_metrics") { WorkerMetricsScreen() }
            composable("w_alert") { WorkerAlertScreen(onLogout = ::logout) }
            // Supervisor
            composable("s_summary") {
                SupervisorSummaryScreen(
                    onOpenWorker = { nav.navigate("s_detail/$it") },
                    onAllAlerts = { nav.goTab("s_alerts") },
                    onTeam = { nav.goTab("s_team") }
                )
            }
            composable("s_team") { SupervisorTeamScreen(onOpenWorker = { nav.navigate("s_detail/$it") }) }
            composable("s_alerts") {
                SupervisorAlertsScreen(onOpenWorker = { nav.navigate("s_detail/$it") }, onLogout = ::logout)
            }
            composable("s_detail/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                WorkerDetailScreen(it.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
        }
    }
}
