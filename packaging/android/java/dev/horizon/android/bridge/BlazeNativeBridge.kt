package dev.horizon.android.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Singleton JNI bridge to the pure Rust backend (libhorizon_android.so).
 */
object BlazeNativeBridge {
    init {
        try {
            System.loadLibrary("horizon_android")
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("BlazeNativeBridge: failed to load horizon_android: ${e.message}")
        }
    }

    private var initialized = false

    fun initIfNeeded(context: Context) {
        if (!initialized) {
            try {
                nativeInit(context.filesDir.absolutePath)
                initialized = true
            } catch (e: Throwable) {
                System.err.println("BlazeNativeBridge init error: ${e.message}")
            }
        }
    }

    // Native JNI functions implemented in crates/horizon-client/src/android/jni_bridge.rs
    private external fun nativeInit(dataDir: String)
    external fun nativeGetConfigJson(): String
    external fun nativeSaveConfigJson(json: String): Boolean
    external fun nativeLaunchGame(profileId: Int): Boolean
    external fun nativeStartMultiThreadDownload(threads: Int): Boolean
    external fun nativeGetDownloadProgressJson(): String
    external fun nativeSetKeepAliveEnabled(enabled: Boolean): Boolean
    external fun nativeSetComplexVoiceChatConfig(enabled: Boolean, rnnoise: Boolean): Boolean
    external fun nativeFetchCatalogMods(source: String, category: String, query: String): String
    external fun nativeGetControlLayoutJson(): String
    external fun nativeSaveControlLayoutJson(json: String): Boolean
    external fun nativeExportWorlds(): Boolean
    external fun nativeGetDiagnosticsJson(): String
    external fun nativeGetVersion(): String
    external fun nativeResetSettings(category: String): Boolean

    // Safe helper accessors
    fun getVersion(): String {
        return try {
            nativeGetVersion().ifEmpty { "26.3 • Blaze Engine" }
        } catch (_: Throwable) {
            "26.3 • Blaze Engine"
        }
    }

    fun isKeepAliveEnabled(): Boolean {
        return try {
            val json = JSONObject(nativeGetConfigJson())
            json.optBoolean("keepalive_enabled", true)
        } catch (_: Throwable) {
            true
        }
    }

    fun isComplexVoiceChatEnabled(): Boolean {
        return try {
            val json = JSONObject(nativeGetConfigJson())
            json.optBoolean("svc_enabled", true)
        } catch (_: Throwable) {
            true
        }
    }

    fun isRNNoiseEnabled(): Boolean {
        return try {
            val json = JSONObject(nativeGetConfigJson())
            json.optBoolean("svc_rnnoise", true)
        } catch (_: Throwable) {
            true
        }
    }
}

data class DownloadThread(
    val id: Int,
    val name: String,
    val progress: Float,
    val speedMibS: Float,
    val status: String
)

data class ControlButton(
    val id: String,
    val label: String,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
    var opacity: Float,
    var scale: Float,
    var cameraPassthrough: Boolean
)

data class CatalogItem(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val downloads: Long,
    val installed: Boolean,
    val unsupported: Boolean
)
