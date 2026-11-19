package com.example.obd.cloud

import android.content.Context
import com.example.R
import com.example.obd.data.CustomPidDao
import com.example.obd.data.MaintenanceDao
import com.example.obd.data.TripRecordDao
import com.example.obd.data.VehicleProfileDao
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class CloudSyncRepository(
    private val context: Context,
    private val vehicleProfileDao: VehicleProfileDao,
    private val maintenanceDao: MaintenanceDao,
    private val customPidDao: CustomPidDao,
    private val tripRecordDao: TripRecordDao
) {
    private val firestore: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    suspend fun syncAll(isEnglish: Boolean): Result<Int> {
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    if (isEnglish) "Firebase is not configured in this build" else "Firebase не настроен в этой сборке"
                )
            )
        }
        val user = Firebase.auth.currentUser
            ?: return Result.failure(
                IllegalStateException(
                    if (isEnglish) "Sign in with Google first" else "Сначала войдите в аккаунт Google"
                )
            )
        val uid = user.uid

        return try {
            val userDoc = firestore.collection("users").document(uid)
            val vehiclesCol = userDoc.collection("vehicles")
            val maintCol = userDoc.collection("maintenance")
            val pidsCol = userDoc.collection("customPids")
            val tripsCol = userDoc.collection("trips")

            var syncedCount = 0

            // 1. Vehicles bidirectional sync
            val remoteVehicleSnaps = vehiclesCol.get().awaitResult().documents
            val remoteVehicleCreatedAt = remoteVehicleSnaps.associate {
                it.id to it.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            val remoteVehicles = remoteVehicleSnaps.mapNotNull {
                it.toObject(CloudVehicleProfile::class.java)
            }

            val localVehicles = vehicleProfileDao.getAll()
            for (veh in localVehicles) {
                val docId = sanitizeDocId("veh_${veh.syncVehicleKey}")
                val writeMap = veh.toCloudWriteMap(uid, remoteVehicleCreatedAt[docId])
                vehiclesCol.document(docId).set(writeMap).awaitResult()
                syncedCount++
            }

            val refreshedLocalVehicles = vehicleProfileDao.getAll().toMutableList()
            for (remoteVeh in remoteVehicles) {
                val match = refreshedLocalVehicles.find {
                    (!remoteVeh.vin.isNullOrBlank() && it.vin.equals(remoteVeh.vin, ignoreCase = true)) ||
                        it.name.equals(remoteVeh.name, ignoreCase = true)
                }
                if (match == null) {
                    val insertedId = vehicleProfileDao.insert(remoteVeh.toEntity())
                    refreshedLocalVehicles.add(remoteVeh.toEntity(existingId = insertedId))
                    syncedCount++
                } else {
                    val mergedOdo = maxOf(match.odometerKm, remoteVeh.odometerKm.toFloat())
                    val mergedHours = maxOf(match.engineHours, remoteVeh.engineHours.toFloat())
                    if (mergedOdo > match.odometerKm || mergedHours > match.engineHours) {
                        val updated = match.copy(odometerKm = mergedOdo, engineHours = mergedHours)
                        vehicleProfileDao.update(updated)
                        refreshedLocalVehicles[refreshedLocalVehicles.indexOf(match)] = updated
                    }
                }
            }

            // 2. Maintenance items bidirectional sync
            val remoteMaintSnaps = maintCol.get().awaitResult().documents
            val remoteMaintCreatedAt = remoteMaintSnaps.associate {
                it.id to it.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            val remoteMaintItems = remoteMaintSnaps.mapNotNull {
                it.toObject(CloudMaintenanceItem::class.java)
            }

            val localMaintItems = maintenanceDao.getAll()
            for (item in localMaintItems) {
                val ownerVeh = refreshedLocalVehicles.find { it.id == item.vehicleId }
                val resolvedKey = ownerVeh?.syncVehicleKey ?: item.vehicleKey.ifBlank { "default" }
                val docId = sanitizeDocId("maint_${resolvedKey}_${stableHashHex(item.title)}")
                val writeMap = item.toCloudWriteMap(uid, resolvedKey, remoteMaintCreatedAt[docId])
                maintCol.document(docId).set(writeMap).awaitResult()
                syncedCount++
            }

            val currentMaintAfterPush = maintenanceDao.getAll()
            for (remoteItem in remoteMaintItems) {
                val targetVeh = refreshedLocalVehicles.find {
                    it.syncVehicleKey.equals(remoteItem.vehicleKey, ignoreCase = true)
                } ?: refreshedLocalVehicles.firstOrNull() ?: continue
                val existing = currentMaintAfterPush.find {
                    it.vehicleId == targetVeh.id && it.title.equals(remoteItem.title, ignoreCase = true)
                }
                if (existing == null) {
                    maintenanceDao.insert(remoteItem.toEntity(vehicleId = targetVeh.id))
                    syncedCount++
                } else if (remoteItem.lastServiceTimestampMs > existing.lastServiceTimestampMs) {
                    maintenanceDao.update(
                        existing.copy(
                            lastServiceOdometerKm = remoteItem.lastServiceOdometerKm.toFloat(),
                            lastServiceEngineHours = remoteItem.lastServiceEngineHours.toFloat(),
                            lastServiceTimestampMs = remoteItem.lastServiceTimestampMs,
                            intervalKm = remoteItem.intervalKm,
                            intervalHours = remoteItem.intervalHours,
                            notes = remoteItem.notes
                        )
                    )
                }
            }

            // 3. Custom PIDs bidirectional sync
            val remotePidSnaps = pidsCol.get().awaitResult().documents
            val remotePidCreatedAt = remotePidSnaps.associate {
                it.id to it.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            val remotePids = remotePidSnaps.mapNotNull {
                it.toObject(CloudCustomPid::class.java)
            }

            val localPids = customPidDao.observeCustomPids().first()
            for (pid in localPids) {
                val docId = sanitizeDocId("pid_${pid.modeAndPid}_${pid.header}_${stableHashHex(pid.equation)}")
                val writeMap = pid.toCloudWriteMap(uid, remotePidCreatedAt[docId])
                pidsCol.document(docId).set(writeMap).awaitResult()
                syncedCount++
            }

            val localPidKeys = localPids.map { "${it.modeAndPid}|${it.equation}" }.toHashSet()
            for (remotePid in remotePids) {
                val key = "${remotePid.modeAndPid}|${remotePid.equation}"
                if (key !in localPidKeys) {
                    customPidDao.insert(remotePid.toEntity())
                    localPidKeys.add(key)
                    syncedCount++
                }
            }

            // 4. Trip summaries bidirectional sync (latest 30)
            val remoteTripSnaps = tripsCol.get().awaitResult().documents
            val remoteTripCreatedAt = remoteTripSnaps.associate {
                it.id to it.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            val remoteTrips = remoteTripSnaps.mapNotNull {
                it.toObject(CloudTripSummary::class.java)
            }

            val localTrips = tripRecordDao.observeTrips().first().take(30)
            for (trip in localTrips) {
                val docId = sanitizeDocId("trip_${trip.startedAtMs}_${trip.endedAtMs}")
                val writeMap = trip.toCloudWriteMap(uid, remoteTripCreatedAt[docId])
                tripsCol.document(docId).set(writeMap).awaitResult()
                syncedCount++
            }

            val localTripKeys = localTrips.map { "${it.startedAtMs}_${it.endedAtMs}" }.toHashSet()
            for (remoteTrip in remoteTrips) {
                val key = "${remoteTrip.startedAtMs}_${remoteTrip.endedAtMs}"
                if (key !in localTripKeys) {
                    tripRecordDao.insert(remoteTrip.toEntity())
                    localTripKeys.add(key)
                    syncedCount++
                }
            }

            Result.success(syncedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val friendly = FirestoreErrorHandler.handleFirestoreError(e, isEnglish)
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    private fun sanitizeDocId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(120)
            .ifBlank { "item" }
    }

    private fun stableHashHex(text: String): String {
        return text.trim().lowercase().hashCode().toUInt().toString(16)
    }
}
