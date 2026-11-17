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

@Dao
interface DiagnosticScanDao {
    @Query("SELECT * FROM diagnostic_scans ORDER BY timestampMs DESC LIMIT 25")
    fun observeRecentScans(): Flow<List<DiagnosticScanEntity>>

    @Insert
    suspend fun insertScan(scan: DiagnosticScanEntity)

    @Query("DELETE FROM diagnostic_scans")
    suspend fun clearHistory()
}

@Database(entities = [DiagnosticScanEntity::class], version = 1, exportSchema = false)
abstract class DiagnosticDatabase : RoomDatabase() {
    abstract fun diagnosticScanDao(): DiagnosticScanDao

    companion object {
        @Volatile
        private var INSTANCE: DiagnosticDatabase? = null

        fun getInstance(context: Context): DiagnosticDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiagnosticDatabase::class.java,
                    "obd_diagnostics.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
