package com.example.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.ObdApplication
import com.example.R
import com.example.obd.i18n.AppLocalization
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.SensorCategory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class ObdMainCarScreen(
    carContext: CarContext,
    private val surfaceController: CockpitSurfaceController? = null
) : Screen(carContext) {

    companion object {
        private const val TAB_GAUGES = "tab_gauges"
        private const val TAB_SENSORS = "tab_sensors"
        private const val TAB_DTC = "tab_dtc"
        private const val TAB_CONNECT = "tab_connect"
    }

    private val app = carContext.applicationContext as ObdApplication
    private val repository = app.telemetryRepository

    private var activeTabId: String = TAB_GAUGES
    private var lastInvalidateMs: Long = 0L

    init {
        repository.sessionLogger.info("AUTO", "Открыт экран Android Auto (carApiLevel=${carContext.carAppApiLevel})")
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch {
                    val primaryFlow = combine(
                        repository.connectionState,
                        repository.readings,
                        repository.dtcState,
                        repository.settings,
                        repository.sensors
                    ) { _, _, _, _, _ -> Unit }
                    combine(
                        primaryFlow,
                        repository.maintenanceItems,
                        repository.vehicleProfiles
                    ) { _, _, _ -> Unit }.collectLatest {
                        val now = System.currentTimeMillis()
                        if (now - lastInvalidateMs >= 550L) {
                            lastInvalidateMs = now
                            invalidate()
                        }
                    }
                }
            }
        })
    }

    override fun onGetTemplate(): Template {
        return if (carContext.carAppApiLevel >= 6) {
            buildTabTemplate()
        } else {
            buildLegacyFallbackGridTemplate()
        }
    }

    private fun buildTabTemplate(): Template {
        val settings = repository.settings.value
        val l10nCtx = AppLocalization.localizedContext(carContext, settings.appLanguage)
        val activeContentTemplate = when (activeTabId) {
            TAB_SENSORS -> buildAllSensorsListTemplate(embedded = true)
            TAB_DTC -> buildDtcTemplate(embedded = true)
            TAB_CONNECT -> buildConnectionPaneTemplate(embedded = true)
            else -> buildGaugesGridTemplate(embedded = true)
        }

        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                activeTabId = tabContentId
                lastInvalidateMs = System.currentTimeMillis()
                invalidate()
            }
        })
            .setHeaderAction(Action.APP_ICON)
            .setTabContents(TabContents.Builder(activeContentTemplate).build())
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_GAUGES)
                    .setTitle(l10nCtx.getString(R.string.nav_gauges))
                    .setIcon(carIcon(R.drawable.ic_car_gauges))
                    .build()
            )
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_SENSORS)
                    .setTitle(l10nCtx.getString(R.string.nav_sensors))
                    .setIcon(carIcon(R.drawable.ic_car_sensors))
                    .build()
            )
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_DTC)
                    .setTitle(l10nCtx.getString(R.string.nav_diagnostics))
                    .setIcon(carIcon(R.drawable.ic_car_dtc))
                    .build()
            )
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_CONNECT)
                    .setTitle(l10nCtx.getString(R.string.nav_obd2))
                    .setIcon(carIcon(R.drawable.ic_car_connect))
                    .build()
            )
            .setActiveTabContentId(activeTabId)
            .build()
    }

    private fun buildLegacyFallbackGridTemplate(): Template {
        return buildGaugesGridTemplate(embedded = false)
    }

    private fun buildGaugesGridTemplate(embedded: Boolean): Template {
        val settings = repository.settings.value
        val isEng = settings.isEnglish
        val l10nCtx = AppLocalization.localizedContext(carContext, settings.appLanguage)
        val readings = repository.readings.value
        val allSensors = repository.sensors.value
        val conn = repository.connectionState.value
        val gridLimit = getConstraintLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID, 6)
        val pinnedSensors = settings.androidAutoSensorIds
            .mapNotNull { id -> allSensors.find { it.id == id } }
            .take(gridLimit)
            .ifEmpty { allSensors.take(gridLimit) }

        val itemListBuilder = ItemList.Builder()
        for (sensor in pinnedSensors) {
            val reading = readings[sensor.id]
            val valueStr = reading?.let {
                "${sensor.formatValue(it.value, settings.useFahrenheit, settings.pressureUnit, isEng)} ${sensor.displayUnit(settings.useFahrenheit, settings.pressureUnit, isEng)}"
            } ?: l10nCtx.getString(R.string.label_no_data)

            val gaugeIcon = CarGaugeBitmapFactory.createGaugeIcon(
                sensor = sensor,
                reading = reading,
                useFahrenheit = settings.useFahrenheit,
                pressureUnit = settings.pressureUnit,
                isEnglish = isEng
            )

            itemListBuilder.addItem(
                GridItem.Builder()
                    .setTitle(sensor.localizedShortName(isEng))
                    .setText(valueStr)
                    .setImage(gaugeIcon, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        screenManager.push(SensorDetailCarScreen(carContext, sensor.id))
                    }
                    .build()
            )
        }

        val builder = GridTemplate.Builder()
            .setSingleList(itemListBuilder.build())

        if (!embedded) {
            val title = when (conn) {
                is ObdConnectionState.Simulation -> l10nCtx.getString(R.string.car_aa_sim_title)
                is ObdConnectionState.Connected -> "OBD2: ${conn.deviceName}"
                else -> l10nCtx.getString(R.string.app_name)
            }
            val headerBuilder = Header.Builder()
                .setTitle(title)
                .setStartHeaderAction(Action.APP_ICON)
                .addEndHeaderAction(
                    Action.Builder()
                        .setTitle(l10nCtx.getString(R.string.nav_diagnostics))
                        .setOnClickListener {
                            screenManager.push(LegacySectionCarScreen(carContext, TAB_DTC))
                        }
                        .build()
                )
                .addEndHeaderAction(
                    Action.Builder()
                        .setTitle(l10nCtx.getString(R.string.nav_obd2))
                        .setOnClickListener {
                            screenManager.push(LegacySectionCarScreen(carContext, TAB_CONNECT))
                        }
                        .build()
                )
            builder.setHeader(headerBuilder.build())
        }

        return builder.build()
    }

    private inner class LegacySectionCarScreen(
        carContext: CarContext,
        private val sectionId: String
    ) : Screen(carContext) {
        override fun onGetTemplate(): Template {
            return when (sectionId) {
                TAB_SENSORS -> buildAllSensorsListTemplate(embedded = false)
                TAB_DTC -> buildDtcTemplate(embedded = false)
                else -> buildConnectionPaneTemplate(embedded = false)
            }
        }
    }

    private fun buildAllSensorsListTemplate(embedded: Boolean): Template {
        val settings = repository.settings.value
        val isEng = settings.isEnglish
        val l10nCtx = AppLocalization.localizedContext(carContext, settings.appLanguage)
        val readings = repository.readings.value
        val allSensors = repository.sensors.value
        val listLimit = getConstraintLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6)

        val templateBuilder = ListTemplate.Builder()
        var totalAdded = 0

        if (surfaceController != null && totalAdded + 2 <= listLimit) {
            val presetName = settings.activePreset.localizedName(isEng)
            val modeRowList = ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle(l10nCtx.getString(R.string.car_fullscreen_row_title))
                        .addText(l10nCtx.getString(R.string.car_fullscreen_row_sub))
                        .setOnClickListener {
                            launchFullscreenCockpit()
                        }
                        .build()
                )
                .addItem(
                    Row.Builder()
                        .setTitle(l10nCtx.getString(R.string.car_preset_switch_title, presetName))
                        .addText(l10nCtx.getString(R.string.car_preset_switch_sub))
                        .setOnClickListener {
                            repository.cycleNextCockpitPreset()
                        }
                        .build()
                )
                .build()
            templateBuilder.addSectionedList(
                SectionedItemList.create(modeRowList, l10nCtx.getString(R.string.car_screen_mode_section))
            )
            totalAdded += 2
        } else if (totalAdded < listLimit) {
            val presetName = settings.activePreset.localizedName(isEng)
            val presetList = ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle(l10nCtx.getString(R.string.car_preset_switch_title, presetName))
                        .addText(l10nCtx.getString(R.string.car_preset_switch_sub))
                        .setOnClickListener {
                            repository.cycleNextCockpitPreset()
                        }
                        .build()
                )
                .build()
            templateBuilder.addSectionedList(
                SectionedItemList.create(presetList, l10nCtx.getString(R.string.car_screen_mode_section))
            )
            totalAdded++
        }

        val orderedCategories = listOf(SensorCategory.CUSTOM) +
            SensorCategory.entries.filter { it != SensorCategory.CUSTOM }

        for (category in orderedCategories) {
            if (totalAdded >= listLimit) break
            val categorySensors = allSensors.filter { it.category == category }
            val sectionList = ItemList.Builder()
            var sectionCount = 0

            for (sensor in categorySensors) {
                if (totalAdded >= listLimit) break
                val reading = readings[sensor.id]
                val unit = sensor.displayUnit(settings.useFahrenheit, settings.pressureUnit, isEng)
                val formatted = reading?.let {
                    "${sensor.formatValue(it.value, settings.useFahrenheit, settings.pressureUnit, isEng)} $unit"
                } ?: "-- $unit"

                val statusSuffix = when (reading?.alertLevel) {
                    AlertLevel.CRITICAL -> l10nCtx.getString(R.string.car_crit_badge)
                    AlertLevel.WARNING -> l10nCtx.getString(R.string.car_warn_badge)
                    else -> if (sensor.id in settings.androidAutoSensorIds) {
                        l10nCtx.getString(R.string.car_on_screen_badge)
                    } else ""
                }

                val icon = CarGaugeBitmapFactory.createGaugeIcon(
                    sensor = sensor,
                    reading = reading,
                    useFahrenheit = settings.useFahrenheit,
                    pressureUnit = settings.pressureUnit,
                    sizePx = 128,
                    isEnglish = isEng
                )

                sectionList.addItem(
                    Row.Builder()
                        .setTitle("${sensor.localizedDisplayName(isEng)}: $formatted")
                        .addText("PID ${sensor.modeAndPid}$statusSuffix")
                        .setImage(icon, Row.IMAGE_TYPE_SMALL)
                        .setOnClickListener {
                            screenManager.push(SensorDetailCarScreen(carContext, sensor.id))
                        }
                        .build()
                )
                sectionCount++
                totalAdded++
            }

            if (sectionCount > 0) {
                templateBuilder.addSectionedList(
                    SectionedItemList.create(sectionList.build(), category.localizedName(isEng))
                )
            }
        }

        if (!embedded) {
            templateBuilder.setHeader(
                Header.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_all_sensors_title))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        }

        return templateBuilder.build()
    }

    private fun buildDtcTemplate(embedded: Boolean): Template {
        val settings = repository.settings.value
        val isEng = settings.isEnglish
        val l10nCtx = AppLocalization.localizedContext(carContext, settings.appLanguage)
        val dtc = repository.dtcState.value
        val listLimit = getConstraintLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6)
        val itemList = ItemList.Builder()
        var addedRows = 0

        val milTitle = if (dtc.milOn) {
            l10nCtx.getString(R.string.mil_active)
        } else {
            l10nCtx.getString(R.string.mil_normal)
        }
        val milSubtitle = dtc.statusMessage ?: l10nCtx.getString(R.string.dtc_scan_hint)
        itemList.addItem(
            Row.Builder()
                .setTitle(milTitle)
                .addText(milSubtitle)
                .build()
        )
        addedRows++

        if (addedRows < listLimit) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_ai_summary_title))
                    .addText(l10nCtx.getString(R.string.car_ai_summary_sub))
                    .setOnClickListener {
                        repository.speakCarStatusSummary()
                    }
                    .build()
            )
            addedRows++
        }

        // Maintenance overdue / due-soon alert row
        val activeProfile = repository.vehicleProfiles.value.find {
            it.id == settings.activeVehicleProfileId
        } ?: repository.vehicleProfiles.value.firstOrNull()
        val odo = activeProfile?.odometerKm ?: 0f
        val urgentMaint = repository.maintenanceItems.value
            .map { item ->
                val remKm = (item.intervalKm - (odo - item.lastServiceOdometerKm).coerceAtLeast(0f)).toInt()
                item to remKm
            }
            .minByOrNull { it.second }
        if (urgentMaint != null && urgentMaint.second <= 1000 && addedRows < listLimit) {
            val (mItem, remKm) = urgentMaint
            val sub = if (remKm <= 0) {
                l10nCtx.getString(R.string.car_maint_due_sub, kotlin.math.abs(remKm))
            } else {
                l10nCtx.getString(R.string.car_maint_soon_sub, remKm)
            }
            itemList.addItem(
                Row.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_maint_overdue_title, mItem.title))
                    .addText(sub)
                    .build()
            )
            addedRows++
        }

        if (addedRows < listLimit) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(
                        if (dtc.isScanning) l10nCtx.getString(R.string.car_dtc_scanning)
                        else l10nCtx.getString(R.string.car_dtc_read_title)
                    )
                    .addText(l10nCtx.getString(R.string.car_dtc_read_sub))
                    .setOnClickListener {
                        repository.scanDtcs()
                    }
                    .build()
            )
            addedRows++
        }

        if (dtc.codes.isNotEmpty() && addedRows < listLimit) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_dtc_clear_title))
                    .addText(l10nCtx.getString(R.string.car_dtc_clear_sub))
                    .setOnClickListener {
                        screenManager.push(DtcConfirmClearCarScreen(carContext))
                    }
                    .build()
            )
            addedRows++
        }

        val remainingSlots = (listLimit - addedRows).coerceAtLeast(0)
        for (code in dtc.codes.take(remainingSlots)) {
            itemList.addItem(
                Row.Builder()
                    .setTitle("${code.code} • ${code.severity.localizedLabel(isEng)}")
                    .addText(code.description)
                    .build()
            )
        }

        val builder = ListTemplate.Builder().setSingleList(itemList.build())
        if (!embedded) {
            builder.setHeader(
                Header.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_diagnostics_title))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        }
        return builder.build()
    }

    private fun buildConnectionPaneTemplate(embedded: Boolean): Template {
        val conn = repository.connectionState.value
        val settings = repository.settings.value
        val isEng = settings.isEnglish
        val l10nCtx = AppLocalization.localizedContext(carContext, settings.appLanguage)
        val locale = if (isEng) Locale.US else Locale.forLanguageTag("ru-RU")

        val statusTitle = when (conn) {
            is ObdConnectionState.Connected -> l10nCtx.getString(R.string.banner_connected_title, conn.deviceName)
            is ObdConnectionState.Connecting -> l10nCtx.getString(R.string.banner_connecting_title, conn.deviceName)
            is ObdConnectionState.Simulation -> l10nCtx.getString(R.string.banner_sim_title)
            is ObdConnectionState.Error -> l10nCtx.getString(R.string.banner_error_title)
            is ObdConnectionState.Disconnected -> l10nCtx.getString(R.string.banner_disconnected_title)
        }

        val statusDetail = when (conn) {
            is ObdConnectionState.Connected ->
                l10nCtx.getString(
                    R.string.car_conn_proto,
                    conn.protocol,
                    String.format(locale, "%.1f", conn.pollingHz)
                )
            is ObdConnectionState.Connecting -> conn.step
            is ObdConnectionState.Simulation ->
                l10nCtx.getString(
                    R.string.car_conn_sim_sub,
                    String.format(locale, "%.1f", conn.pollingHz)
                )
            is ObdConnectionState.Error -> conn.message
            is ObdConnectionState.Disconnected ->
                l10nCtx.getString(R.string.car_conn_disconn_sub)
        }

        val activeProfile = repository.vehicleProfiles.value.find {
            it.id == settings.activeVehicleProfileId
        } ?: repository.vehicleProfiles.value.firstOrNull()
        val presetName = settings.activePreset.localizedName(isEng)

        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(statusTitle)
                    .addText(statusDetail)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(
                        l10nCtx.getString(
                            R.string.car_vehicle_row_title,
                            activeProfile?.name ?: "OBD2"
                        )
                    )
                    .addText(l10nCtx.getString(R.string.car_preset_switch_title, presetName))
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_sensors_count, settings.androidAutoSensorIds.size))
                    .addText(
                        settings.androidAutoSensorIds.joinToString(", ") { id ->
                            repository.sensors.value.find { it.id == id }?.localizedShortName(isEng) ?: id
                        }
                    )
                    .build()
            )

        var actionCount = 0
        if (!repository.bluetoothScanner.hasBluetoothPermissions()) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_allow_bt))
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener {
                        carContext.requestPermissions(
                            repository.bluetoothScanner.requiredPermissions().toList()
                        ) { _, _ -> invalidate() }
                    }
                    .build()
            )
            actionCount++
        } else if (conn.isActiveTelemetry) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle(l10nCtx.getString(R.string.action_disconnect))
                    .setOnClickListener { repository.disconnect() }
                    .build()
            )
            actionCount++
        } else {
            val lastAddr = settings.lastDeviceAddress
            val lastName = settings.lastDeviceName
            val targetDevice = if (lastAddr != null && lastName != null) {
                com.example.obd.model.BluetoothDeviceItem(
                    name = lastName,
                    address = lastAddr,
                    transport = settings.lastTransport,
                    isPaired = true,
                    isLikelyObd = true
                )
            } else {
                repository.bluetoothScanner.findPreferredPairedObdDevice()
            }

            if (targetDevice != null) {
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle(l10nCtx.getString(R.string.car_connect_prefix, targetDevice.name))
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener {
                            repository.connectToDevice(targetDevice)
                        }
                        .build()
                )
                actionCount++
            }
            if (actionCount < 2) {
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle(l10nCtx.getString(R.string.action_sim_mode))
                        .setOnClickListener { repository.startSimulationMode() }
                        .build()
                )
                actionCount++
            }
        }

        if (surfaceController != null && actionCount < 2) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_fullscreen_btn))
                    .setOnClickListener { launchFullscreenCockpit() }
                    .build()
            )
            actionCount++
        }
        if (actionCount < 2) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_ai_summary_title))
                    .setOnClickListener { repository.speakCarStatusSummary() }
                    .build()
            )
        }

        val builder = PaneTemplate.Builder(paneBuilder.build())
        if (!embedded) {
            builder.setHeader(
                Header.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_connection_title))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        }
        return builder.build()
    }

    private fun launchFullscreenCockpit() {
        val controller = surfaceController ?: return
        repository.setAndroidAutoDisplayMode(
            com.example.obd.data.AndroidAutoDisplayMode.FULLSCREEN_COCKPIT
        )
        screenManager.push(
            FullscreenCockpitCarScreen(
                carContext = carContext,
                surfaceController = controller,
                isPushedOnTopOfMenu = true
            )
        )
    }

    private fun getConstraintLimit(limitType: Int, fallback: Int): Int {
        return runCatching {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(limitType)
        }.getOrDefault(fallback)
    }

    private fun carIcon(resId: Int): CarIcon {
        return CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
    }
}
