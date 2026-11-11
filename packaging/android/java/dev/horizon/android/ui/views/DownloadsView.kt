package dev.horizon.android.ui.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.bridge.AssetComponent
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.ui.animation.EaseOutFluent
import dev.horizon.android.ui.components.HorizonButton
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.components.HorizonSectionHeader
import dev.horizon.android.ui.theme.LocalBlazeColors
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Clean, authentic Minecraft 26.3 Asset Pipeline and Download Hub.
 * Displays genuine byte progress, real download transfer rate, and verified on-disk components.
 */
@Composable
fun DownloadsView(
    onLaunchGame: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val context = LocalContext.current

    var isReady by remember { mutableStateOf(BlazeNativeBridge.areAssetsReady()) }
    var isActive by remember { mutableStateOf(false) }
    var stageText by remember { mutableStateOf("Ready to play") }
    var totalProgress by remember { mutableFloatStateOf(if (isReady) 1.0f else 0.0f) }
    var speedMibS by remember { mutableFloatStateOf(0.0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var doneBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }

    var components by remember {
        mutableStateOf(
            listOf(
                AssetComponent("Minecraft 26.3 Runtime JARs", "Official client and server engine binaries", isReady, if (isReady) "Verified" else "Pending"),
                AssetComponent("Extracted Assets & Texture Atlas", "Block models, entity states, fonts and shaders", isReady, if (isReady) "Verified" else "Pending"),
                AssetComponent("Audio Subsystem & Sound Index", "Vanilla music, sound effects, voice chat index", isReady, if (isReady) "Verified" else "Pending"),
                AssetComponent("Engine Data & Touch Atlas", "Touch controls, WGSL cache, baseline configs", true, "Verified")
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
                speedMibS = root.optDouble("speed_mib_s", 0.0).toFloat()

                val err = root.optString("error", "").trim()
                errorMessage = if (err.isNotEmpty() && err != "null" && err != "None") err else null

                doneBytes = root.optLong("done_bytes", root.optLong("done", 0L))
                totalBytes = root.optLong("total_bytes", root.optLong("total", 0L))

                val compArray = root.optJSONArray("components")
                if (compArray != null && compArray.length() > 0) {
                    val list = mutableListOf<AssetComponent>()
                    for (i in 0 until compArray.length()) {
                        val obj = compArray.getJSONObject(i)
                        list.add(
                            AssetComponent(
                                name = obj.optString("name", "Asset Package"),
                                desc = obj.optString("desc", ""),
                                isReady = obj.optBoolean("ready", false),
                                status = obj.optString("status", "Pending")
                            )
                        )
                    }
                    components = list
                }
            } catch (_: Throwable) {}
            delay(if (isActive) 300L else 1200L)
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Column: Real Progress Hero & Installed Components (weight 1.45f)
        Column(
            modifier = Modifier
                .weight(1.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hero Card: Genuine Progress & Telemetry
            HorizonGlass(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Row: Status Badge & Live Transfer Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val badgeBg = if (isReady) Color(0x2610B981) else if (isActive) Color(0x2638BDF8) else Color(0x1AFFFFFF)
                        val badgeBorder = if (isReady) Color(0x4D10B981) else if (isActive) colors.activePalette.primary else Color(0x26FFFFFF)
                        val badgeText = if (isReady) Color(0xFF34D399) else if (isActive) colors.activePalette.primary else Color(0xFF94A3B8)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .border(1.dp, badgeBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = if (isReady) "READY TO PLAY" else if (isActive) "DOWNLOADING ASSETS" else "STANDBY",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = badgeText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        // Real Speed Display
                        if (isActive && speedMibS > 0.05f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.activePalette.primary)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "${"%.1f".format(speedMibS)} MiB/s",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = colors.activePalette.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        } else if (isReady) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Assets Verified ✓",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    // Stage Name / Subtitle
                    androidx.compose.foundation.text.BasicText(
                        text = stageText,
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    // Large Fluid Progress Bar
                    val animatedProg by animateFloatAsState(
                        targetValue = totalProgress.coerceIn(0f, 1f),
                        animationSpec = tween(280, easing = EaseOutFluent(3)),
                        label = "mainDownloadProg"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x26FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProg)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isReady) Color(0xFF10B981) else colors.activePalette.primary)
                        )
                    }

                    // Bottom Stats: Bytes & Percentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = if (totalBytes > 0L) {
                                "${"%.1f".format(doneBytes / (1024f * 1024f))} / ${"%.1f".format(totalBytes / (1024f * 1024f))} MiB"
                            } else if (isReady) {
                                "Minecraft: Java Edition 26.3 Runtime"
                            } else {
                                "Download cache ready"
                            },
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        androidx.compose.foundation.text.BasicText(
                            text = "${(totalProgress * 100).toInt()}%",
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isReady) Color(0xFF34D399) else colors.activePalette.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            // Real Components Card (No fake threads)
            HorizonGlass(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    HorizonSectionHeader(tag = "INSTALLED PACKAGES", title = "Real Game Components")

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        components.forEach { comp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x14FFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Status Indicator Dot
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (comp.isReady) Color(0xFF34D399)
                                                else if (isActive) colors.activePalette.primary
                                                else Color(0xFF64748B)
                                            )
                                    )

                                    Column {
                                        androidx.compose.foundation.text.BasicText(
                                            text = comp.name,
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        if (comp.desc.isNotEmpty()) {
                                            androidx.compose.foundation.text.BasicText(
                                                text = comp.desc,
                                                style = androidx.compose.ui.text.TextStyle(
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 9.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                // Status Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (comp.isReady) Color(0x2610B981) else Color(0x1AFFFFFF))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = comp.status,
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = if (comp.isReady) Color(0xFF34D399) else Color(0xFFCBD5E1),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Column: System Integrity & Actions (weight 1f)
        HorizonGlass(
            modifier = Modifier
                .weight(1f)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizonSectionHeader(tag = "INTEGRITY", title = "Storage & Environment")

                    // Error Banner (if real error occurred)
                    if (errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x33EF4444))
                                .border(1.dp, Color(0x66EF4444), RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                androidx.compose.foundation.text.BasicText(
                                    text = "PIPELINE NOTICE",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = Color(0xFFF87171),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                androidx.compose.foundation.text.BasicText(
                                    text = errorMessage ?: "Unknown error",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }

                    // System Specs & Storage Breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Game Engine",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                            )
                            androidx.compose.foundation.text.BasicText(
                                text = "Horizon 26.3 (Rust/wgpu)",
                                style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Architecture",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                            )
                            androidx.compose.foundation.text.BasicText(
                                text = "aarch64 (ARM64-v8a)",
                                style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Data Directory",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                            )
                            androidx.compose.foundation.text.BasicText(
                                text = ".local/horizon-client/26.3",
                                style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Installation Status",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                            )
                            androidx.compose.foundation.text.BasicText(
                                text = if (isReady) "Verified & Active" else "Pending Assets",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = if (isReady) Color(0xFF34D399) else colors.activePalette.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Action Buttons
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
                        text = if (isActive) {
                            "DOWNLOADING (${(totalProgress * 100).toInt()}%)..."
                        } else if (errorMessage != null) {
                            "RETRY ASSET DOWNLOAD"
                        } else if (isReady) {
                            "RECHECK ASSET INTEGRITY"
                        } else {
                            "DOWNLOAD GAME ASSETS"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        active = !isActive,
                        accent = !isReady,
                        onClick = {
                            if (!isActive) {
                                BlazeNativeBridge.ensureEngineData(context)
                                BlazeNativeBridge.nativeStartMultiThreadDownload(8)
                            }
                        }
                    )
                }
            }
        }
    }
}
