package com.example.obd.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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

    @androidx.room.Update
    suspend fun update(entity: CustomPidEntity)

    @Query("DELETE FROM custom_pids WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(
    entities = [DiagnosticScanEntity::class, CustomPidEntity::class],
    version = 3,
    exportSchema = false
)
abstract class DiagnosticDatabase : RoomDatabase() {
    abstract fun diagnosticScanDao(): DiagnosticScanDao
    abstract fun customPidDao(): CustomPidDao

    companion object {
        @Volatile
        private var INSTANCE: DiagnosticDatabase? = null

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN warningHigh REAL")
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN criticalHigh REAL")
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN warningLow REAL")
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN criticalLow REAL")
                db.execSQL("ALTER TABLE custom_pids ADD COLUMN alertsEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getInstance(context: Context): DiagnosticDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiagnosticDatabase::class.java,
                    "obd_diagnostics.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
