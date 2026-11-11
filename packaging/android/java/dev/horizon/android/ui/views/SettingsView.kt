package dev.horizon.android.ui.views

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.ui.animation.verticalSlideFadeTransition
import dev.horizon.android.ui.components.*
import dev.horizon.android.ui.theme.BlazePalettes
import dev.horizon.android.ui.theme.LocalBlazeColors
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun SettingsView(
    onPaletteChanged: (String) -> Unit,
    onOpenAccountManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val context = LocalContext.current
    var activeCategory by remember { mutableStateOf("appearance") }

    // Persistent Config States
    var selectedPalette by remember { mutableStateOf(colors.activePalette.key) }
    var backgroundMode by remember { mutableStateOf("mc") }
    var panelOpacity by remember { mutableFloatStateOf(72f) }
    var panelBlur by remember { mutableFloatStateOf(16f) }
    var rendererMode by remember { mutableStateOf("VulkanCompatibility") }
    var memoryLimitMb by remember { mutableFloatStateOf(2048f) }
    var workerLimit by remember { mutableIntStateOf(0) }
    var keepAliveEnabled by remember { mutableStateOf(true) }
    var touchSensitivity by remember { mutableFloatStateOf(60f) }
    var gyroSensitivity by remember { mutableFloatStateOf(40f) }
    var hapticFeedback by remember { mutableStateOf(true) }
    var autoJump by remember { mutableStateOf(true) }
    var splitTouch by remember { mutableStateOf(true) }
    var playerName by remember { mutableStateOf("Player") }
    var serverAddress by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    // Experimental Feature States
    var complexVoiceChatEnabled by remember { mutableStateOf(true) }
    var rnnoiseEnabled by remember { mutableStateOf(true) }
    var zeroLatencyTouch by remember { mutableStateOf(true) }
    var fastChunkUpload by remember { mutableStateOf(true) }
    var hollowLeafCulling by remember { mutableStateOf(false) }
    var superPerfMode by remember { mutableStateOf(false) }
    var batteryPerfMode by remember { mutableStateOf(false) }
    var maxFpsMode by remember { mutableStateOf(false) }
    var recordPerformance by remember { mutableStateOf(false) }

    fun loadFromNativeConfig() {
        try {
            val jsonStr = BlazeNativeBridge.nativeGetConfigJson()
            val root = JSONObject(jsonStr)
            playerName = root.optString("name", "Player")
            serverAddress = root.optString("server", "")
            rendererMode = root.optString("renderer", "VulkanCompatibility")
            workerLimit = root.optInt("worker_limit", 0)
            memoryLimitMb = root.optInt("cache_budget_mib", 2048).toFloat()

            // Keepalive & Audio
            keepAliveEnabled = root.optBoolean("keepalive_enabled", true)
            complexVoiceChatEnabled = root.optBoolean("svc_enabled", true)
            rnnoiseEnabled = root.optBoolean("svc_rnnoise", true)

            // Experimental Flags
            zeroLatencyTouch = root.optBoolean("zero_latency_touch", true)
            fastChunkUpload = root.optBoolean("fast_chunk_upload", true)
            hollowLeafCulling = root.optBoolean("experimental_leaf_culling", false)
            superPerfMode = root.optBoolean("super_perf_mode", false)
            batteryPerfMode = root.optBoolean("battery_perf_mode", false)
            maxFpsMode = root.optBoolean("max_fps_mode", false)
            recordPerformance = root.optBoolean("record_performance", false)
        } catch (_: Throwable) {}
    }

    fun mutateAndSaveConfig(updater: (JSONObject) -> Unit) {
        try {
            val jsonStr = BlazeNativeBridge.nativeGetConfigJson()
            val root = JSONObject(jsonStr)
            updater(root)
            val saved = BlazeNativeBridge.nativeSaveConfigJson(root.toString())
            if (saved) {
                statusMessage = "Setting saved"
            }
        } catch (e: Throwable) {
            statusMessage = "Error saving: ${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        loadFromNativeConfig()
    }

    // REAL SAF: Custom Wallpaper Picker (Supports both Videos and Images)
    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val wallDir = File(context.filesDir, "wallpapers")
                wallDir.mkdirs()
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val uriStr = uri.toString().lowercase()
                val isVideo = mimeType.startsWith("video") || uriStr.endsWith(".mp4") || uriStr.endsWith(".webm") || uriStr.endsWith(".mkv")
                val targetFile = File(wallDir, if (isVideo) "custom_bg.mp4" else "custom_bg.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
                backgroundMode = "custom"
                mutateAndSaveConfig { root ->
                    val theme = root.optJSONObject("theme") ?: JSONObject()
                    theme.put("bg_style", "custom")
                    theme.put("custom_bg_uri", targetFile.absolutePath)
                    theme.put("custom_bg_type", if (isVideo) "video" else "image")
                    root.put("theme", theme)
                }
                statusMessage = if (isVideo) "Custom video wallpaper imported successfully." else "Custom photo wallpaper imported successfully."
            } catch (e: Exception) {
                statusMessage = "Import failed: ${e.message}"
            }
        }
    }

    // REAL SAF: Export Settings JSON
    val exportSettingsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    val json = BlazeNativeBridge.nativeGetConfigJson()
                    output.write(json.toByteArray(Charsets.UTF_8))
                }
                statusMessage = "Settings JSON exported successfully via SAF."
            } catch (e: Exception) {
                statusMessage = "Export failed: ${e.message}"
            }
        }
    }

    // REAL SAF: Export Worlds to ZIP
    val exportWorldsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                val savesDir = File(context.filesDir, "saves")
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        if (savesDir.exists() && savesDir.isDirectory) {
                            savesDir.walkTopDown().forEach { file ->
                                val relPath = file.relativeTo(savesDir).path
                                if (file.isFile) {
                                    zipOut.putNextEntry(ZipEntry(relPath))
                                    file.inputStream().use { it.copyTo(zipOut) }
                                    zipOut.closeEntry()
                                }
                            }
                        }
                    }
                }
                statusMessage = "Worlds exported to ZIP successfully via SAF."
            } catch (e: Exception) {
                statusMessage = "Export failed: ${e.message}"
            }
        }
    }

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
        // Left: Category Sidebar (Scrollable & auto-fit)
        HorizonGlass(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
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
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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

        // Right: Content Panel (Scrollable & auto-fit)
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
                AnimatedContent(
                    targetState = activeCategory,
                    transitionSpec = { verticalSlideFadeTransition() },
                    label = "settingsCategoryContent"
                ) { currentCat ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (currentCat) {
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
                                    mutateAndSaveConfig { root ->
                                        val theme = root.optJSONObject("theme") ?: JSONObject()
                                        val preset = when (it) {
                                            "cyan" -> 0
                                            "emerald" -> 1
                                            "crimson" -> 2
                                            "violet" -> 3
                                            else -> 0
                                        }
                                        theme.put("preset", preset)
                                        root.put("theme", theme)
                                    }
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
                                onSelect = {
                                    backgroundMode = bgKeys[it]
                                    mutateAndSaveConfig { root ->
                                        val theme = root.optJSONObject("theme") ?: JSONObject()
                                        theme.put("bg_style", bgKeys[it])
                                        root.put("theme", theme)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizonButton(
                            text = "Import Image / Video Background (SAF)",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                wallpaperPicker.launch(arrayOf("image/*", "video/mp4", "image/png", "image/jpeg"))
                            }
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
                                    onValueChange = {
                                        panelOpacity = it
                                        mutateAndSaveConfig { root ->
                                            val theme = root.optJSONObject("theme") ?: JSONObject()
                                            theme.put("glass_opacity", it / 100.0)
                                            root.put("theme", theme)
                                        }
                                    },
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
                                    onValueChange = {
                                        panelBlur = it
                                        mutateAndSaveConfig { root ->
                                            val theme = root.optJSONObject("theme") ?: JSONObject()
                                            theme.put("glass_blur", it.toDouble())
                                            root.put("theme", theme)
                                        }
                                    },
                                    valueRange = 0f..32f
                                )
                            }
                        }
                    }

                    "engine" -> {
                        HorizonSectionHeader(tag = "ENGINE", title = "Renderer & Memory")

                        // Renderer switch
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.compose.foundation.text.BasicText(text = "Graphics Backend", style = androidx.compose.ui.text.TextStyle(color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (rendererMode != "OpenGl") colors.activePalette.soft else Color(0x1AFFFFFF))
                                        .border(1.dp, if (rendererMode != "OpenGl") colors.activePalette.primary else Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                        .clickable {
                                            rendererMode = "VulkanCompatibility"
                                            mutateAndSaveConfig { root -> root.put("renderer", "VulkanCompatibility") }
                                        }
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        androidx.compose.foundation.text.BasicText(text = "Vulkan", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                        androidx.compose.foundation.text.BasicText(text = "Native GPU path (Recommended)", style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 9.sp))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (rendererMode == "OpenGl") colors.activePalette.soft else Color(0x1AFFFFFF))
                                        .border(1.dp, if (rendererMode == "OpenGl") colors.activePalette.primary else Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                        .clickable {
                                            rendererMode = "OpenGl"
                                            mutateAndSaveConfig { root -> root.put("renderer", "OpenGl") }
                                        }
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        androidx.compose.foundation.text.BasicText(text = "OpenGL ES", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                        androidx.compose.foundation.text.BasicText(text = "Compatibility fallback", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 9.sp))
                                    }
                                }
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                androidx.compose.foundation.text.BasicText(text = "Cache Budget (Memory)", style = androidx.compose.ui.text.TextStyle(color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                                androidx.compose.foundation.text.BasicText(text = "${memoryLimitMb.toInt()} MB", style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                            }
                            HorizonSlider(
                                value = memoryLimitMb,
                                onValueChange = {
                                    memoryLimitMb = it
                                    mutateAndSaveConfig { root -> root.put("cache_budget_mib", it.toInt()) }
                                },
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
                                    mutateAndSaveConfig { root -> root.put("keepalive_enabled", it) }
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
                            HorizonSwitch(
                                checked = hapticFeedback,
                                onCheckedChange = {
                                    hapticFeedback = it
                                    mutateAndSaveConfig { root ->
                                        val dev = root.optJSONObject("device") ?: JSONObject()
                                        dev.put("vibrate", it)
                                        root.put("device", dev)
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Auto-Jump", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(
                                checked = autoJump,
                                onCheckedChange = {
                                    autoJump = it
                                    mutateAndSaveConfig { root ->
                                        val dev = root.optJSONObject("device") ?: JSONObject()
                                        dev.put("auto_jump", it)
                                        root.put("device", dev)
                                    }
                                }
                            )
                        }

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                androidx.compose.foundation.text.BasicText(text = "Touch Sensitivity", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                                androidx.compose.foundation.text.BasicText(text = "${touchSensitivity.toInt()}%", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                            }
                            HorizonSlider(
                                value = touchSensitivity,
                                onValueChange = {
                                    touchSensitivity = it
                                    mutateAndSaveConfig { root ->
                                        val dev = root.optJSONObject("device") ?: JSONObject()
                                        dev.put("touch_sensitivity", it / 100.0)
                                        root.put("device", dev)
                                    }
                                },
                                valueRange = 10f..100f
                            )
                        }

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                androidx.compose.foundation.text.BasicText(text = "Gyro Sensitivity", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                                androidx.compose.foundation.text.BasicText(text = "${gyroSensitivity.toInt()}%", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                            }
                            HorizonSlider(
                                value = gyroSensitivity,
                                onValueChange = {
                                    gyroSensitivity = it
                                    mutateAndSaveConfig { root ->
                                        val dev = root.optJSONObject("device") ?: JSONObject()
                                        dev.put("gyro_sensitivity", it / 100.0)
                                        root.put("device", dev)
                                    }
                                },
                                valueRange = 10f..100f
                            )
                        }
                    }

                    "account" -> {
                        HorizonSectionHeader(tag = "PROFILE & IDENTITIES", title = "Account Management")
                        val activeType = BlazeNativeBridge.getActiveAccountType().uppercase()
                        val activeName = BlazeNativeBridge.getActiveAccountName()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x2638BDF8))
                                .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.activePalette.primary)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        androidx.compose.foundation.text.BasicText(
                                            text = activeType,
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color(0xFF020617),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicText(
                                        text = activeName,
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                androidx.compose.foundation.text.BasicText(
                                    text = "Active identity for Minecraft 26.3 • Skins, capes & offline parity synced",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        HorizonButton(
                            text = "MANAGE & SWITCH ACCOUNTS (MICROSOFT / ELY.BY / OFFLINE)",
                            accent = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (onOpenAccountManager != null) {
                                    onOpenAccountManager()
                                } else {
                                    statusMessage = "Select account from top bar pill"
                                }
                            }
                        )
                    }

                    "storage" -> {
                        HorizonSectionHeader(tag = "STORAGE", title = "Files and Backups")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HorizonButton(
                                text = "Export Worlds (SAF)",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    exportWorldsPicker.launch("horizon_worlds_backup.zip")
                                }
                            )
                            HorizonButton(
                                text = "Export JSON Settings (SAF)",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    exportSettingsPicker.launch("blaze_settings.json")
                                }
                            )
                        }
                    }

                    "experimental" -> {
                        HorizonSectionHeader(tag = "LABS & FLAGS", title = "Experimental Features")

                        // 1. Complex Voice Chat (ported by Asano Saryu)
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
                                    mutateAndSaveConfig { root -> root.put("svc_enabled", it) }
                                }
                            )
                        }

                        // 2. RNNoise Filter
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
                                    mutateAndSaveConfig { root -> root.put("svc_rnnoise", it) }
                                }
                            )
                        }

                        // 3. Zero Latency Touch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "Zero Latency Touch Pipeline",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Bypasses Android buffer queues for instantaneous touch response",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = zeroLatencyTouch,
                                onCheckedChange = {
                                    zeroLatencyTouch = it
                                    mutateAndSaveConfig { root -> root.put("zero_latency_touch", it) }
                                }
                            )
                        }

                        // 4. Fast Async Chunk Upload
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "Fast Async Chunk Upload",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Asynchronous multi-thread chunk mesh upload to GPU",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = fastChunkUpload,
                                onCheckedChange = {
                                    fastChunkUpload = it
                                    mutateAndSaveConfig { root -> root.put("fast_chunk_upload", it) }
                                }
                            )
                        }

                        // 5. Smart Hollow Leaf Culling
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "Smart Hollow Leaf Culling",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Cull hidden internal leaf faces for significant FPS boost",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = hollowLeafCulling,
                                onCheckedChange = {
                                    hollowLeafCulling = it
                                    mutateAndSaveConfig { root -> root.put("experimental_leaf_culling", it) }
                                }
                            )
                        }

                        // 6. Super Performance Profile
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "Super Performance Profile",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Aggressive low fidelity optimizations for maximum framerates",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = superPerfMode,
                                onCheckedChange = {
                                    superPerfMode = it
                                    mutateAndSaveConfig { root -> root.put("super_perf_mode", it) }
                                }
                            )
                        }

                        // 7. Battery Saver Profile
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "Battery Saver Profile",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Throttles draw loop to conserve device battery",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = batteryPerfMode,
                                onCheckedChange = {
                                    batteryPerfMode = it
                                    mutateAndSaveConfig { root -> root.put("battery_perf_mode", it) }
                                }
                            )
                        }

                        // 8. Uncapped Max FPS Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "Uncapped Max FPS Mode",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Removes frame pacing caps on high refresh rate displays",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = maxFpsMode,
                                onCheckedChange = {
                                    maxFpsMode = it
                                    mutateAndSaveConfig { root -> root.put("max_fps_mode", it) }
                                }
                            )
                        }

                        // 9. Record Performance Data
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "Record GPU Performance Data",
                                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Collect frame timing metrics in memory for diagnostics export",
                                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                            HorizonSwitch(
                                checked = recordPerformance,
                                onCheckedChange = {
                                    recordPerformance = it
                                    mutateAndSaveConfig { root -> root.put("record_performance", it) }
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
                        HorizonButton(
                            text = "GitHub Repository",
                            onClick = {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/kaito65452/horizon-android")
                                    ).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    statusMessage = "Could not open browser: ${e.message}"
                                }
                            }
                        )
                    }
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
