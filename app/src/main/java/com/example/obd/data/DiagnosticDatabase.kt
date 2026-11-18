package com.example.obd.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.obd.model.FuelType
import com.example.obd.model.SensorCategory
import com.example.obd.model.TelemetrySensor
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diagnostic_scans")
data class DiagnosticScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val adapterName: String,
    val protocol: String,
    val milOn: Boolean,
    val dtcCount: Int,
    val dtcSummary: String,
    val batteryVoltage: Float?
)

@Entity(tableName = "custom_pids")
data class CustomPidEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shortName: String,
    val modeAndPid: String,
    val header: String = "",
    val equation: String,
    val unit: String,
    val minValue: Float = 0f,
    val maxValue: Float = 100f,
    val decimals: Int = 1,
    val enabled: Boolean = true,
    val warningHigh: Float? = null,
    val criticalHigh: Float? = null,
    val warningLow: Float? = null,
    val criticalLow: Float? = null,
    val alertsEnabled: Boolean = true
) {
    val sensorId: String
        get() = "CUSTOM_$id"

    fun toTelemetrySensor(): TelemetrySensor = TelemetrySensor(
        id = sensorId,
        modeAndPid = modeAndPid,
        header = header,
        displayName = name,
        shortName = shortName.ifBlank { name.take(12) },
        unit = unit,
        minRange = minValue,
        maxRange = if (maxValue > minValue) maxValue else minValue + 1f,
        decimals = decimals.coerceIn(0, 3),
        category = SensorCategory.CUSTOM,
        isCustom = true,
        customId = id,
        equation = equation,
        builtInPid = null,
        warningThreshold = warningHigh,
        criticalThreshold = criticalHigh,
        lowWarningThreshold = warningLow,
        lowCriticalThreshold = criticalLow,
        alertsEnabled = alertsEnabled
    )
}

@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val vin: String? = null,
    val displacementLiters: Float = 2.0f,
    val fuelType: String = FuelType.PETROL.name,
    val redlineRpm: Int = 6500,
    val shiftLightRpm: Int = 6000,
    val fuelPricePerLiter: Float = 58.0f,
    val preferredPresetId: String = "main",
    @ColumnInfo(defaultValue = "0.0") val odometerKm: Float = 0f,
    @ColumnInfo(defaultValue = "0.0") val engineHours: Float = 0f,
    @ColumnInfo(defaultValue = "1450") val curbWeightKg: Int = 1450
) {
    val fuelTypeEnum: FuelType
        get() = FuelType.entries.find { it.name == fuelType } ?: FuelType.PETROL

    val syncVehicleKey: String
        get() = vin?.takeIf { it.isNotBlank() } ?: "profile_$id"
}

@Entity(tableName = "maintenance_items")
data class MaintenanceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val vehicleKey: String,
    val title: String,
    val category: String,
    val intervalKm: Int,
    val intervalHours: Int = 0,
    val lastServiceOdometerKm: Float = 0f,
    val lastServiceEngineHours: Float = 0f,
    val lastServiceTimestampMs: Long = System.currentTimeMillis(),
    val notes: String? = null
)

@Entity(tableName = "trip_records")
data class TripRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleName: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceKm: Float,
    val durationSec: Long,
    val fuelUsedLiters: Float,
    val avgLPer100Km: Float?,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val estimatedCost: Float,
    @ColumnInfo(defaultValue = "100") val ecoScore: Int = 100,
    @ColumnInfo(defaultValue = "0.0") val peakHorsepower: Float = 0f,
    @ColumnInfo(defaultValue = "0") val harshEventsCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val coldRevSec: Long = 0L
)

@Entity(tableName = "acceleration_runs")
data class AccelerationRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleName: String,
    val discipline: String,
    val elapsedSec: Float,
    val peakSpeedKmh: Float,
    val peakRpm: Float,
    val timestampMs: Long,
    @ColumnInfo(defaultValue = "0.0") val peakHorsepower: Float = 0f,
    @ColumnInfo(defaultValue = "0.0") val peakTorqueNm: Float = 0f,
    @ColumnInfo(defaultValue = "") val curveCsv: String = ""
)

@Dao
interface DiagnosticScanDao {
    @Query("SELECT * FROM diagnostic_scans ORDER BY timestampMs DESC LIMIT 25")
    fun observeRecentScans(): Flow<List<DiagnosticScanEntity>>

    @Insert
    suspend fun insertScan(scan: DiagnosticScanEntity)

    @Query("DELETE FROM diagnostic_scans")
    suspend fun clearHistory()
}

@Dao
interface CustomPidDao {
    @Query("SELECT * FROM custom_pids ORDER BY id ASC")
    fun observeCustomPids(): Flow<List<CustomPidEntity>>

    @Insert
    suspend fun insert(entity: CustomPidEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CustomPidEntity>)

    @Update
    suspend fun update(entity: CustomPidEntity)

    @Query("DELETE FROM custom_pids WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_pids")
    suspend fun clearAll()
}

@Dao
interface VehicleProfileDao {
    @Query("SELECT * FROM vehicle_profiles ORDER BY id ASC")
    fun observeProfiles(): Flow<List<VehicleProfileEntity>>

    @Query("SELECT * FROM vehicle_profiles ORDER BY id ASC")
    suspend fun getAll(): List<VehicleProfileEntity>

    @Query("SELECT * FROM vehicle_profiles WHERE vin = :vin LIMIT 1")
    suspend fun findByVin(vin: String): VehicleProfileEntity?

    @Insert
    suspend fun insert(profile: VehicleProfileEntity): Long

    @Insert
    suspend fun insertAll(profiles: List<VehicleProfileEntity>)

    @Update
    suspend fun update(profile: VehicleProfileEntity)

    @Query(
        "UPDATE vehicle_profiles SET odometerKm = odometerKm + :deltaKm, engineHours = engineHours + :deltaHours WHERE id = :id"
    )
    suspend fun addMileageAndHours(id: Long, deltaKm: Float, deltaHours: Float)

    @Query("DELETE FROM vehicle_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM maintenance_items ORDER BY id ASC")
    fun observeAll(): Flow<List<MaintenanceItemEntity>>

    @Query("SELECT * FROM maintenance_items WHERE vehicleId = :vehicleId ORDER BY id ASC")
    fun observeForVehicle(vehicleId: Long): Flow<List<MaintenanceItemEntity>>

    @Query("SELECT * FROM maintenance_items WHERE vehicleId = :vehicleId ORDER BY id ASC")
    suspend fun getForVehicle(vehicleId: Long): List<MaintenanceItemEntity>

    @Query("SELECT * FROM maintenance_items ORDER BY id ASC")
    suspend fun getAll(): List<MaintenanceItemEntity>

    @Insert
    suspend fun insert(item: MaintenanceItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<MaintenanceItemEntity>)

    @Update
    suspend fun update(item: MaintenanceItemEntity)

    @Query("DELETE FROM maintenance_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM maintenance_items WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: Long)
}

@Dao
interface TripRecordDao {
    @Query("SELECT * FROM trip_records ORDER BY endedAtMs DESC LIMIT 50")
    fun observeTrips(): Flow<List<TripRecordEntity>>

    @Insert
    suspend fun insert(trip: TripRecordEntity): Long

    @Query("DELETE FROM trip_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trip_records")
    suspend fun clearAll()
}

@Dao
interface AccelerationRunDao {
    @Query("SELECT * FROM acceleration_runs ORDER BY timestampMs DESC LIMIT 50")
    fun observeRuns(): Flow<List<AccelerationRunEntity>>

    @Insert
    suspend fun insert(run: AccelerationRunEntity): Long

    @Query("DELETE FROM acceleration_runs")
    suspend fun clearAll()
}

@Database(
    entities = [
        DiagnosticScanEntity::class,
        CustomPidEntity::class,
        VehicleProfileEntity::class,
        TripRecordEntity::class,
        AccelerationRunEntity::class,
        MaintenanceItemEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class DiagnosticDatabase : RoomDatabase() {
    abstract fun diagnosticScanDao(): DiagnosticScanDao
    abstract fun customPidDao(): CustomPidDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun tripRecordDao(): TripRecordDao
    abstract fun accelerationRunDao(): AccelerationRunDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        @Volatile
        private var INSTANCE: DiagnosticDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN warningHigh REAL")
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN criticalHigh REAL")
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN warningLow REAL")
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN criticalLow REAL")
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN alertsEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vehicle_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        vin TEXT,
                        displacementLiters REAL NOT NULL DEFAULT 2.0,
                        fuelType TEXT NOT NULL DEFAULT 'PETROL',
                        redlineRpm INTEGER NOT NULL DEFAULT 6500,
                        shiftLightRpm INTEGER NOT NULL DEFAULT 6000,
                        fuelPricePerLiter REAL NOT NULL DEFAULT 58.0,
                        preferredPresetId TEXT NOT NULL DEFAULT 'main'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trip_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleName TEXT NOT NULL,
                        startedAtMs INTEGER NOT NULL,
                        endedAtMs INTEGER NOT NULL,
                        distanceKm REAL NOT NULL,
                        durationSec INTEGER NOT NULL,
                        fuelUsedLiters REAL NOT NULL,
                        avgLPer100Km REAL,
                        avgSpeedKmh REAL NOT NULL,
                        maxSpeedKmh REAL NOT NULL,
                        estimatedCost REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS acceleration_runs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleName TEXT NOT NULL,
                        discipline TEXT NOT NULL,
                        elapsedSec REAL NOT NULL,
                        peakSpeedKmh REAL NOT NULL,
                        peakRpm REAL NOT NULL,
                        timestampMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_profiles ADD COLUMN odometerKm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE vehicle_profiles ADD COLUMN engineHours REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS maintenance_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleId INTEGER NOT NULL,
                        vehicleKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        intervalKm INTEGER NOT NULL,
                        intervalHours INTEGER NOT NULL DEFAULT 0,
                        lastServiceOdometerKm REAL NOT NULL DEFAULT 0.0,
                        lastServiceEngineHours REAL NOT NULL DEFAULT 0.0,
                        lastServiceTimestampMs INTEGER NOT NULL DEFAULT 0,
                        notes TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_profiles ADD COLUMN curbWeightKg INTEGER NOT NULL DEFAULT 1450")
                db.execSQL("ALTER TABLE trip_records ADD COLUMN ecoScore INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE trip_records ADD COLUMN peakHorsepower REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE trip_records ADD COLUMN harshEventsCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_records ADD COLUMN coldRevSec INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE acceleration_runs ADD COLUMN peakHorsepower REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE acceleration_runs ADD COLUMN peakTorqueNm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE acceleration_runs ADD COLUMN curveCsv TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): DiagnosticDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiagnosticDatabase::class.java,
                    "obd_diagnostics.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
