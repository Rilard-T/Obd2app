package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.obd.i18n.AppLocalization
import com.example.obd.model.AppLanguage
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  @Config(qualifiers = "ru")
  fun `read string from russian context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OBD2 Авто", appName)
  }

  @Test
  @Config(qualifiers = "en")
  fun `read string from english context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OBD2 Auto", appName)
  }

  @Test
  fun `appLocalization switches resource configuration without activity restart`() {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val ruContext = AppLocalization.localizedContext(base, AppLanguage.RU)
    val enContext = AppLocalization.localizedContext(base, AppLanguage.EN)

    assertEquals("OBD2 Авто", ruContext.getString(R.string.app_name))
    assertEquals("OBD2 Auto", enContext.getString(R.string.app_name))
    assertFalse(AppLocalization.isEnglish(AppLanguage.RU))
    assertTrue(AppLocalization.isEnglish(AppLanguage.EN))
  }

  @Test
  @Suppress("DEPRECATION")
  fun `manifest car app permissions and category match distribution channel`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = context.packageManager
    val pkgInfo = pm.getPackageInfo(
      context.packageName,
      android.content.pm.PackageManager.GET_PERMISSIONS
    )
    val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()

    val navIntent = android.content.Intent("androidx.car.app.CarAppService")
      .addCategory("androidx.car.app.category.NAVIGATION")
      .setPackage(context.packageName)
    val iotIntent = android.content.Intent("androidx.car.app.CarAppService")
      .addCategory("androidx.car.app.category.IOT")
      .setPackage(context.packageName)

    val navResolved = pm.queryIntentServices(navIntent, 0)
    val iotResolved = pm.queryIntentServices(iotIntent, 0)

    if (BuildConfig.IS_GITHUB_BUILD) {
      assertTrue("GitHub build must declare NAVIGATION_TEMPLATES", "androidx.car.app.NAVIGATION_TEMPLATES" in perms)
      assertTrue("GitHub build must declare ACCESS_SURFACE", "androidx.car.app.ACCESS_SURFACE" in perms)
      assertTrue("GitHub build must resolve CarAppService as NAVIGATION", navResolved.isNotEmpty())
      assertTrue("GitHub build uses NAVIGATION instead of IOT", iotResolved.isEmpty())
    } else {
      assertFalse("Google Play build must NOT declare NAVIGATION_TEMPLATES", "androidx.car.app.NAVIGATION_TEMPLATES" in perms)
      assertFalse("Google Play build must NOT declare ACCESS_SURFACE", "androidx.car.app.ACCESS_SURFACE" in perms)
      assertTrue("Google Play build must NOT resolve CarAppService as NAVIGATION", navResolved.isEmpty())
      assertTrue("Google Play build must resolve CarAppService as IOT", iotResolved.isNotEmpty())
    }
  }

  @Test
  fun `launching MainActivity succeeds without runtime crash`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertFalse(activity.isFinishing)
    controller.pause().stop().destroy()
  }

  @Test
  fun `starting ObdForegroundService and simulation mode without BT permissions does not crash`() {
    val serviceController = org.robolectric.Robolectric.buildService(com.example.obd.ObdForegroundService::class.java)
    serviceController.create()
    serviceController.startCommand(0, 1)
    serviceController.destroy()

    val app = ApplicationProvider.getApplicationContext<ObdApplication>()
    app.telemetryRepository.startSimulationMode()
    app.telemetryRepository.disconnect()
  }

  @Test
  fun `vehicleCatalog detects brand from WMI VIN prefix and provides templates`() {
    assertEquals("VAG (VW / Audi / Skoda)", com.example.obd.model.VehicleCatalog.detectBrandFromVin("WVWZZZ3CZ8E123456"))
    assertEquals("Toyota / Lexus", com.example.obd.model.VehicleCatalog.detectBrandFromVin("jtdkn3du901234567"))
    assertEquals("Hyundai / Kia", com.example.obd.model.VehicleCatalog.detectBrandFromVin("KMHD84LF9HU123456"))
    assertEquals("Lada / Renault / Nissan", com.example.obd.model.VehicleCatalog.detectBrandFromVin("XTAGFL110JY123456"))
    assertEquals(null, com.example.obd.model.VehicleCatalog.detectBrandFromVin("ZZZ00000000000000"))
    assertTrue(com.example.obd.model.VehicleCatalog.brands.isNotEmpty())
    assertTrue(com.example.obd.model.VehicleCatalog.modelsForBrand("VAG (VW / Audi / Skoda)").isNotEmpty())
  }

  @Test
  fun `vehicleCatalog detects JDM chassis frame numbers with or without emission prefix`() {
    val prius = com.example.obd.model.VehicleCatalog.detectFromIdentifier("ZVW30-1234567")
    assertEquals("Toyota / Lexus", prius?.brand)
    assertEquals("toyota_hybrid", prius?.templateId)
    assertEquals(com.example.obd.model.FuelType.HYBRID, prius?.fuelType)
    assertEquals(1.8f, prius?.displacementLiters ?: 0f, 0.01f)
    assertTrue(prius?.isFrameNumber == true)

    val priusPrefix = com.example.obd.model.VehicleCatalog.detectFromIdentifier("daa-zvw30-0045678")
    assertEquals("Toyota / Lexus", priusPrefix?.brand)
    assertEquals("toyota_hybrid", priusPrefix?.templateId)

    val forester = com.example.obd.model.VehicleCatalog.detectFromIdentifier("SG5-012345")
    assertEquals("Subaru", forester?.brand)
    assertEquals("subaru_boxer", forester?.templateId)

    val fit = com.example.obd.model.VehicleCatalog.detectFromIdentifier("GK3-100001")
    assertEquals("Honda / Acura", fit?.brand)
    assertEquals("honda_petrol", fit?.templateId)

    val cx5Diesel = com.example.obd.model.VehicleCatalog.detectFromIdentifier("LDA-KE2FW-100234")
    assertEquals("Ford / Mazda", cx5Diesel?.brand)
    assertEquals(com.example.obd.model.FuelType.DIESEL, cx5Diesel?.fuelType)

    val outlanderPhev = com.example.obd.model.VehicleCatalog.detectFromIdentifier("GG2W-012345")
    assertEquals("Mitsubishi", outlanderPhev?.brand)
    assertEquals(com.example.obd.model.FuelType.HYBRID, outlanderPhev?.fuelType)
  }

  @Test
  fun `fuelTrimHealthAnalyzer classifies optimal lean and rich mixtures`() {
    val stftSensor = com.example.obd.model.ObdPid.SHORT_FUEL_TRIM.toTelemetrySensor(true)
    val ltftSensor = com.example.obd.model.ObdPid.LONG_FUEL_TRIM.toTelemetrySensor(true)

    val noData = com.example.obd.data.FuelTrimHealthAnalyzer.analyze(emptyMap())
    assertEquals(com.example.obd.data.FuelTrimHealthStatus.NO_DATA, noData.status)

    val optimal = com.example.obd.data.FuelTrimHealthAnalyzer.analyze(
      mapOf(
        stftSensor.id to com.example.obd.model.SensorReading(stftSensor.id, stftSensor, 1.5f),
        ltftSensor.id to com.example.obd.model.SensorReading(ltftSensor.id, ltftSensor, 2.0f)
      )
    )
    assertEquals(com.example.obd.data.FuelTrimHealthStatus.OPTIMAL, optimal.status)

    val lean = com.example.obd.data.FuelTrimHealthAnalyzer.analyze(
      mapOf(
        stftSensor.id to com.example.obd.model.SensorReading(stftSensor.id, stftSensor, 5.5f),
        ltftSensor.id to com.example.obd.model.SensorReading(ltftSensor.id, ltftSensor, 9.5f)
      )
    )
    assertEquals(com.example.obd.data.FuelTrimHealthStatus.LEAN_VACUUM_LEAK, lean.status)

    val rich = com.example.obd.data.FuelTrimHealthAnalyzer.analyze(
      mapOf(
        stftSensor.id to com.example.obd.model.SensorReading(stftSensor.id, stftSensor, -6.0f),
        ltftSensor.id to com.example.obd.model.SensorReading(ltftSensor.id, ltftSensor, -8.0f)
      )
    )
    assertEquals(com.example.obd.data.FuelTrimHealthStatus.RICH_OVERFUEL, rich.status)
  }

  @Test
  fun `tripComputerManager samples trip curve CSV and clears on reset`() {
    val mgr = com.example.obd.data.TripComputerManager()
    val t0 = 1_000_000L
    mgr.onTelemetrySample(
      timestampMs = t0,
      speedKmh = 20f,
      rpm = 1800f,
      coolantTempC = 75f,
      mafGramsPerSec = 10f,
      intakeMapKpa = 60f,
      intakeAirTempC = 25f,
      displacementLiters = 2.0f,
      fuelType = com.example.obd.model.FuelType.PETROL,
      fuelPricePerLiter = 58f
    )
    mgr.onTelemetrySample(
      timestampMs = t0 + 15_000L,
      speedKmh = 85f,
      rpm = 3100f,
      coolantTempC = 88f,
      mafGramsPerSec = 28f,
      intakeMapKpa = 95f,
      intakeAirTempC = 30f,
      displacementLiters = 2.0f,
      fuelType = com.example.obd.model.FuelType.PETROL,
      fuelPricePerLiter = 58f
    )
    val csv = mgr.getTripCurveCsv()
    assertTrue("Curve CSV should contain pipe-delimited points", csv.contains("|"))
    assertTrue(csv.contains("85.0"))

    mgr.resetTrip()
    assertEquals("", mgr.getTripCurveCsv())
  }

  @Test
  fun `database v7 stores telemetry snapshots and per-vehicle custom pids`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.obd.data.DiagnosticDatabase::class.java
    ).allowMainThreadQueries().build()

    try {
      val snapId = db.telemetrySnapshotDao().insert(
        com.example.obd.data.TelemetrySnapshotEntity(
          timestampMs = 123456L,
          triggerType = "DTC",
          dtcSummary = "P0300",
          rpm = 2400f,
          speedKmh = 60f,
          coolantTempC = 92f,
          batteryVoltage = 14.1f,
          stftPct = 4.2f,
          ltftPct = 11.5f,
          vehicleName = "VW Tiguan"
        )
      )
      assertTrue(snapId > 0)
      val loadedSnaps = db.telemetrySnapshotDao().observeRecentSnapshots().first()
      assertEquals(1, loadedSnaps.size)
      assertEquals("P0300", loadedSnaps.first().dtcSummary)
      assertEquals(11.5f, loadedSnaps.first().ltftPct ?: 0f, 0.01f)

      val pidId = db.customPidDao().insert(
        com.example.obd.data.CustomPidEntity(
          name = "Oil Temp",
          shortName = "OilT",
          modeAndPid = "221401",
          equation = "A-40",
          unit = "°C",
          vehicleProfileId = 42L,
          probeStatus = "SUPPORTED"
        )
      )
      assertTrue(pidId > 0)
      val loadedPids = db.customPidDao().observeCustomPids().first()
      assertEquals(1, loadedPids.size)
      assertEquals(42L, loadedPids.first().vehicleProfileId)
      assertEquals("SUPPORTED", loadedPids.first().probeStatus)
    } finally {
      db.close()
    }
  }

  @Test
  fun `quickConnectTileService is declared and starts listening cleanly under Robolectric`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tileIntent = android.content.Intent(android.service.quicksettings.TileService.ACTION_QS_TILE)
      .setPackage(context.packageName)
    val resolved = context.packageManager.queryIntentServices(tileIntent, 0)
    assertTrue("Manifest must register ObdQuickConnectTileService", resolved.isNotEmpty())

    val controller = org.robolectric.Robolectric.buildService(com.example.obd.ObdQuickConnectTileService::class.java)
    val service = controller.create().get()
    service.onStartListening()
    service.onStopListening()
  }
}
