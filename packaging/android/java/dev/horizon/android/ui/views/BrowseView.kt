package dev.horizon.android.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.bridge.CatalogItem
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.components.HorizonPill
import dev.horizon.android.ui.theme.LocalBlazeColors
import org.json.JSONArray

@Composable
fun BrowseView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var selectedType by remember { mutableStateOf("mods") }
    var selectedSource by remember { mutableStateOf("Horizon") }
    var searchQuery by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    fun refreshItems() {
        try {
            val jsonStr = BlazeNativeBridge.nativeFetchCatalogMods(selectedSource, selectedType, searchQuery)
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CatalogItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CatalogItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        summary = obj.optString("summary"),
                        source = obj.optString("source"),
                        downloads = obj.optLong("downloads"),
                        installed = obj.optBoolean("installed"),
                        unsupported = obj.optBoolean("unsupported")
                    )
                )
            }
            items = list
        } catch (_: Throwable) {
            items = emptyList()
        }
    }

    LaunchedEffect(selectedType, selectedSource, searchQuery) {
        refreshItems()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter bar: Type chips & Source chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type chips: Mods, Resource Packs, Shaders
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("mods" to "Mods", "packs" to "Resource Packs", "shaders" to "Shaders").forEach { (key, label) ->
                    val isSel = selectedType == key
                    HorizonPill(
                        backgroundColor = if (isSel) colors.activePalette.primary else Color(0x1AFFFFFF),
                        borderColor = if (isSel) colors.activePalette.primary else Color(0x26FFFFFF),
                        onClick = { selectedType = key }
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = label,
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isSel) Color(0xFF020617) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Source chips: Horizon, Modrinth
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Horizon", "Modrinth").forEach { src ->
                    val isSel = selectedSource == src
                    HorizonPill(
                        backgroundColor = if (isSel) colors.activePalette.soft else Color(0x14FFFFFF),
                        borderColor = if (isSel) colors.activePalette.primary else Color(0x26FFFFFF),
                        onClick = { selectedSource = src }
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = src,
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isSel) colors.activePalette.primary else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Project Items List
        HorizonGlass(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    text = item.title,
                                    style = androidx.compose.ui.text.TextStyle(
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
                                            .border(1.dp, Color(0x66EF4444), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        androidx.compose.foundation.text.BasicText(
                                            text = "UNSUPPORTED",
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            androidx.compose.foundation.text.BasicText(
                                text = item.summary,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.foundation.text.BasicText(
                                text = "${item.source} • ${item.downloads / 1000}k downloads",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = colors.activePalette.primary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        // Install button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        item.unsupported -> Color(0x1AFFFFFF)
                                        item.installed -> Color(0x2610B981)
                                        else -> colors.activePalette.primary
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = when {
                                    item.unsupported -> "Disabled"
                                    item.installed -> "Installed"
                                    else -> "Install"
                                },
                                style = androidx.compose.ui.text.TextStyle(
                                    color = when {
                                        item.unsupported -> Color(0xFF64748B)
                                        item.installed -> Color(0xFF34D399)
                                        else -> Color(0xFF020617)
                                    },
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
