package dev.horizon.android.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import dev.horizon.android.diagnostics.NetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object WardrobeService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Converts a legacy 64x32 Minecraft skin to modern 64x64 dual-layer format,
     * replicating right limbs to left limbs and handling the Notch hat transparency rule.
     */
    fun convertLegacySkin(legacy: Bitmap): Bitmap {
        if (legacy.width != 64 || legacy.height != 32) return legacy
        val modern = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(modern)
        canvas.drawBitmap(legacy, 0f, 0f, null)

        val pixels = IntArray(64 * 64)
        modern.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        fun at(x: Int, y: Int): Int = y * 64 + x

        // Replicate and mirror Right Leg -> Left Leg
        val legRects = listOf(
            intArrayOf(4, 16, 16, 32, 4, 4),
            intArrayOf(8, 16, 16, 32, 4, 4),
            intArrayOf(0, 20, 24, 32, 4, 12),
            intArrayOf(4, 20, 16, 32, 4, 12),
            intArrayOf(8, 20, 8, 32, 4, 12),
            intArrayOf(12, 20, 16, 32, 4, 12)
        )

        // Replicate and mirror Right Arm -> Left Arm
        val armRects = listOf(
            intArrayOf(44, 16, -8, 32, 4, 4),
            intArrayOf(48, 16, -8, 32, 4, 4),
            intArrayOf(40, 20, 0, 32, 4, 12),
            intArrayOf(44, 20, -8, 32, 4, 12),
            intArrayOf(48, 20, -16, 32, 4, 12),
            intArrayOf(52, 20, -8, 32, 4, 12)
        )

        for (r in legRects + armRects) {
            val sx = r[0]; val sy = r[1]; val ox = r[2]; val oy = r[3]; val w = r[4]; val h = r[5]
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val srcIdx = at(sx + x, sy + y)
                    val dstIdx = at(sx + ox + (w - 1 - x), sy + oy + y)
                    pixels[dstIdx] = pixels[srcIdx]
                }
            }
        }

        fun setOpaque(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val idx = at(x, y)
                    pixels[idx] = pixels[idx] or (0xFF shl 24)
                }
            }
        }

        setOpaque(0, 0, 32, 16)

        var hatOpaque = true
        for (y in 0 until 16) {
            for (x in 32 until 64) {
                if ((pixels[at(x, y)] ushr 24) < 128) {
                    hatOpaque = false
                    break
                }
            }
            if (!hatOpaque) break
        }
        if (hatOpaque) {
            for (y in 0 until 16) {
                for (x in 32 until 64) {
                    pixels[at(x, y)] = 0
                }
            }
        }

        setOpaque(0, 16, 64, 32)
        setOpaque(16, 48, 48, 64)

        modern.setPixels(pixels, 0, 64, 0, 0, 64, 64)
        return modern
    }

    /**
     * Detects slim (Alex 3px) vs classic (Steve 4px) geometry by checking the unused UV strips.
     */
    fun isSlimArm(bmp: Bitmap): Boolean {
        if (bmp.width < 64 || bmp.height < 64) return false
        fun isStripTransparent(xRange: IntRange, yRange: IntRange): Boolean {
            for (y in yRange) {
                for (x in xRange) {
                    if ((bmp.getPixel(x, y) ushr 24 and 0xFF) != 0) return false
                }
            }
            return true
        }

        return isStripTransparent(50..51, 16..19) &&
               isStripTransparent(54..55, 20..31) &&
               isStripTransparent(42..43, 48..51) &&
               isStripTransparent(46..47, 52..63)
    }

    /**
     * Downloads skin from Microsoft/Mojang profile services.
     */
    suspend fun fetchMicrosoftSkin(accessToken: String): Pair<Bitmap, String>? = withContext(Dispatchers.IO) {
        val endpoint = "https://api.minecraftservices.com/minecraft/profile"
        try {
            val req = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                val body = res.body?.string() ?: throw IOException("Empty profile")
                val json = JSONObject(body)
                val skins = json.optJSONArray("skins")
                var skinUrl: String? = null
                var variant = "CLASSIC"

                if (skins != null) {
                    for (i in 0 until skins.length()) {
                        val s = skins.getJSONObject(i)
                        if (s.optString("state") == "ACTIVE") {
                            skinUrl = s.optString("url")
                            variant = s.optString("variant", "CLASSIC")
                            break
                        }
                    }
                }

                if (skinUrl.isNullOrEmpty()) throw IOException("No active skin")

                val imgReq = Request.Builder().url(skinUrl).build()
                client.newCall(imgReq).execute().use { imgRes ->
                    val bytes = imgRes.body?.bytes() ?: throw IOException("Empty image")
                    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp.width == 64 && bmp.height == 32) {
                        bmp = convertLegacySkin(bmp)
                    }
                    val model = if (variant.equals("SLIM", ignoreCase = true) || isSlimArm(bmp)) "slim" else "classic"
                    NetLog.logSuccess("Microsoft Skin", skinUrl, 1)
                    Pair(bmp, model)
                }
            }
        } catch (e: Throwable) {
            NetLog.logError("Microsoft Skin", endpoint, e)
            null
        }
    }

    /**
     * Downloads skin from Ely.by skin system.
     */
    suspend fun fetchElyBySkin(username: String): Pair<Bitmap, String>? = withContext(Dispatchers.IO) {
        val url = "http://skinsystem.ely.by/textures/$username"
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                val bytes = res.body?.bytes() ?: throw IOException("Empty image")
                var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp.width == 64 && bmp.height == 32) {
                    bmp = convertLegacySkin(bmp)
                }
                val model = if (isSlimArm(bmp)) "slim" else "classic"
                NetLog.logSuccess("Ely.by Skin", url, 1)
                Pair(bmp, model)
            }
        } catch (e: Throwable) {
            NetLog.logError("Ely.by Skin", url, e)
            null
        }
    }
}
