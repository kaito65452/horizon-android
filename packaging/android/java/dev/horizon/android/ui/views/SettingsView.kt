package dev.horizon.android.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import dev.horizon.android.ui.components.*
import dev.horizon.android.ui.theme.BlazePalettes
import dev.horizon.android.ui.theme.LocalBlazeColors

@Composable
fun SettingsView(
    onPaletteChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var activeCategory by remember { mutableStateOf("appearance") }

    // Settings States
    var selectedPalette by remember { mutableStateOf(colors.activePalette.key) }
    var backgroundMode by remember { mutableStateOf("mc") }
    var panelOpacity by remember { mutableFloatStateOf(72f) }
    var panelBlur by remember { mutableFloatStateOf(16f) }
    var memoryLimitMb by remember { mutableFloatStateOf(2048f) }
    var keepAliveEnabled by remember { mutableStateOf(true) }
    var complexVoiceChatEnabled by remember { mutableStateOf(true) }
    var rnnoiseEnabled by remember { mutableStateOf(true) }
    var touchSensitivity by remember { mutableFloatStateOf(60f) }
    var gyroSensitivity by remember { mutableFloatStateOf(40f) }
    var joystickDeadzone by remember { mutableFloatStateOf(15f) }
    var hapticFeedback by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }

    val categories = listOf(
        "appearance" to "Appearance",
        "engine" to "Engine & Memory",
        "keepalive" to "Downloads & Keep-alive",
        "controls" to "Controls",
        "account" to "Account",
        "storage" to "Storage",
        "experimental" to "Experimental",
        "about" to "About"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left: Category Sidebar (4 cols)
        HorizonGlass(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = "SETTINGS",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )

                categories.forEach { (catId, catTitle) ->
                    val isSelected = activeCategory == catId
                    val bg = if (isSelected) Color(0x26FFFFFF) else Color.Transparent
                    val textCol = if (isSelected) Color.White else Color(0xFF94A3B8)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { activeCategory = catId }
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = catTitle,
                            style = androidx.compose.ui.text.TextStyle(
                                color = textCol,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Right: Content Panel (8 cols)
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeCategory) {
                    "appearance" -> {
                        HorizonSectionHeader(tag = "THEME & SURFACE", title = "Color & Background")

                        // Accent Color Swatches
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Accent Palette",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            )
                            HorizonSwatchRow(
                                selectedKey = selectedPalette,
                                onSelect = {
                                    selectedPalette = it
                                    onPaletteChanged(it)
                                }
                            )
                        }

                        // Background Mode
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Background Mode",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            )
                            val bgOptions = listOf("Default art", "Animated", "Custom", "OLED black")
                            val bgKeys = listOf("mc", "shader", "custom", "oled")
                            val idx = bgKeys.indexOf(backgroundMode).coerceAtLeast(0)
                            HorizonSegmented(
                                options = bgOptions,
                                selectedIndex = idx,
                                onSelect = { backgroundMode = bgKeys[it] },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizonButton(
                            text = "Import Image / Video Background (SAF)",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { statusMessage = "Select background media file via SAF" }
                        )

                        // Panel Opacity & Blur Sliders
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    androidx.compose.foundation.text.BasicText(text = "Panel Opacity", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                                    androidx.compose.foundation.text.BasicText(text = "${panelOpacity.toInt()}%", style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                                }
                                HorizonSlider(
                                    value = panelOpacity,
                                    onValueChange = { panelOpacity = it },
                                    valueRange = 30f..95f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    androidx.compose.foundation.text.BasicText(text = "Panel Blur", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                                    androidx.compose.foundation.text.BasicText(text = "${panelBlur.toInt()}px", style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                                }
                                HorizonSlider(
                                    value = panelBlur,
                                    onValueChange = { panelBlur = it },
                                    valueRange = 0f..32f
                                )
                            }
                        }
                    }

                    "engine" -> {
                        HorizonSectionHeader(tag = "ENGINE", title = "Renderer & Memory")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.activePalette.soft)
                                    .border(1.dp, colors.activePalette.primary, RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    androidx.compose.foundation.text.BasicText(text = "Vulkan", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                    androidx.compose.foundation.text.BasicText(text = "Native GPU path", style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 9.sp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x1AFFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    androidx.compose.foundation.text.BasicText(text = "OpenGL ES", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                    androidx.compose.foundation.text.BasicText(text = "Fallback driver", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 9.sp))
                                }
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                androidx.compose.foundation.text.BasicText(text = "Memory Limit", style = androidx.compose.ui.text.TextStyle(color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                                androidx.compose.foundation.text.BasicText(text = "${memoryLimitMb.toInt()} MB", style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                            }
                            HorizonSlider(
                                value = memoryLimitMb,
                                onValueChange = { memoryLimitMb = it },
                                valueRange = 512f..4096f
                            )
                        }
                    }

                    "keepalive" -> {
                        HorizonSectionHeader(tag = "BACKGROUND PERSISTENCE", title = "Downloads & Keep-alive")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(text = "Foreground Service (Wake Lock + Wi-Fi Lock)", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                androidx.compose.foundation.text.BasicText(text = "Downloads and server sessions stay active when switching apps.", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                            }
                            HorizonSwitch(
                                checked = keepAliveEnabled,
                                onCheckedChange = {
                                    keepAliveEnabled = it
                                    BlazeNativeBridge.nativeSetKeepAliveEnabled(it)
                                }
                            )
                        }
                    }

                    "controls" -> {
                        HorizonSectionHeader(tag = "INPUT", title = "Touch & Gyroscope Controls")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Haptic Vibration", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = hapticFeedback, onCheckedChange = { hapticFeedback = it })
                        }

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                androidx.compose.foundation.text.BasicText(text = "Touch Sensitivity", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                                androidx.compose.foundation.text.BasicText(text = "${touchSensitivity.toInt()}%", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                            }
                            HorizonSlider(value = touchSensitivity, onValueChange = { touchSensitivity = it }, valueRange = 10f..100f)
                        }

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                androidx.compose.foundation.text.BasicText(text = "Gyro Sensitivity", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                                androidx.compose.foundation.text.BasicText(text = "${gyroSensitivity.toInt()}%", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                            }
                            HorizonSlider(value = gyroSensitivity, onValueChange = { gyroSensitivity = it }, valueRange = 10f..100f)
                        }
                    }

                    "account" -> {
                        HorizonSectionHeader(tag = "PROFILE", title = "Account Management")
                        androidx.compose.foundation.text.BasicText(text = "Player Profile: Player", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        androidx.compose.foundation.text.BasicText(text = "Offline account mode enabled. Sign in with Microsoft is optional.", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                        Spacer(Modifier.height(4.dp))
                        HorizonButton(text = "Link Microsoft Account", onClick = { statusMessage = "Opening Microsoft OAuth flow" })
                    }

                    "storage" -> {
                        HorizonSectionHeader(tag = "STORAGE", title = "Files and Backups")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HorizonButton(text = "Export Worlds (SAF)", modifier = Modifier.weight(1f), onClick = { BlazeNativeBridge.nativeExportWorlds() })
                            HorizonButton(text = "Export JSON Settings", modifier = Modifier.weight(1f), onClick = { statusMessage = "Settings exported" })
                        }
                    }

                    "experimental" -> {
                        HorizonSectionHeader(tag = "EXPERIMENTAL", title = "Experimental Features")

                        // Complex Voice Chat (ported by Asano Saryu)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "Complex Voice Chat (ported by Asano Saryu)",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Native 3D proximity UDP voice chat with RNNoise & AGC (ported by Asano Saryu)",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = complexVoiceChatEnabled,
                                onCheckedChange = {
                                    complexVoiceChatEnabled = it
                                    BlazeNativeBridge.nativeSetComplexVoiceChatConfig(it, rnnoiseEnabled)
                                }
                            )
                        }

                        // RNNoise Input Filter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "RNNoise Input Filter",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Deep learning microphone noise suppression",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = rnnoiseEnabled,
                                onCheckedChange = {
                                    rnnoiseEnabled = it
                                    BlazeNativeBridge.nativeSetComplexVoiceChatConfig(complexVoiceChatEnabled, it)
                                }
                            )
                        }
                    }

                    "about" -> {
                        HorizonSectionHeader(tag = "ABOUT", title = "Blaze Launcher 26.3")
                        androidx.compose.foundation.text.BasicText(text = "Minecraft Java Edition 26.3 Engine rewritten in Pure Rust (wgpu Vulkan)", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                        androidx.compose.foundation.text.BasicText(text = "Frontend: Lightweight Kotlin / Jetpack Compose Foundation (Landscape Stage)", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                        androidx.compose.foundation.text.BasicText(text = "Voice Chat: Complex Voice Chat (ported by Asano Saryu)", style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                        Spacer(Modifier.height(4.dp))
                        HorizonButton(text = "GitHub Repository", onClick = { statusMessage = "Opens GitHub repo" })
                    }
                }

                if (statusMessage.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.text.BasicText(
                        text = statusMessage,
                        style = androidx.compose.ui.text.TextStyle(color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
