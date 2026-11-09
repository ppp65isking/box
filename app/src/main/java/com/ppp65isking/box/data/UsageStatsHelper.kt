package com.ppp65isking.box.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * UsageStats — per-app last-used times, for "most used" ordering.
 *
 * This is a special Settings grant, not a runtime permission: the user
 * enables it once in Settings → Usage access
 * ([Settings.ACTION_USAGE_ACCESS_SETTINGS]). No screen content, no
 * messages — only which apps were used and when.
 *
 * Honest unknown, flagged for the on-device check: whether a
 * sideloaded Box also needs the "Allow restricted settings" step
 * (App info → ⋮ menu) before it can appear in Usage access. If Box
 * doesn't show up there, that step is the first thing to try.
 */
object UsageStatsHelper {

    /** True when the user has granted usage access. Re-checked on
     *  resume (same pattern as the Health Connect check) — the grant
     *  can appear or disappear while Box is in the background. */
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
            as? AppOpsManager ?: return false
        return runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    /** Open the system Usage access settings, where the user flips
     *  Box on. Best-effort: no-op if unresolvable. */
    fun openSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** Package name → last-used time (epoch ms), from the last
     *  [daysBack] days. Empty when the grant is missing or there is
     *  no usage yet — callers treat that as "no data", never an error. */
    fun lastUsedTimes(context: Context, daysBack: Int = 7): Map<String, Long> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - daysBack * 24L * 60L * 60L * 1000L
        return runCatching {
            manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                start,
                end,
            ).orEmpty()
                .filter { it.lastTimeUsed > 0L }
                .associate { it.packageName to it.lastTimeUsed }
        }.getOrDefault(emptyMap())
    }
}
