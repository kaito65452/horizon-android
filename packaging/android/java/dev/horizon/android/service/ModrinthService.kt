package dev.horizon.android.service

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dev.horizon.android.bridge.CatalogItem
import dev.horizon.android.diagnostics.NetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ModrinthHit(
    @SerializedName("project_id") val projectId: String,
    @SerializedName("project_type") val projectType: String,
    @SerializedName("slug") val slug: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("downloads") val downloads: Long,
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("client_side") val clientSide: String?,
    @SerializedName("server_side") val serverSide: String?
)

data class ModrinthSearchResponse(
    @SerializedName("hits") val hits: List<ModrinthHit>,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("total_hits") val totalHits: Int
)

class ModrinthService(
    private val baseUrl: String = "https://api.modrinth.com/v2",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "BlazeLauncher/0.7.8 (https://github.com/Sahur01-arch/horizon-android)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()
) {
    private val gson = Gson()

    suspend fun searchProjects(
        query: String = "",
        type: String = "mods",
        version: String = "26.3",
        offset: Int = 0,
        limit: Int = 20
    ): List<CatalogItem> = withContext(Dispatchers.IO) {
        val projectTypeFacet = when (type) {
            "packs" -> "project_type:resourcepack"
            "shaders" -> "project_type:shader"
            else -> "project_type:mod"
        }

        val facetsList = mutableListOf<List<String>>()
        facetsList.add(listOf(projectTypeFacet))
        if (version.isNotBlank()) {
            facetsList.add(listOf("versions:$version"))
        }
        val facetsJson = gson.toJson(facetsList)

        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("facets", facetsJson)
            .addQueryParameter("index", "relevance")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .build()

        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = gson.fromJson(bodyStr, ModrinthSearchResponse::class.java)
                val items = parsed.hits.map { hit ->
                    CatalogItem(
                        id = hit.projectId,
                        title = hit.title ?: hit.slug ?: "Unknown",
                        summary = hit.description.orEmpty(),
                        source = "Modrinth",
                        downloads = hit.downloads,
                        installed = false,
                        unsupported = hit.clientSide == "unsupported",
                        iconUrl = hit.iconUrl
                    )
                }
                NetLog.logSuccess("Modrinth", url.toString(), items.size)
                items
            }
        } catch (t: Throwable) {
            NetLog.logError("Modrinth", url.toString(), t)
            getHorizonCatalogFallback(type, query)
        }
    }

    fun getHorizonCatalogFallback(type: String, query: String = ""): List<CatalogItem> {
        val allItems = when (type) {
            "shaders" -> listOf(
                CatalogItem("comp-reimagined", "Complementary Reimagined", "Exceptional visuals and performance on mobile Vulkan.", "Horizon Built-in", 4820000, true, false),
                CatalogItem("bsl-shaders", "BSL Shaders", "Bright, colorful mobile lighting optimization.", "Horizon Built-in", 6510000, false, false)
            )
            "packs" -> listOf(
                CatalogItem("faithful-64x", "Faithful 64x", "Crisp high-resolution authentic textures.", "Horizon Built-in", 3200000, true, false),
                CatalogItem("bare-bones", "Bare Bones", "Minimalist trailer-style flat colors for mobile.", "Horizon Built-in", 1950000, false, false)
            )
            else -> listOf(
                CatalogItem("complex-voice-chat", "Complex Voice Chat (ported by Asano Saryu)", "Native 3D proximity UDP voice chat with RNNoise & AGC (ported by Asano Saryu)", "Horizon Built-in", 890000, true, false),
                CatalogItem("sodium-vulkan", "Sodium (Vulkan Mobile)", "Modern rendering engine replacement with chunk meshing speedup.", "Horizon Built-in", 12500000, true, false),
                CatalogItem("lithium-mobile", "Lithium Mobile", "Physics, chunk ticking, and mob AI optimization.", "Horizon Built-in", 9800000, true, false),
                CatalogItem("legacy-mod-sample", "Sample Native Mod (.hnmod)", "Native C++ module requiring custom Android compilation.", "Horizon Built-in", 31000, false, true)
            )
        }

        val filtered = if (query.isBlank()) allItems else allItems.filter {
            it.title.contains(query, ignoreCase = true) || it.summary.contains(query, ignoreCase = true)
        }
        NetLog.logSuccess("Horizon Catalog", "builtin://catalog/$type", filtered.size)
        return filtered
    }
}
