package com.example

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.obd.ai.configureAppCheck
import com.example.obd.i18n.AppLocalization
import com.example.obd.i18n.LocalL10nContext
import com.example.obd.i18n.l10nStr
import com.example.obd.model.ObdConnectionState
import com.example.ui.ObdViewModel
import com.example.ui.components.AndroidAutoPreviewDialog
import com.example.ui.components.GarageManagerDialog
import com.example.ui.components.HudOverlayDialog
import com.example.ui.components.ProUpgradeDialog
import com.example.ui.components.SafetyDisclaimerDialog
import com.example.ui.components.VoiceAssistantDialog
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.SensorsScreen
import com.example.ui.screens.TripAndPerformanceScreen
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
@Serializable data object TripRoute
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
        configureAppCheck(this, intent)
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
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val l10nCtx = remember(context, uiState.settings.appLanguage) {
        AppLocalization.localizedContext(context, uiState.settings.appLanguage)
    }

    CompositionLocalProvider(LocalL10nContext provides l10nCtx) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        var showAaPreview by remember { mutableStateOf(false) }
        var showHudOverlay by remember { mutableStateOf(false) }
        var showGarageDialog by remember { mutableStateOf(false) }
        var showSafetyManualDialog by remember { mutableStateOf(false) }
        var showProDialog by remember { mutableStateOf(false) }
        var showVoiceDialog by remember { mutableStateOf(false) }
        val lifecycleOwner = LocalLifecycleOwner.current

        fun requirePro(action: () -> Unit) {
            if (uiState.hasProAccess) {
                action()
            } else {
                showProDialog = true
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshBilling()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(Unit) {
            viewModel.attemptSilentCloudSignIn(context)
        }

        val startupPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            viewModel.logInfo("PERM", "Initial permission request result: $results")
            viewModel.refreshBluetoothDevices()
        }

        LaunchedEffect(
            uiState.settingsLoaded,
            uiState.settings.hasRequestedStartupPermissions,
            uiState.hasBluetoothPermissions,
            uiState.isBluetoothEnabled
        ) {
            if (!uiState.settingsLoaded) return@LaunchedEffect
            if (!uiState.settings.hasRequestedStartupPermissions) {
                viewModel.markStartupPermissionsRequested()
                val perms = viewModel.startupPermissions()
                val missing = perms.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    viewModel.logInfo("PERM", "First-launch permission request: $missing")
                    startupPermissionLauncher.launch(missing.toTypedArray())
                    return@LaunchedEffect
                }
            }
            val reason = if (uiState.settings.isEnglish) "App launch" else "Запуск приложения"
            viewModel.tryAutoConnect(reason)
        }

        if (showAaPreview) {
            AndroidAutoPreviewDialog(
                uiState = uiState,
                onDismiss = { showAaPreview = false },
                onSelectDisplayMode = { viewModel.setAndroidAutoDisplayMode(it) },
                onCyclePreset = { viewModel.cycleNextCockpitPreset() },
                onSetDialSlot = { idx, id -> viewModel.setFullscreenDialSlot(idx, id) },
                onSetStripSlot = { idx, id -> viewModel.setFullscreenStripSlot(idx, id) },
                onTogglePinAuto = { viewModel.toggleAndroidAutoSensor(it) },
                onScanDtcs = { viewModel.scanDtcs() },
                onStartSimulation = { viewModel.startSimulationMode() },
                onDisconnect = { viewModel.disconnect() }
            )
        }

        if (showHudOverlay) {
            HudOverlayDialog(
                uiState = uiState,
                onDismiss = { showHudOverlay = false }
            )
        }

        if (showVoiceDialog) {
            VoiceAssistantDialog(
                state = uiState.voiceAssistantState,
                isEnglish = uiState.settings.isEnglish,
                onAskQuestion = { question, speak ->
                    viewModel.askVoiceDriverQuestion(question, speak)
                },
                onDismiss = { showVoiceDialog = false }
            )
        }

        if (showGarageDialog) {
            GarageManagerDialog(
                uiState = uiState,
                onSelectProfile = { viewModel.selectVehicleProfile(it) },
                onSaveProfile = { existingId, name, vin, disp, fuel, redline, shift, price, preset, odo, curbWeightKg ->
                    viewModel.saveVehicleProfile(
                        existingId = existingId,
                        name = name,
                        vin = vin,
                        displacementLiters = disp,
                        fuelType = fuel,
                        redlineRpm = redline,
                        shiftLightRpm = shift,
                        fuelPricePerLiter = price,
                        preferredPresetId = preset,
                        odometerKm = odo,
                        curbWeightKg = curbWeightKg
                    )
                },
                onDeleteProfile = { viewModel.deleteVehicleProfile(it) },
                onRequestAiSuggestion = { vin, query ->
                    requirePro { viewModel.requestAiVehicleSuggestion(vin, query) }
                },
                onClearAiSuggestion = { viewModel.clearAiVehicleSuggestion() },
                onApplyAiSuggestionExtras = { suggestion ->
                    viewModel.applyAiSuggestionExtras(suggestion)
                },
                onDismiss = { showGarageDialog = false }
            )
        }

        if ((uiState.settingsLoaded && !uiState.settings.hasAcceptedSafetyDisclaimer) || showSafetyManualDialog) {
            SafetyDisclaimerDialog(
                onAccept = {
                    viewModel.acceptSafetyDisclaimer()
                    showSafetyManualDialog = false
                }
            )
        }

        if (showProDialog) {
            ProUpgradeDialog(
                isEnglish = uiState.settings.isEnglish,
                isProPreviewEnabled = uiState.settings.isProPreviewEnabled,
                billingUiState = uiState.billingUiState,
                onToggleProPreview = { viewModel.setProPreviewEnabled(it) },
                onPurchaseSku = { act, sku -> viewModel.launchProPurchase(act, sku) },
                onDismiss = { showProDialog = false }
            )
        }

        val destinations = listOf(
            NavDestinationItem(
                route = DashboardRoute,
                label = l10nStr(R.string.nav_gauges),
                selectedIcon = Icons.Filled.Speed,
                unselectedIcon = Icons.Outlined.Speed,
                testTag = "nav_dashboard"
            ),
            NavDestinationItem(
                route = SensorsRoute,
                label = l10nStr(R.string.nav_sensors),
                selectedIcon = Icons.Filled.ShowChart,
                unselectedIcon = Icons.Outlined.ShowChart,
                testTag = "nav_sensors"
            ),
            NavDestinationItem(
                route = TripRoute,
                label = l10nStr(R.string.nav_trip),
                selectedIcon = Icons.Filled.Timer,
                unselectedIcon = Icons.Outlined.Timer,
                testTag = "nav_trip"
            ),
            NavDestinationItem(
                route = DiagnosticsRoute,
                label = l10nStr(R.string.nav_diagnostics),
                selectedIcon = Icons.Filled.Build,
                unselectedIcon = Icons.Outlined.Build,
                testTag = "nav_diagnostics"
            ),
            NavDestinationItem(
                route = ConnectionRoute,
                label = l10nStr(R.string.nav_obd2),
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
                                    text = l10nStr(R.string.app_name),
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
                                onTogglePinAuto = { sensorId -> viewModel.toggleAndroidAutoSensor(sensorId) },
                                onOpenSensorChart = { sensorId ->
                                    viewModel.selectChartSensor(sensorId)
                                    navigateSingleTop(SensorsRoute)
                                },
                                onOpenAndroidAutoPreview = { showAaPreview = true },
                                onOpenHudMode = { requirePro { showHudOverlay = true } },
                                onOpenVoiceAssistant = { requirePro { showVoiceDialog = true } },
                                onDismissAlert = { viewModel.dismissActiveAlert() }
                            )
                        }
                        composable<SensorsRoute> {
                            SensorsScreen(
                                uiState = uiState,
                                onSelectCategory = { viewModel.selectCategory(it) },
                                onSelectChartSensor = { viewModel.selectChartSensor(it) },
                                onTogglePinAuto = { viewModel.toggleAndroidAutoSensor(it) },
                                onSetPollingMs = { viewModel.setPollingIntervalMs(it) },
                                onSetUseFahrenheit = { viewModel.setUseFahrenheit(it) },
                                onSetPressureUnit = { viewModel.setPressureUnit(it) },
                                onAddCustomPid = { name, shortName, modeAndPid, header, equation, unit, minV, maxV, dec, warnH, critH, warnL, critL, alertsEn ->
                                    requirePro {
                                        viewModel.addCustomPid(
                                            name, shortName, modeAndPid, header, equation, unit,
                                            minV, maxV, dec, warnH, critH, warnL, critL, alertsEn
                                        )
                                    }
                                },
                                onUpdateCustomPid = { pid -> requirePro { viewModel.updateCustomPid(pid) } },
                                onDeleteCustomPid = { id -> requirePro { viewModel.deleteCustomPid(id) } },
                                onImportTorqueCsv = { csv, onComplete ->
                                    requirePro { viewModel.importTorqueCsv(csv, onComplete) }
                                },
                                onTestLivePid = { modeAndPid, header, equation ->
                                    viewModel.probeCustomPid(modeAndPid, header, equation)
                                },
                                onSetSensorAlertOverride = { viewModel.setSensorAlertOverride(it) },
                                onStartDatalogger = { requirePro { viewModel.startDatalogger() } },
                                onStopDatalogger = { viewModel.stopDatalogger() },
                                onClearDatalogger = { viewModel.clearDatalogger() },
                                onBuildDataloggerCsv = {
                                    if (uiState.hasProAccess) {
                                        viewModel.buildDataloggerCsv()
                                    } else {
                                        showProDialog = true
                                        ""
                                    }
                                },
                                onImportOemPack = { packId, onComplete ->
                                    requirePro { viewModel.importOemPack(packId, onComplete) }
                                }
                            )
                        }
                        composable<TripRoute> {
                            TripAndPerformanceScreen(
                                uiState = uiState,
                                onOpenGarage = { showGarageDialog = true },
                                onOpenHud = { requirePro { showHudOverlay = true } },
                                onResetTrip = { viewModel.resetTrip() },
                                onSaveTrip = { viewModel.saveCurrentTripToHistory() },
                                onDeleteTrip = { viewModel.deleteTripRecord(it) },
                                onClearTrips = { viewModel.clearTripHistory() },
                                onBuildTripsCsv = { trips -> viewModel.buildTripsCsv(trips) },
                                onSelectAccelDiscipline = { viewModel.selectAccelDiscipline(it) },
                                onArmAccelTimer = { requirePro { viewModel.armAccelTimer() } },
                                onResetAccelTimer = { viewModel.resetAccelTimer() },
                                onClearAccelRuns = { viewModel.clearAccelRuns() },
                                onSetAutoSaveTrip = { viewModel.setAutoSaveTripOnDisconnect(it) },
                                onSetFuelCalibrationFactor = { viewModel.setFuelCalibrationFactor(it) },
                                onSetSpeedCalibrationFactor = { viewModel.setSpeedCalibrationFactor(it) },
                                onApplyFuelFillup = { actual, comp, cb ->
                                    viewModel.applyFuelFillupCalibration(actual, comp, cb)
                                },
                                onStartGpsSpeedCheck = { requirePro { viewModel.startGpsSpeedCheck() } },
                                onStopGpsSpeedCheck = { viewModel.stopGpsSpeedCheck() },
                                onApplySuggestedGpsFactor = { viewModel.applySuggestedGpsSpeedFactor() },
                                onAddMaintenanceItem = { title, cat, intKm, intHrs, notes ->
                                    requirePro { viewModel.addMaintenanceItem(title, cat, intKm, intHrs, notes) }
                                },
                                onMarkMaintenanceDone = { viewModel.markMaintenanceDone(it) },
                                onDeleteMaintenanceItem = { viewModel.deleteMaintenanceItem(it) },
                                onAnalyzePhoto = { uri, mode -> requirePro { viewModel.analyzePhoto(uri, mode) } },
                                onClearPhotoAnalysis = { viewModel.clearPhotoAnalysis() },
                                onUpdateFuelPrice = { newPrice ->
                                    val p = uiState.activeVehicleProfile
                                    if (p != null) {
                                        viewModel.saveVehicleProfile(
                                            existingId = p.id,
                                            name = p.name,
                                            vin = p.vin,
                                            displacementLiters = p.displacementLiters,
                                            fuelType = p.fuelTypeEnum,
                                            redlineRpm = p.redlineRpm,
                                            shiftLightRpm = p.shiftLightRpm,
                                            fuelPricePerLiter = newPrice,
                                            preferredPresetId = p.preferredPresetId,
                                            odometerKm = p.odometerKm,
                                            curbWeightKg = p.curbWeightKg
                                        )
                                    }
                                }
                            )
                        }
                        composable<DiagnosticsRoute> {
                            DiagnosticsScreen(
                                uiState = uiState,
                                onScanDtcs = { viewModel.scanDtcs() },
                                onScanFreezeFrame = { viewModel.scanFreezeFrame() },
                                onClearDtcs = { viewModel.clearDtcs() },
                                onRunAiFaultAnalysis = { requirePro { viewModel.runAiFaultAnalysis() } },
                                onClearAiAnalysis = { viewModel.clearAiAnalysis() },
                                onOpenVoiceAssistant = { requirePro { showVoiceDialog = true } },
                                onClearHistory = { viewModel.clearScanHistory() },
                                onBuildErrorReportText = { viewModel.buildErrorReportText() },
                                onCreateShareableReportFile = { viewModel.createShareableReportFile() },
                                onClearLogs = { viewModel.clearLogs() },
                                onRunPrePurchaseInspection = { requirePro { viewModel.runPrePurchaseInspection() } },
                                onCreatePrePurchasePdfFile = {
                                    if (uiState.hasProAccess) {
                                        viewModel.createPrePurchasePdfFile()
                                    } else {
                                        showProDialog = true
                                        null
                                    }
                                },
                                onAnalyzePhoto = { uri, mode -> requirePro { viewModel.analyzePhoto(uri, mode) } },
                                onClearPhotoAnalysis = { viewModel.clearPhotoAnalysis() }
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
                                onConnectWifi = { host, port -> viewModel.connectWifi(host, port) },
                                onStartSimulation = { viewModel.startSimulationMode() },
                                onDisconnect = { viewModel.disconnect() },
                                onToggleShowAllBt = { viewModel.toggleShowAllBtDevices() },
                                onToggleAndroidAutoSensor = { viewModel.toggleAndroidAutoSensor(it) },
                                onSelectDisplayMode = { viewModel.setAndroidAutoDisplayMode(it) },
                                onSelectDialTheme = { viewModel.setDialTheme(it) },
                                onSetShiftLightEnabled = { viewModel.setShiftLightEnabled(it) },
                                onSetShiftLightRpm = { viewModel.setShiftLightRpm(it) },
                                onSelectPreset = { viewModel.selectCockpitPreset(it) },
                                onRenamePreset = { id, name -> viewModel.renameCockpitPreset(id, name) },
                                onSetDialSlot = { idx, id -> viewModel.setFullscreenDialSlot(idx, id) },
                                onSetStripSlot = { idx, id -> viewModel.setFullscreenStripSlot(idx, id) },
                                onResetFullscreenDefaults = { viewModel.resetFullscreenLayoutDefaults() },
                                onSetAutoConnect = { viewModel.setAutoConnectOnLaunch(it) },
                                onSetAutoReconnect = { viewModel.setAutoReconnectOnDrop(it) },
                                onSetVoiceAlerts = { viewModel.setVoiceAlertsEnabled(it) },
                                onSetAaPopupAlerts = { viewModel.setAaPopupAlertsEnabled(it) },
                                onSetTtsVolume = { viewModel.setTtsVolume(it) },
                                onSetTtsSpeechRate = { viewModel.setTtsSpeechRate(it) },
                                onSetTtsVoiceName = { viewModel.setTtsVoiceName(it) },
                                onPreloadTtsVoices = { viewModel.preloadTtsVoices() },
                                onTriggerTestAlert = { viewModel.triggerTestAlert() },
                                onRunAdapterBenchmark = { viewModel.runAdapterBenchmark() },
                                onOpenAndroidAutoPreview = { showAaPreview = true },
                                onOpenGarage = { showGarageDialog = true },
                                onExportConfigBackupJson = { viewModel.exportConfigBackupJson() },
                                onImportConfigBackupJson = { json, cb ->
                                    viewModel.importConfigBackupJson(json, cb)
                                },
                                onOpenSafetyDialog = { showSafetyManualDialog = true },
                                onOpenProDialog = { showProDialog = true },
                                onOpenErrorLogs = { navigateSingleTop(DiagnosticsRoute) },
                                onSetAppLanguage = { viewModel.setAppLanguage(it) },
                                onRefreshUsbDevices = { viewModel.refreshUsbDevices() },
                                onRequestUsbPermission = { viewModel.requestUsbPermission(it) },
                                onConnectUsbDevice = { viewModel.connectUsbDevice(it) },
                                onSignInGoogle = { ctx -> requirePro { viewModel.signInWithGoogle(ctx) } },
                                onSyncCloudNow = { requirePro { viewModel.syncCloudNow() } },
                                onSignOutCloud = { viewModel.signOutCloud() },
                                onSetFastSlowPolling = { viewModel.setFastSlowPollingEnabled(it) },
                                onSetAutoConnectBtCar = { viewModel.setAutoConnectOnBtCar(it) },
                                onStartBatteryTest = { viewModel.startBatteryTest() },
                                onCancelBatteryTest = { viewModel.cancelBatteryTest() },
                                onSendTerminalCommand = { cmd -> requirePro { viewModel.sendRawElmCommand(cmd) } },
                                onClearTerminal = { viewModel.clearTerminal() }
                            )
                        }
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
        is ObdConnectionState.Connected -> l10nStr(R.string.status_pill_online) to NominalEmerald
        is ObdConnectionState.Simulation -> l10nStr(R.string.status_pill_sim) to MotorsportAmber
        is ObdConnectionState.Connecting -> l10nStr(R.string.status_pill_connecting) to TelemetryCyan
        is ObdConnectionState.Error -> l10nStr(R.string.status_pill_error) to MaterialTheme.colorScheme.error
        is ObdConnectionState.Disconnected -> l10nStr(R.string.status_pill_offline) to TextSecondary
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
