package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.obd.model.ObdConnectionState
import com.example.ui.ObdViewModel
import com.example.ui.components.AndroidAutoPreviewDialog
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.SensorsScreen
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.ObdAutoTheme
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute
@Serializable data object SensorsRoute
@Serializable data object DiagnosticsRoute
@Serializable data object ConnectionRoute

private data class NavDestinationItem<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            ObdAutoTheme {
                ObdAutoRootApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdAutoRootApp(
    viewModel: ObdViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var showAaPreview by remember { mutableStateOf(false) }

    if (showAaPreview) {
        AndroidAutoPreviewDialog(
            uiState = uiState,
            onDismiss = { showAaPreview = false },
            onTogglePinAuto = { viewModel.toggleAndroidAutoPid(it) },
            onScanDtcs = { viewModel.scanDtcs() },
            onStartSimulation = { viewModel.startSimulationMode() },
            onDisconnect = { viewModel.disconnect() }
        )
    }

    val destinations = listOf(
        NavDestinationItem(
            route = DashboardRoute,
            label = "Приборы",
            selectedIcon = Icons.Filled.Speed,
            unselectedIcon = Icons.Outlined.Speed,
            testTag = "nav_dashboard"
        ),
        NavDestinationItem(
            route = SensorsRoute,
            label = "Датчики",
            selectedIcon = Icons.Filled.ShowChart,
            unselectedIcon = Icons.Outlined.ShowChart,
            testTag = "nav_sensors"
        ),
        NavDestinationItem(
            route = DiagnosticsRoute,
            label = "Ошибки",
            selectedIcon = Icons.Filled.Build,
            unselectedIcon = Icons.Outlined.Build,
            testTag = "nav_diagnostics"
        ),
        NavDestinationItem(
            route = ConnectionRoute,
            label = "OBD2",
            selectedIcon = Icons.Filled.DirectionsCar,
            unselectedIcon = Icons.Outlined.DirectionsCar,
            testTag = "nav_connection"
        )
    )

    fun navigateSingleTop(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = CarbonObsidian,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "OBD2 Авто",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            ConnectionStatusPill(uiState.connectionState)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = CarbonObsidian
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = CockpitSurface,
                        tonalElevation = 0.dp
                    ) {
                        destinations.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateSingleTop(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TelemetryCyan,
                                    selectedTextColor = TelemetryCyan,
                                    indicatorColor = TelemetryCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = CockpitSurface
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        destinations.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateSingleTop(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = TelemetryCyan,
                                    selectedTextColor = TelemetryCyan,
                                    indicatorColor = TelemetryCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                uiState = uiState,
                                onNavigateToConnection = { navigateSingleTop(ConnectionRoute) },
                                onStartSimulation = { viewModel.startSimulationMode() },
                                onTogglePinAuto = { pid -> viewModel.toggleAndroidAutoPid(pid) },
                                onOpenSensorChart = { pid ->
                                    viewModel.selectChartPid(pid)
                                    navigateSingleTop(SensorsRoute)
                                },
                                onOpenAndroidAutoPreview = { showAaPreview = true }
                            )
                        }
                        composable<SensorsRoute> {
                            SensorsScreen(
                                uiState = uiState,
                                onSelectCategory = { viewModel.selectCategory(it) },
                                onSelectChartPid = { viewModel.selectChartPid(it) },
                                onTogglePinAuto = { viewModel.toggleAndroidAutoPid(it) },
                                onSetPollingMs = { viewModel.setPollingIntervalMs(it) },
                                onSetUseFahrenheit = { viewModel.setUseFahrenheit(it) },
                                onSetPressureUnit = { viewModel.setPressureUnit(it) }
                            )
                        }
                        composable<DiagnosticsRoute> {
                            DiagnosticsScreen(
                                uiState = uiState,
                                onScanDtcs = { viewModel.scanDtcs() },
                                onClearDtcs = { viewModel.clearDtcs() },
                                onClearHistory = { viewModel.clearScanHistory() }
                            )
                        }
                        composable<ConnectionRoute> {
                            ConnectionScreen(
                                uiState = uiState,
                                requiredPermissions = viewModel.requiredBluetoothPermissions(),
                                onPermissionsResult = { viewModel.refreshBluetoothDevices() },
                                onRefreshDevices = { viewModel.refreshBluetoothDevices() },
                                onStartBleScan = { viewModel.startBleScan() },
                                onStopBleScan = { viewModel.stopBleScan() },
                                onConnectDevice = { viewModel.connectToDevice(it) },
                                onStartSimulation = { viewModel.startSimulationMode() },
                                onDisconnect = { viewModel.disconnect() },
                                onToggleShowAllBt = { viewModel.toggleShowAllBtDevices() },
                                onToggleAndroidAutoPid = { viewModel.toggleAndroidAutoPid(it) },
                                onOpenAndroidAutoPreview = { showAaPreview = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(state: ObdConnectionState) {
    val (label, color) = when (state) {
        is ObdConnectionState.Connected -> "ONLINE" to NominalEmerald
        is ObdConnectionState.Simulation -> "СИМУЛЯЦИЯ" to MotorsportAmber
        is ObdConnectionState.Connecting -> "ПОИСК" to TelemetryCyan
        is ObdConnectionState.Error -> "ОШИБКА" to MaterialTheme.colorScheme.error
        is ObdConnectionState.Disconnected -> "ОФЛАЙН" to TextSecondary
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
