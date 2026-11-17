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
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.SensorCategory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ObdMainCarScreen(carContext: CarContext) : Screen(carContext) {

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
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch {
                    combine(
                        repository.connectionState,
                        repository.readings,
                        repository.dtcState,
                        repository.settings
                    ) { _, _, _, _ -> Unit }.collectLatest {
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
                    .setTitle("Приборы")
                    .setIcon(carIcon(R.drawable.ic_car_gauges))
                    .build()
            )
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_SENSORS)
                    .setTitle("Датчики")
                    .setIcon(carIcon(R.drawable.ic_car_sensors))
                    .build()
            )
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_DTC)
                    .setTitle("Ошибки")
                    .setIcon(carIcon(R.drawable.ic_car_dtc))
                    .build()
            )
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_CONNECT)
                    .setTitle("OBD2")
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
        val readings = repository.readings.value
        val conn = repository.connectionState.value
        val gridLimit = getConstraintLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID, 6)
        val pids = settings.androidAutoPids.take(gridLimit)

        val itemListBuilder = ItemList.Builder()
        for (pid in pids) {
            val reading = readings[pid]
            val valueStr = reading?.let {
                "${pid.formatValue(it.value, settings.useFahrenheit, settings.pressureUnit)} ${pid.displayUnit(settings.useFahrenheit, settings.pressureUnit)}"
            } ?: "Нет данных"

            val gaugeIcon = CarGaugeBitmapFactory.createGaugeIcon(
                pid = pid,
                reading = reading,
                useFahrenheit = settings.useFahrenheit,
                pressureUnit = settings.pressureUnit
            )

            itemListBuilder.addItem(
                GridItem.Builder()
                    .setTitle(pid.shortName)
                    .setText(valueStr)
                    .setImage(gaugeIcon, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        screenManager.push(SensorDetailCarScreen(carContext, pid))
                    }
                    .build()
            )
        }

        val builder = GridTemplate.Builder()
            .setSingleList(itemListBuilder.build())

        if (!embedded) {
            val title = when (conn) {
                is ObdConnectionState.Simulation -> "OBD2 Авто (Симуляция)"
                is ObdConnectionState.Connected -> "OBD2: ${conn.deviceName}"
                else -> "OBD2 Авто"
            }
            val headerBuilder = Header.Builder()
                .setTitle(title)
                .setStartHeaderAction(Action.APP_ICON)
                .addEndHeaderAction(
                    Action.Builder()
                        .setTitle("Ошибки")
                        .setOnClickListener {
                            screenManager.push(LegacySectionCarScreen(carContext, TAB_DTC))
                        }
                        .build()
                )
                .addEndHeaderAction(
                    Action.Builder()
                        .setTitle("OBD2")
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
        val readings = repository.readings.value
        val listLimit = getConstraintLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6)

        val templateBuilder = ListTemplate.Builder()
        var totalAdded = 0

        for (category in SensorCategory.entries) {
            if (totalAdded >= listLimit) break
            val categoryPids = ObdPid.entries.filter { it.category == category }
            val sectionList = ItemList.Builder()
            var sectionCount = 0

            for (pid in categoryPids) {
                if (totalAdded >= listLimit) break
                val reading = readings[pid]
                val formatted = reading?.let {
                    "${pid.formatValue(it.value, settings.useFahrenheit, settings.pressureUnit)} ${pid.displayUnit(settings.useFahrenheit, settings.pressureUnit)}"
                } ?: "-- ${pid.displayUnit(settings.useFahrenheit, settings.pressureUnit)}"

                val statusSuffix = when (reading?.alertLevel) {
                    AlertLevel.CRITICAL -> " • КРИТИЧНО"
                    AlertLevel.WARNING -> " • ВНИМАНИЕ"
                    else -> if (pid in settings.androidAutoPids) " • На экране" else ""
                }

                val icon = CarGaugeBitmapFactory.createGaugeIcon(
                    pid = pid,
                    reading = reading,
                    useFahrenheit = settings.useFahrenheit,
                    pressureUnit = settings.pressureUnit,
                    sizePx = 128
                )

                sectionList.addItem(
                    Row.Builder()
                        .setTitle("${pid.displayName}: $formatted")
                        .addText("PID ${pid.modeAndPid}$statusSuffix")
                        .setImage(icon, Row.IMAGE_TYPE_SMALL)
                        .setOnClickListener {
                            screenManager.push(SensorDetailCarScreen(carContext, pid))
                        }
                        .build()
                )
                sectionCount++
                totalAdded++
            }

            if (sectionCount > 0) {
                templateBuilder.addSectionedList(
                    SectionedItemList.create(sectionList.build(), category.displayName)
                )
            }
        }

        if (!embedded) {
            templateBuilder.setHeader(
                Header.Builder()
                    .setTitle("Все датчики OBD2")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        }

        return templateBuilder.build()
    }

    private fun buildDtcTemplate(embedded: Boolean): Template {
        val dtc = repository.dtcState.value
        val listLimit = getConstraintLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6)
        val itemList = ItemList.Builder()

        val milTitle = if (dtc.milOn) "Check Engine (MIL): АКТИВЕН" else "Check Engine (MIL): Норма"
        val milSubtitle = dtc.statusMessage ?: "Нажмите «Считать» для опроса ЭБУ"
        itemList.addItem(
            Row.Builder()
                .setTitle(milTitle)
                .addText(milSubtitle)
                .build()
        )

        itemList.addItem(
            Row.Builder()
                .setTitle(if (dtc.isScanning) "Сканирование ЭБУ..." else "Считать ошибки ЭБУ (Mode 03)")
                .addText("Повторный опрос блоков управления")
                .setOnClickListener {
                    repository.scanDtcs()
                }
                .build()
        )

        if (dtc.codes.isNotEmpty()) {
            itemList.addItem(
                Row.Builder()
                    .setTitle("Сбросить ошибки (Mode 04)")
                    .addText("Очистить коды неисправностей и погасить MIL")
                    .setOnClickListener {
                        screenManager.push(DtcConfirmClearCarScreen(carContext))
                    }
                    .build()
            )
        }

        val remainingSlots = (listLimit - 3).coerceAtLeast(1)
        for (code in dtc.codes.take(remainingSlots)) {
            itemList.addItem(
                Row.Builder()
                    .setTitle("${code.code} • ${code.severity.label}")
                    .addText(code.description)
                    .build()
            )
        }

        val builder = ListTemplate.Builder().setSingleList(itemList.build())
        if (!embedded) {
            builder.setHeader(
                Header.Builder()
                    .setTitle("Диагностика ЭБУ")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        }
        return builder.build()
    }

    private fun buildConnectionPaneTemplate(embedded: Boolean): Template {
        val conn = repository.connectionState.value
        val settings = repository.settings.value

        val statusTitle = when (conn) {
            is ObdConnectionState.Connected -> "Подключён: ${conn.deviceName}"
            is ObdConnectionState.Connecting -> "Подключение: ${conn.deviceName}"
            is ObdConnectionState.Simulation -> "РЕЖИМ СИМУЛЯЦИИ (без OBD2)"
            is ObdConnectionState.Error -> "Ошибка подключения"
            is ObdConnectionState.Disconnected -> "Адаптер не подключён"
        }

        val statusDetail = when (conn) {
            is ObdConnectionState.Connected ->
                "Протокол: ${conn.protocol} • ${String.format("%.1f", conn.pollingHz)} Гц"
            is ObdConnectionState.Connecting -> conn.step
            is ObdConnectionState.Simulation ->
                "Тестовые данные датчиков • ${String.format("%.1f", conn.pollingHz)} Гц"
            is ObdConnectionState.Error -> conn.message
            is ObdConnectionState.Disconnected ->
                "Выберите адаптер на телефоне или включите тест-режим"
        }

        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(statusTitle)
                    .addText(statusDetail)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Датчиков на экране: ${settings.androidAutoPids.size} из 6")
                    .addText(settings.androidAutoPids.joinToString(", ") { it.shortName })
                    .build()
            )

        if (!repository.bluetoothScanner.hasBluetoothPermissions()) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("Разрешить BT")
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener {
                        carContext.requestPermissions(
                            repository.bluetoothScanner.requiredPermissions().toList()
                        ) { _, _ -> invalidate() }
                    }
                    .build()
            )
        } else if (conn.isActiveTelemetry) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("Отключить")
                    .setOnClickListener { repository.disconnect() }
                    .build()
            )
        } else {
            val lastAddr = settings.lastDeviceAddress
            val lastName = settings.lastDeviceName
            if (lastAddr != null && lastName != null) {
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle("Подключить $lastName")
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener {
                            repository.connectToDevice(
                                com.example.obd.model.BluetoothDeviceItem(
                                    name = lastName,
                                    address = lastAddr,
                                    transport = settings.lastTransport,
                                    isPaired = true,
                                    isLikelyObd = true
                                )
                            )
                        }
                        .build()
                )
            }
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("Тест-режим")
                    .setOnClickListener { repository.startSimulationMode() }
                    .build()
            )
        }

        val builder = PaneTemplate.Builder(paneBuilder.build())
        if (!embedded) {
            builder.setHeader(
                Header.Builder()
                    .setTitle("Подключение OBD2")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        }
        return builder.build()
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
