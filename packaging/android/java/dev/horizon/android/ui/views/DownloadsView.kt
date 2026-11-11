package dev.horizon.android.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun DownloadsView(
    onLaunchGame: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var isReady by remember { mutableStateOf(BlazeNativeBridge.areAssetsReady()) }
    var isActive by remember { mutableStateOf(false) }
    var stageText by remember { mutableStateOf("Ready to play") }
    var totalProgress by remember { mutableFloatStateOf(if (isReady) 1.0f else 0.0f) }
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
        while (true) {
            try {
                val jsonStr = BlazeNativeBridge.nativeGetDownloadProgressJson()
                val root = JSONObject(jsonStr)
                isReady = root.optBoolean("ready", BlazeNativeBridge.areAssetsReady())
                isActive = root.optBoolean("active", false)
                stageText = root.optString("stage", if (isReady) "Verified and ready to launch" else "Idle")
                totalProgress = root.optDouble("progress", if (isReady) 1.0 else 0.0).toFloat()

                val array = root.optJSONArray("threads")
                if (array != null) {
                    val list = mutableListOf<DownloadThread>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            DownloadThread(
                                id = obj.optInt("id", i + 1),
                                name = obj.optString("name", "Thread #${i + 1}"),
                                progress = obj.optDouble("progress", 1.0).toFloat(),
                                speedMibS = obj.optDouble("speed_mib_s", 0.0).toFloat(),
                                status = obj.optString("status", if (isReady) "Verified" else "Pending")
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        threads = list
                    }
                }
            } catch (_: Throwable) {}
            delay(if (isActive) 300L else 1000L)
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left: 8-Thread Grid (Scrollable & auto-fit)
        HorizonGlass(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                HorizonSectionHeader(tag = "PARALLEL PIPELINE", title = "8-Thread Segmented Downloader")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    threads.forEach { th ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
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
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                // Progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0x26FFFFFF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(th.progress.coerceIn(0.001f, 1f))
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(colors.activePalette.primary)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    text = if (th.speedMibS > 0f) "${"%.1f".format(th.speedMibS)} MiB/s" else th.status,
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = if (th.status == "Verified") Color(0xFF34D399) else colors.activePalette.primary,
                                        fontSize = 9.sp,
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

        // Right: Queue & Keep-Alive Panel (Scrollable & auto-fit)
        HorizonGlass(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    HorizonSectionHeader(tag = "STATUS", title = "Persistence & Queue")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isReady) Color(0x2610B981) else Color(0x2638BDF8))
                            .border(1.dp, if (isReady) Color(0x4010B981) else Color(0x4038BDF8), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            androidx.compose.foundation.text.BasicText(
                                text = if (isReady) "READY FOR LAUNCH" else if (isActive) "DOWNLOADING ASSETS" else "STANDBY",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = if (isReady) Color(0xFF34D399) else colors.activePalette.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Spacer(Modifier.height(3.dp))
                            androidx.compose.foundation.text.BasicText(
                                text = stageText,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

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
                                text = if (isActive) "8 / 8" else if (isReady) "8 Verified" else "0 / 8",
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
                                text = "${(totalProgress * 100).toInt()}%",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = if (isReady) Color(0xFF34D399) else colors.activePalette.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isReady && onLaunchGame != null) {
                        HorizonButton(
                            text = "LAUNCH GAME",
                            modifier = Modifier.fillMaxWidth(),
                            accent = true,
                            onClick = { onLaunchGame() }
                        )
                    }

                    HorizonButton(
                        text = if (isActive) "DOWNLOADING..." else if (isReady) "RECHECK ASSETS" else "START DOWNLOAD (8 THREADS)",
                        modifier = Modifier.fillMaxWidth(),
                        active = !isActive,
                        accent = !isReady,
                        onClick = {
                            if (!isActive) {
                                BlazeNativeBridge.nativeStartMultiThreadDownload(8)
                            }
                        }
                    )
                }
            }
        }
    }
}
