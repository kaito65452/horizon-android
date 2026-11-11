package dev.horizon.android.diagnostics

import androidx.compose.runtime.mutableStateMapOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic record of a network request per source.
 */
data class NetEntry(
    val source: String,
    val lastUrl: String,
    val status: String,
    val count: Int,
    val timestamp: Long
) {
    fun formattedTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * Shared diagnostic logger for all external network endpoints.
 * Displays live in Settings > About > Diagnostics without needing adb logcat.
 */
object NetLog {
    val entries = mutableStateMapOf<String, NetEntry>(
        "Modrinth" to NetEntry("Modrinth", "None", "Pending", 0, System.currentTimeMillis()),
        "Horizon Catalog" to NetEntry("Horizon Catalog", "None", "Pending", 0, System.currentTimeMillis()),
        "Microsoft Skin" to NetEntry("Microsoft Skin", "None", "Pending", 0, System.currentTimeMillis()),
        "Ely.by Skin" to NetEntry("Ely.by Skin", "None", "Pending", 0, System.currentTimeMillis())
    )

    fun log(source: String, url: String, status: String, count: Int = 0) {
        entries[source] = NetEntry(
            source = source,
            lastUrl = url,
            status = status,
            count = count,
            timestamp = System.currentTimeMillis()
        )
    }

    fun logSuccess(source: String, url: String, count: Int = 1) {
        log(source, url, "HTTP 200 (OK)", count)
    }

    fun logError(source: String, url: String, throwable: Throwable) {
        val message = throwable.javaClass.simpleName + (throwable.message?.let { ": $it" } ?: "")
        log(source, url, message, 0)
    }
}

/**
 * Diagnostic tracking of runtime engine process and services.
 */
object ProcessTracker {
    @Volatile
    var isGameRunning: Boolean = false

    @Volatile
    var isServiceRunning: Boolean = false
}
