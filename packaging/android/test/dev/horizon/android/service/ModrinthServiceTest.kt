package dev.horizon.android.service

import dev.horizon.android.diagnostics.NetLog
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class ModrinthServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: ModrinthService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "BlazeLauncher/0.7.8 (https://github.com/Sahur01-arch/horizon-android)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()
        service = ModrinthService(
            baseUrl = server.url("/v2").toString().removeSuffix("/"),
            client = client
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testSearchRequestQueryAndFacetsFormatting() = runBlocking {
        val mockJson = """
        {
            "hits": [
                {
                    "project_id": "AANobbMI",
                    "project_type": "mod",
                    "slug": "sodium",
                    "author": "jellysquid3",
                    "title": "Sodium",
                    "description": "Modern rendering engine replacement",
                    "downloads": 32541234,
                    "icon_url": null,
                    "client_side": "required",
                    "server_side": "unsupported"
                }
            ],
            "offset": 0,
            "limit": 20,
            "total_hits": 1
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val items = service.searchProjects(query = "sodium", type = "mods", version = "26.3")
        val req = server.takeRequest()

        val decodedPath = URLDecoder.decode(req.path ?: "", "UTF-8")
        assertTrue("Request path should contain query=sodium", decodedPath.contains("query=sodium"))
        assertTrue("Request path should contain project_type:mod facet", decodedPath.contains("project_type:mod"))
        assertTrue("Request path should contain versions:26.3 facet", decodedPath.contains("versions:26.3"))

        assertEquals("Should parse 1 item", 1, items.size)
        assertEquals("Sodium", items[0].title)
        assertEquals("AANobbMI", items[0].id)
        assertEquals(32541234L, items[0].downloads)
    }

    @Test
    fun testUserAgentHeader() = runBlocking {
        val mockJson = """{ "hits": [], "offset": 0, "limit": 20, "total_hits": 0 }"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        service.searchProjects()
        val req = server.takeRequest()

        assertEquals(
            "BlazeLauncher/0.7.8 (https://github.com/Sahur01-arch/horizon-android)",
            req.getHeader("User-Agent")
        )
        assertEquals("application/json", req.getHeader("Accept"))
    }

    @Test
    fun testResponseParsingAndNetLog() = runBlocking {
        val mockJson = """
        {
            "hits": [
                {
                    "project_id": "cvoice",
                    "project_type": "mod",
                    "slug": "complex-voice",
                    "author": "AsanoSaryu",
                    "title": "Complex Voice Chat (ported by Asano Saryu)",
                    "description": "Native 3D proximity UDP voice chat",
                    "downloads": 54000,
                    "icon_url": null,
                    "client_side": "required",
                    "server_side": "required"
                }
            ],
            "offset": 0,
            "limit": 20,
            "total_hits": 1
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val items = service.searchProjects(query = "voice", type = "mods")
        assertEquals(1, items.size)
        assertEquals("Complex Voice Chat (ported by Asano Saryu)", items[0].title)
        assertFalse(items[0].unsupported)

        val netEntry = NetLog.entries["Modrinth"]
        assertNotNull(netEntry)
        assertTrue(netEntry!!.status.contains("200"))
        assertEquals(1, netEntry.count)
    }

    @Test
    fun testErrorHandlingAndFallback() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val items = service.searchProjects(query = "", type = "mods")
        assertTrue("Should return fallback items on HTTP failure", items.isNotEmpty())
        assertTrue("Fallback should contain built-in packages", items.any { it.title.contains("Complex Voice Chat (ported by Asano Saryu)") })

        val modrinthEntry = NetLog.entries["Modrinth"]
        assertNotNull(modrinthEntry)
        assertTrue("NetLog should record error", modrinthEntry!!.status.contains("500") || modrinthEntry.status.contains("IllegalStateException"))

        val catalogEntry = NetLog.entries["Horizon Catalog"]
        assertNotNull(catalogEntry)
        assertTrue(catalogEntry!!.status.contains("200"))
    }
}
