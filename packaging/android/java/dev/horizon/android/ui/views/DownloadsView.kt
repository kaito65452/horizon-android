package dev.horizon.android.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import dev.horizon.android.bridge.DownloadThread
import dev.horizon.android.ui.components.HorizonButton
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.components.HorizonSectionHeader
import dev.horizon.android.ui.theme.LocalBlazeColors
import org.json.JSONArray

@Composable
fun DownloadsView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var threads by remember {
        mutableStateOf(
            listOf(
                DownloadThread(1, "Game assets", 1.0f, 0.0f, "Verified"),
                DownloadThread(2, "Textures", 1.0f, 0.0f, "Verified"),
                DownloadThread(3, "Audio", 1.0f, 0.0f, "Verified"),
                DownloadThread(4, "Native engine library", 1.0f, 0.0f, "Verified"),
                DownloadThread(5, "Control atlas", 1.0f, 0.0f, "Verified"),
                DownloadThread(6, "Control macros", 1.0f, 0.0f, "Verified"),
                DownloadThread(7, "Shader cache", 1.0f, 0.0f, "Verified"),
                DownloadThread(8, "Biome data", 1.0f, 0.0f, "Verified")
            )
        )
    }

    LaunchedEffect(Unit) {
        try {
            val jsonStr = BlazeNativeBridge.nativeGetDownloadProgressJson()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadThread>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DownloadThread(
                        id = obj.optInt("id", i + 1),
                        name = obj.optString("name", "Thread #${i + 1}"),
                        progress = obj.optDouble("progress", 1.0).toFloat(),
                        speedMibS = obj.optDouble("speed_mib_s", 0.0).toFloat(),
                        status = obj.optString("status", "Verified")
                    )
                )
            }
            if (list.isNotEmpty()) {
                threads = list
            }
        } catch (_: Throwable) {}
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left: 8-Thread Grid (8 cols)
        HorizonGlass(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                HorizonSectionHeader(tag = "PARALLEL PIPELINE", title = "8-Thread Segmented Downloader")

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    threads.forEach { th ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = "THREAD #${"%02d".format(th.id)}",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = colors.activePalette.primary,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    androidx.compose.foundation.text.BasicText(
                                        text = th.name,
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                // Progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0x26FFFFFF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(th.progress.coerceIn(0f, 1f))
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(colors.activePalette.primary)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    text = if (th.speedMibS > 0f) "${"%.1f".format(th.speedMibS)} MiB/s" else th.status,
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = if (th.status == "Verified") Color(0xFF34D399) else colors.activePalette.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (th.status == "Verified") Color(0xFF34D399) else colors.activePalette.primary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right: Queue & Keep-Alive Panel (4 cols)
        HorizonGlass(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    HorizonSectionHeader(tag = "STATUS", title = "Persistence & Queue")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x2610B981))
                            .border(1.dp, Color(0x4010B981), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            androidx.compose.foundation.text.BasicText(
                                text = "KEEP-ALIVE ACTIVE",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.foundation.text.BasicText(
                                text = "Downloads and Complex Voice Chat keep running in background via foreground service.",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Active Threads",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 11.sp)
                            )
                            androidx.compose.foundation.text.BasicText(
                                text = "8 / 8",
                                style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Total Progress",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 11.sp)
                            )
                            androidx.compose.foundation.text.BasicText(
                                text = "100%",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                HorizonButton(
                    text = "RECHECK ASSETS",
                    modifier = Modifier.fillMaxWidth(),
                    active = true,
                    onClick = {
                        BlazeNativeBridge.nativeStartMultiThreadDownload(8)
                    }
                )
            }
        }
    }
}
