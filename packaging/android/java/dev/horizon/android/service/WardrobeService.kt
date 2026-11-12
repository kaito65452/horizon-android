package dev.horizon.android.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import dev.horizon.android.diagnostics.NetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PlayerCape(
    val id: String,
    val state: String,
    val url: String,
    val alias: String
) {
    val isUsing: Boolean get() = state.equals("ACTIVE", ignoreCase = true)
}

data class PlayerSkin(
    val id: String,
    val state: String,
    val url: String,
    val variant: String
) {
    val isUsing: Boolean get() = state.equals("ACTIVE", ignoreCase = true)
}

data class PlayerProfile(
    val id: String,
    val name: String,
    val skins: List<PlayerSkin> = emptyList(),
    val capes: List<PlayerCape> = emptyList()
) {
    val activeSkin: PlayerSkin? get() = skins.firstOrNull { it.isUsing } ?: skins.firstOrNull()
    val activeCape: PlayerCape? get() = capes.firstOrNull { it.isUsing }
}

data class SkinCapeResult(
    val skin: Bitmap,
    val model: String,
    val cape: Bitmap? = null,
    val capeName: String? = null,
    val capes: List<PlayerCape> = emptyList(),
    val profile: PlayerProfile? = null
)

object WardrobeService {
    private const val MINECRAFT_SERVICES_URL = "https://api.minecraftservices.com"
    private const val SESSIONS_SERVER_URL = "https://sessionserver.mojang.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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
     * Fetches PlayerProfile directly from official Minecraft Services API using Bearer token (Zalith Launcher 2 Parity).
     * Endpoint: GET https://api.minecraftservices.com/minecraft/profile
     */
    suspend fun fetchPlayerProfile(accessToken: String): PlayerProfile = withContext(Dispatchers.IO) {
        val url = "$MINECRAFT_SERVICES_URL/minecraft/profile"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", "BlazeLauncher/0.7.8 (Zalith-Parity)")
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                throw IOException("HTTP ${res.code}: ${res.body?.string().orEmpty()}")
            }
            val body = res.body?.string() ?: throw IOException("Empty profile body")
            val json = JSONObject(body)
            val id = json.optString("id")
            val name = json.optString("name")

            val skinList = mutableListOf<PlayerSkin>()
            val skinsArray = json.optJSONArray("skins")
            if (skinsArray != null) {
                for (i in 0 until skinsArray.length()) {
                    val s = skinsArray.getJSONObject(i)
                    skinList.add(
                        PlayerSkin(
                            id = s.optString("id"),
                            state = s.optString("state", "INACTIVE"),
                            url = s.optString("url"),
                            variant = s.optString("variant", "CLASSIC")
                        )
                    )
                }
            }

            val capeList = mutableListOf<PlayerCape>()
            val capesArray = json.optJSONArray("capes")
            if (capesArray != null) {
                for (i in 0 until capesArray.length()) {
                    val c = capesArray.getJSONObject(i)
                    capeList.add(
                        PlayerCape(
                            id = c.optString("id"),
                            state = c.optString("state", "INACTIVE"),
                            url = c.optString("url"),
                            alias = c.optString("alias", "Mojang Cape")
                        )
                    )
                }
            }

            NetLog.logSuccess("Minecraft Services Profile", url, 1)
            PlayerProfile(id, name, skinList, capeList)
        }
    }

    /**
     * Fetches authentic Microsoft player textures (Skin + Capes list) with zero Mojang rate-limiting.
     */
    suspend fun fetchMicrosoftProfileWithTextures(accessToken: String): SkinCapeResult? = withContext(Dispatchers.IO) {
        try {
            val profile = fetchPlayerProfile(accessToken)
            val activeSkin = profile.activeSkin ?: throw IOException("No active skin in profile")
            val rawSkinBmp = downloadBitmap(activeSkin.url) ?: throw IOException("Failed to download active skin bitmap from ${activeSkin.url}")
            val modernSkin = if (rawSkinBmp.width == 64 && rawSkinBmp.height == 32) convertLegacySkin(rawSkinBmp) else rawSkinBmp
            val model = if (activeSkin.variant.equals("SLIM", ignoreCase = true) || isSlimArm(modernSkin)) "slim" else "classic"

            val activeCape = profile.activeCape
            val capeBmp = activeCape?.let { downloadBitmap(it.url) }

            NetLog.logSuccess("Microsoft Full Textures", activeSkin.url, 1)
            SkinCapeResult(
                skin = modernSkin,
                model = model,
                cape = capeBmp,
                capeName = activeCape?.alias,
                capes = profile.capes,
                profile = profile
            )
        } catch (e: Throwable) {
            NetLog.logError("Microsoft Full Textures", "$MINECRAFT_SERVICES_URL/minecraft/profile", e)
            null
        }
    }

    /**
     * Downloads active skin and cape from Mojang Session Server by UUID.
     * Endpoint: https://sessionserver.mojang.com/session/minecraft/profile/<uuid>
     */
    suspend fun fetchMojangSessionProfile(uuid: String): SkinCapeResult? = withContext(Dispatchers.IO) {
        val cleanUuid = uuid.replace("-", "").trim()
        val url = "$SESSIONS_SERVER_URL/session/minecraft/profile/$cleanUuid"
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "BlazeLauncher/0.7.8 (Zalith-Parity)")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}: ${res.message}")
                val body = res.body?.string() ?: throw IOException("Empty session profile body")
                val json = JSONObject(body)
                val properties = json.optJSONArray("properties") ?: throw IOException("No properties in profile")
                var texturesBase64: String? = null
                for (i in 0 until properties.length()) {
                    val p = properties.getJSONObject(i)
                    if (p.optString("name") == "textures") {
                        texturesBase64 = p.optString("value")
                        break
                    }
                }
                if (texturesBase64.isNullOrEmpty()) throw IOException("No textures base64 found")

                val decodedBytes = android.util.Base64.decode(texturesBase64, android.util.Base64.DEFAULT)
                val decodedJson = JSONObject(String(decodedBytes, Charsets.UTF_8))
                val texturesObj = decodedJson.optJSONObject("textures") ?: throw IOException("Invalid textures format")

                val skinObj = texturesObj.optJSONObject("SKIN") ?: throw IOException("No SKIN object in textures")
                val skinUrl = skinObj.optString("url")
                val metadata = skinObj.optJSONObject("metadata")
                val isSlim = metadata?.optString("model")?.equals("slim", ignoreCase = true) ?: false

                val skinBmp = downloadBitmap(skinUrl) ?: throw IOException("Could not download skin bitmap from $skinUrl")

                val capeObj = texturesObj.optJSONObject("CAPE")
                val capeUrl = capeObj?.optString("url")
                val capeBmp = if (!capeUrl.isNullOrEmpty()) downloadBitmap(capeUrl) else null

                val modernSkin = if (skinBmp.width == 64 && skinBmp.height == 32) convertLegacySkin(skinBmp) else skinBmp
                val model = if (isSlim || isSlimArm(modernSkin)) "slim" else "classic"

                NetLog.logSuccess("Mojang Session Textures", url, 1)
                SkinCapeResult(
                    skin = modernSkin,
                    model = model,
                    cape = capeBmp,
                    capeName = if (capeBmp != null) "Mojang Active Cape" else null,
                    capes = if (capeBmp != null && !capeUrl.isNullOrEmpty()) {
                        listOf(PlayerCape(id = "mojang_active", state = "ACTIVE", url = capeUrl, alias = "Active Cape"))
                    } else emptyList()
                )
            }
        } catch (e: Throwable) {
            NetLog.logError("Mojang Session Textures", url, e)
            null
        }
    }

    /**
     * Looks up Minecraft username to UUID, then fetches session textures.
     */
    suspend fun fetchProfileByName(username: String): SkinCapeResult? = withContext(Dispatchers.IO) {
        val cleanName = username.trim()
        val url = "https://api.mojang.com/users/profiles/minecraft/$cleanName"
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "BlazeLauncher/0.7.8 (Zalith-Parity)")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}: ${res.message}")
                val body = res.body?.string() ?: throw IOException("Empty profile body")
                val json = JSONObject(body)
                val uuid = json.optString("id")
                if (uuid.isEmpty()) throw IOException("No UUID for player $cleanName")
                fetchMojangSessionProfile(uuid)
            }
        } catch (e: Throwable) {
            NetLog.logError("Mojang Name Lookup", url, e)
            null
        }
    }

    /**
     * Downloads active skin and cape/cloak from Ely.by textures system.
     */
    suspend fun fetchElyBySkinAndCape(username: String): SkinCapeResult? = withContext(Dispatchers.IO) {
        val cleanName = username.trim()
        val skinUrl = "http://skinsystem.ely.by/textures/$cleanName"
        val capeUrl = "http://skinsystem.ely.by/cloaks/$cleanName"
        try {
            val skinBmp = downloadBitmap(skinUrl) ?: throw IOException("Could not download Ely.by skin")
            val capeBmp = downloadBitmap(capeUrl)
            val modernSkin = if (skinBmp.width == 64 && skinBmp.height == 32) convertLegacySkin(skinBmp) else skinBmp
            val model = if (isSlimArm(modernSkin)) "slim" else "classic"
            NetLog.logSuccess("Ely.by Textures", skinUrl, 1)
            SkinCapeResult(
                skin = modernSkin,
                model = model,
                cape = capeBmp,
                capeName = if (capeBmp != null) "Ely.by Cloak" else null,
                capes = if (capeBmp != null) {
                    listOf(PlayerCape(id = "elyby_cloak", state = "ACTIVE", url = capeUrl, alias = "Ely.by Cloak"))
                } else emptyList()
            )
        } catch (e: Throwable) {
            NetLog.logError("Ely.by Textures", skinUrl, e)
            null
        }
    }

    /**
     * Uploads skin to authentic Microsoft Minecraft account via official API (Zalith Launcher 2 Parity).
     * Endpoint: POST https://api.minecraftservices.com/minecraft/profile/skins
     */
    suspend fun uploadSkinToMicrosoft(
        accessToken: String,
        skinBitmap: Bitmap,
        isSlim: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "$MINECRAFT_SERVICES_URL/minecraft/profile/skins"
        try {
            val stream = ByteArrayOutputStream()
            skinBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()

            val mediaType = "image/png".toMediaTypeOrNull()
            val filePart = bytes.toRequestBody(mediaType)
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("variant", if (isSlim) "slim" else "classic")
                .addFormDataPart("file", "skin.png", filePart)
                .build()

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "BlazeLauncher/0.7.8 (Zalith-Parity)")
                .post(body)
                .build()

            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    val errBody = res.body?.string().orEmpty()
                    throw IOException("HTTP ${res.code}: $errBody")
                }
                NetLog.logSuccess("Upload Microsoft Skin", url, 1)
                Result.success("Skin uploaded successfully to Microsoft account!")
            }
        } catch (e: Throwable) {
            NetLog.logError("Upload Microsoft Skin", url, e)
            Result.failure(e)
        }
    }

    /**
     * Sets or resets the active cape on Microsoft account (Zalith Launcher 2 Parity).
     * Endpoint: PUT or DELETE https://api.minecraftservices.com/minecraft/profile/capes/active
     */
    suspend fun changeActiveCape(
        accessToken: String,
        capeId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "$MINECRAFT_SERVICES_URL/minecraft/profile/capes/active"
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "BlazeLauncher/0.7.8 (Zalith-Parity)")

            if (capeId.isBlank()) {
                reqBuilder.delete()
            } else {
                val jsonBody = JSONObject().put("capeId", capeId).toString()
                val mediaType = "application/json".toMediaTypeOrNull()
                reqBuilder.put(jsonBody.toRequestBody(mediaType))
            }

            client.newCall(reqBuilder.build()).execute().use { res ->
                if (!res.isSuccessful) {
                    val errBody = res.body?.string().orEmpty()
                    throw IOException("HTTP ${res.code}: $errBody")
                }
                val msg = if (capeId.isBlank()) "Cape removed successfully!" else "Cape activated successfully!"
                NetLog.logSuccess("Change Active Cape", url, 1)
                Result.success(msg)
            }
        } catch (e: Throwable) {
            NetLog.logError("Change Active Cape", url, e)
            Result.failure(e)
        }
    }

    /**
     * Safely downloads and decodes a Bitmap from an online URL, automatically upgrading
     * textures.minecraft.net to HTTPS.
     */
    fun downloadBitmap(url: String): Bitmap? {
        val safeUrl = if (url.startsWith("http://textures.minecraft.net/")) {
            url.replace("http://textures.minecraft.net/", "https://textures.minecraft.net/")
        } else url
        return try {
            val req = Request.Builder()
                .url(safeUrl)
                .header("User-Agent", "BlazeLauncher/0.7.8 (Zalith-Parity)")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val bytes = res.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Backward-compatible alias for Microsoft skin fetch.
     */
    suspend fun fetchMicrosoftSkin(accessToken: String): Pair<Bitmap, String>? = withContext(Dispatchers.IO) {
        val res = fetchMicrosoftProfileWithTextures(accessToken) ?: return@withContext null
        Pair(res.skin, res.model)
    }

    /**
     * Backward-compatible alias for Ely.by skin fetch.
     */
    suspend fun fetchElyBySkin(username: String): Pair<Bitmap, String>? = withContext(Dispatchers.IO) {
        val res = fetchElyBySkinAndCape(username) ?: return@withContext null
        Pair(res.skin, res.model)
    }
}
