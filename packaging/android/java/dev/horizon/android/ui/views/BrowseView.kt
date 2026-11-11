package dev.horizon.android.ui.views

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.bridge.CatalogItem
import dev.horizon.android.service.ModrinthService
import dev.horizon.android.ui.animation.pressBounce
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.components.HorizonPill
import dev.horizon.android.ui.theme.LocalBlazeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private object BrowseIconCache {
    val cache = LruCache<String, Bitmap>(128)
    val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

@Composable
fun BrowseProjectIcon(
    iconUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val colors = LocalBlazeColors.current
    var bitmap by remember(iconUrl) { mutableStateOf(iconUrl?.let { BrowseIconCache.cache.get(it) }) }

    LaunchedEffect(iconUrl) {
        if (iconUrl.isNullOrEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }
        val cached = BrowseIconCache.cache.get(iconUrl)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(iconUrl)
                    .header("User-Agent", "BlazeLauncher/0.7.8")
                    .build()
                BrowseIconCache.client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { stream ->
                            val decoded = BitmapFactory.decodeStream(stream)
                            if (decoded != null) {
                                BrowseIconCache.cache.put(iconUrl, decoded)
                                bitmap = decoded
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x26FFFFFF))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = title.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
            BasicText(
                text = initial,
                style = TextStyle(
                    color = colors.activePalette.primary,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
fun BrowseView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val coroutineScope = rememberCoroutineScope()
    val modrinthService = remember { ModrinthService() }

    var selectedType by remember { mutableStateOf("mods") }
    var selectedSource by remember { mutableStateOf("Modrinth") }
    var searchQuery by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshCatalog() {
        coroutineScope.launch {
            isLoading = true
            try {
                items = if (selectedSource == "Modrinth") {
                    modrinthService.searchProjects(
                        query = searchQuery,
                        type = selectedType,
                        version = "26.3"
                    )
                } else {
                    modrinthService.getHorizonCatalogFallback(
                        type = selectedType,
                        query = searchQuery
                    )
                }
            } catch (_: Throwable) {
                items = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedType, selectedSource, searchQuery) {
        refreshCatalog()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ROW 1: Controls Bar (Search Input + Filter Chips)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Input Field
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x26FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    BasicText(
                        text = "Search mods, shaders, packs...",
                        style = TextStyle(color = Color(0xFF64748B), fontSize = 11.sp)
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(colors.activePalette.primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Type Chips: Mods, Resource Packs, Shaders
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("mods" to "Mods", "packs" to "Resource Packs", "shaders" to "Shaders").forEach { (key, label) ->
                    val isSel = selectedType == key
                    HorizonPill(
                        backgroundColor = if (isSel) colors.activePalette.primary else Color(0x1AFFFFFF),
                        borderColor = if (isSel) colors.activePalette.primary else Color(0x26FFFFFF),
                        onClick = { selectedType = key }
                    ) {
                        BasicText(
                            text = label,
                            style = TextStyle(
                                color = if (isSel) Color(0xFF020617) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Source Chips: Modrinth, Horizon Built-in
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Modrinth", "Horizon").forEach { src ->
                    val isSel = selectedSource == src
                    HorizonPill(
                        backgroundColor = if (isSel) colors.activePalette.soft else Color(0x14FFFFFF),
                        borderColor = if (isSel) colors.activePalette.primary else Color(0x26FFFFFF),
                        onClick = { selectedSource = src }
                    ) {
                        BasicText(
                            text = src,
                            style = TextStyle(
                                color = if (isSel) colors.activePalette.primary else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // ROW 2: Scrollable Items List via LazyColumn
        HorizonGlass(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cornerRadius = 16.dp
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "Loading catalog from $selectedSource...",
                        style = TextStyle(color = colors.activePalette.primary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    )
                }
            } else if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(
                            text = "No packages found",
                            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                        BasicText(
                            text = "Try adjusting your query or toggle between Modrinth and Horizon built-in catalog",
                            style = TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BrowseProjectIcon(
                                iconUrl = item.iconUrl,
                                title = item.title,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BasicText(
                                        text = item.title,
                                        style = TextStyle(
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (item.unsupported) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0x33EF4444))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            BasicText(
                                                text = "INCOMPATIBLE",
                                                style = TextStyle(
                                                    color = Color(0xFFF87171),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                BasicText(
                                    text = item.summary,
                                    style = TextStyle(
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    ),
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicText(
                                        text = "${item.downloads / 1000}k downloads",
                                        style = TextStyle(
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    BasicText(
                                        text = "•",
                                        style = TextStyle(color = Color(0xFF475569), fontSize = 10.sp)
                                    )
                                    BasicText(
                                        text = item.source,
                                        style = TextStyle(
                                            color = colors.activePalette.primary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            // Install / Added Button
                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .pressBounce(onClick = {})
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (item.installed) Color(0x2634D399)
                                        else colors.activePalette.primary
                                    )
                                    .border(
                                        1.dp,
                                        if (item.installed) Color(0x6634D399) else colors.activePalette.primary,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                BasicText(
                                    text = if (item.installed) "Installed" else "Install",
                                    style = TextStyle(
                                        color = if (item.installed) Color(0xFF34D399) else Color(0xFF020617),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
