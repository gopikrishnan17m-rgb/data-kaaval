package com.datakaaval.app

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object UsageReader {

    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun uidBytes(c: Context, wifi: Boolean, start: Long, end: Long): Map<Int, Long> {
        val nsm = c.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val out = HashMap<Int, Long>()
        val types = if (wifi) intArrayOf(ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI)
        else intArrayOf(ConnectivityManager.TYPE_MOBILE)
        for (t in types) {
            try {
                val s = nsm.querySummary(t, null, start, end)
                val b = NetworkStats.Bucket()
                while (s.hasNextBucket()) {
                    s.getNextBucket(b)
                    out[b.uid] = (out[b.uid] ?: 0L) + b.rxBytes + b.txBytes
                }
                s.close()
            } catch (e: Exception) {
                // this network type may be unavailable (no SIM, etc.)
            }
        }
        return out
    }

    private fun idOf(c: Context, uid: Int, names: MutableMap<String, String>): String {
        if (uid == NetworkStats.Bucket.UID_TETHERING) return "hs"
        if (uid == NetworkStats.Bucket.UID_REMOVED) {
            names["removed"] = "Removed apps"
            return "removed"
        }
        if (uid < 10000) {
            names["system"] = "Android system"
            return "system"
        }
        val pm = c.packageManager
        val pkgs = pm.getPackagesForUid(uid)
        if (pkgs == null || pkgs.isEmpty()) {
            names["other"] = "Other apps"
            return "other"
        }
        val p = pkgs[0]
        if (!names.containsKey(p)) {
            names[p] = try {
                pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()
            } catch (e: Exception) {
                p
            }
        }
        return p
    }

    private fun toMb(c: Context, bytes: Map<Int, Long>, names: MutableMap<String, String>): Map<String, Double> {
        val out = HashMap<String, Double>()
        for ((uid, b) in bytes) {
            val id = idOf(c, uid, names)
            out[id] = (out[id] ?: 0.0) + b / 1048576.0
        }
        return out
    }

    private fun midnight(daysAgo: Int): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return cal
    }

    fun today(c: Context, wifi: Boolean): Map<String, Double> {
        val start = midnight(0).timeInMillis
        val names = HashMap<String, String>()
        return toMb(c, uidBytes(c, wifi, start, System.currentTimeMillis()), names)
    }

    fun readDays(c: Context, wifi: Boolean): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val names = HashMap<String, String>()
        val days = JSONObject()
        val now = System.currentTimeMillis()
        for (i in 29 downTo 0) {
            val s = midnight(i)
            val e = midnight(i - 1)
            val end = if (e.timeInMillis > now) now else e.timeInMillis
            val perId = toMb(c, uidBytes(c, wifi, s.timeInMillis, end), names)
            val o = JSONObject()
            for ((id, mb) in perId) {
                if (mb >= 0.05) o.put(id, Math.round(mb * 10.0) / 10.0)
            }
            days.put(fmt.format(s.time), o)
        }
        val n = JSONObject()
        for ((k, v) in names) n.put(k, v)
        return JSONObject().put("days", days).put("names", n).toString()
    }
}
