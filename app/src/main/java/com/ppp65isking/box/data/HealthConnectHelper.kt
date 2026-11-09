package com.ppp65isking.box.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect — today's step count for the Home card. Read-only.
 * Everything is best-effort: Health Connect missing, permission denied,
 * or no data all yield null, never a crash. The UI shows a setup card
 * in those cases instead of a number.
 */
class HealthConnectHelper(private val context: Context) {

    sealed interface StepsResult {
        /** Steps counted today (0 is a real answer, not an error). */
        data class Ok(val steps: Long) : StepsResult
        /** Health Connect is not available on this device / profile. */
        data object Unavailable : StepsResult
        /** Available, but the user has not granted READ_STEPS yet. */
        data object NeedsPermission : StepsResult
        data object Error : StepsResult
    }

    private fun clientOrNull(): HealthConnectClient? {
        if (HealthConnectClient.getSdkStatus(context) !=
            HealthConnectClient.SDK_AVAILABLE
        ) {
            return null
        }
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    suspend fun todaySteps(): StepsResult {
        val client = clientOrNull() ?: return StepsResult.Unavailable
        val readPermission = HealthPermission.getReadPermission(StepsRecord::class)
        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrNull() ?: return StepsResult.Error
        if (readPermission !in granted) return StepsResult.NeedsPermission
        return runCatching {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val end = java.time.Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            StepsResult.Ok(response.records.sumOf { it.count })
        }.getOrElse { StepsResult.Error }
    }
}
