package dev.horizon.android.ui.views

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.service.PlayerCape
import dev.horizon.android.service.PlayerProfile
import dev.horizon.android.service.SkinCapeResult
import dev.horizon.android.service.WardrobeService
import dev.horizon.android.ui.animation.pressBounce
import dev.horizon.android.ui.components.*
import dev.horizon.android.ui.theme.LocalBlazeColors
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D Minecraft Character Studio and Wardrobe.
 * Implements genuine 3D cuboid projection with depth sorting, dual-layer inflation,
 * official skin/cape presets, and touch orbit drag based on Zalith Launcher 2 and Horizon preview.rs.
 */
@Composable
fun SkinStudioView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeName by remember { mutableStateOf(BlazeNativeBridge.getActiveAccountName()) }
    var activeType by remember { mutableStateOf(BlazeNativeBridge.getActiveAccountType()) }
    var activeUUID by remember { mutableStateOf(BlazeNativeBridge.getActiveAccountUUID()) }
    var isOperating by remember { mutableStateOf(false) }

    var rotationYaw by remember { mutableFloatStateOf(-25f) }
    var rotationPitch by remember { mutableFloatStateOf(-6f) }
    var animState by remember { mutableStateOf("walk") } // walk, idle, crouch
    var armGeometry by remember { mutableStateOf("classic") } // classic (4px), slim (3px)
    var selectedPreset by remember { mutableStateOf("steve") }
    var selectedCapePreset by remember { mutableStateOf("none") }
    var activeCapeId by remember { mutableStateOf("") }
    var accountCapes by remember { mutableStateOf<List<PlayerCape>>(emptyList()) }

    // 64x64 Dual-Layer Toggles
    var hatLayer by remember { mutableStateOf(true) }
    var jacketLayer by remember { mutableStateOf(true) }
    var sleeveLayer by remember { mutableStateOf(true) }
    var pantsLayer by remember { mutableStateOf(true) }

    var statusMessage by remember { mutableStateOf("") }
    var skinBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun loadDefaultSteve() {
        val bmp = try {
            context.assets.open("skin/steve.png").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) { null } ?: createSteveTexture()
        skinBitmap = bmp
        armGeometry = "classic"
        selectedPreset = "steve"
    }

    fun loadDefaultAlex() {
        val bmp = try {
            context.assets.open("skin/alex.png").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) { null } ?: createAlexTexture()
        skinBitmap = bmp
        armGeometry = "slim"
        selectedPreset = "alex"
    }

    // Load existing skin and account info on startup
    LaunchedEffect(Unit) {
        try {
            activeName = BlazeNativeBridge.getActiveAccountName()
            activeType = BlazeNativeBridge.getActiveAccountType()
            activeUUID = BlazeNativeBridge.getActiveAccountUUID()

            val wardrobeSkin = File(context.filesDir, "wardrobe/skins/current.png")
            if (wardrobeSkin.exists()) {
                val bmp = BitmapFactory.decodeFile(wardrobeSkin.absolutePath)
                if (bmp != null) {
                    skinBitmap = bmp
                    armGeometry = if (WardrobeService.isSlimArm(bmp)) "slim" else "classic"
                    selectedPreset = "custom"
                } else {
                    loadDefaultSteve()
                }
            } else {
                val isMicrosoft = activeType.equals("microsoft", ignoreCase = true)
                val token = if (isMicrosoft) BlazeNativeBridge.getActiveAccessToken() else ""
                if (isMicrosoft && token.isNotEmpty()) {
                    val res = WardrobeService.fetchMicrosoftProfileWithTextures(token)
                    if (res != null) {
                        skinBitmap = res.skin
                        armGeometry = res.model
                        capeBitmap = res.cape
                        accountCapes = res.capes
                        selectedPreset = "account"
                        selectedCapePreset = res.capeName ?: "none"
                        activeCapeId = res.profile?.activeCape?.id ?: ""
                    } else {
                        loadDefaultSteve()
                    }
                } else {
                    loadDefaultSteve()
                }
            }

            val wardrobeCape = File(context.filesDir, "wardrobe/capes/current.png")
            if (wardrobeCape.exists() && capeBitmap == null) {
                val cape = BitmapFactory.decodeFile(wardrobeCape.absolutePath)
                if (cape != null) {
                    capeBitmap = cape
                    selectedCapePreset = "custom"
                }
            }
        } catch (_: Throwable) {
            loadDefaultSteve()
        }
    }

    // REAL SAF: Skin Picker
    val skinPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val skinsDir = File(context.filesDir, "wardrobe/skins")
                    skinsDir.mkdirs()
                    val target = File(skinsDir, "current.png")
                    target.outputStream().use { output -> input.copyTo(output) }

                    var bmp = BitmapFactory.decodeFile(target.absolutePath)
                    if (bmp != null) {
                        if (bmp.width == 64 && bmp.height == 32) {
                            bmp = WardrobeService.convertLegacySkin(bmp)
                            target.outputStream().use { output ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, output)
                            }
                        }
                        skinBitmap = bmp
                        armGeometry = if (WardrobeService.isSlimArm(bmp)) "slim" else "classic"
                        selectedPreset = "custom"
                        statusMessage = "Imported custom ${bmp.width}x${bmp.height} skin via SAF"
                    } else {
                        statusMessage = "Failed to decode skin PNG"
                    }
                }
            } catch (e: Exception) {
                statusMessage = "SAF error: ${e.message}"
            }
        }
    }

    // REAL SAF: Cape Picker
    val capePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val capesDir = File(context.filesDir, "wardrobe/capes")
                    capesDir.mkdirs()
                    val target = File(capesDir, "current.png")
                    target.outputStream().use { output -> input.copyTo(output) }

                    val bmp = BitmapFactory.decodeFile(target.absolutePath)
                    if (bmp != null) {
                        capeBitmap = bmp
                        selectedCapePreset = "custom"
                        statusMessage = "Loaded custom cape via SAF"
                    }
                }
            } catch (e: Exception) {
                statusMessage = "SAF error: ${e.message}"
            }
        }
    }

    val faceCache = remember(skinBitmap, capeBitmap, armGeometry) {
        val skin = skinBitmap ?: createSteveTexture()
        SkinFaceCache(skin, capeBitmap, armGeometry == "slim")
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Column: 3D Viewport
        HorizonGlass(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Viewport Top Status & Quick Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.activePalette.soft)
                            .border(1.dp, colors.activePalette.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "3D ISOMETRIC VIEWPORT",
                            style = androidx.compose.ui.text.TextStyle(
                                color = colors.activePalette.primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HorizonPill(onClick = {
                            animState = when (animState) {
                                "walk" -> "idle"
                                "idle" -> "crouch"
                                else -> "walk"
                            }
                        }) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Anim: ${animState.replaceFirstChar { it.uppercase() }}",
                                style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        HorizonPill(onClick = {
                            rotationYaw = -25f
                            rotationPitch = -6f
                        }) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Reset View",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 10.sp)
                            )
                        }
                    }
                }

                // Interactive 3D Canvas with Orbit Drag
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    rotationYaw = (rotationYaw + dragAmount.x * 0.7f).rem(360f)
                                    rotationPitch = (rotationPitch + dragAmount.y * 0.4f).coerceIn(-65f, 65f)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Skin3DRenderer(
                        faceCache = faceCache,
                        yaw = rotationYaw,
                        pitch = rotationPitch,
                        armSlim = armGeometry == "slim",
                        hatLayer = hatLayer,
                        jacketLayer = jacketLayer,
                        sleeveLayer = sleeveLayer,
                        pantsLayer = pantsLayer,
                        animState = animState
                    )
                }

                // Bottom Hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = "Touch drag to orbit 360° • Yaw: ${rotationYaw.toInt()}°",
                        style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                    )
                    androidx.compose.foundation.text.BasicText(
                        text = "${armGeometry.replaceFirstChar { it.uppercase() }} (64x64 3D)",
                        style = androidx.compose.ui.text.TextStyle(
                            color = colors.activePalette.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }

        // Right Column: Studio Controls & Presets
        HorizonGlass(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizonSectionHeader(tag = "WARDROBE", title = "Skin & Cape Options")

                    // Active Account Card & Online Sync
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardHeroBg)
                            .border(1.dp, colors.pillBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        androidx.compose.foundation.text.BasicText(
                                            text = activeName,
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        val isMs = activeType.equals("microsoft", ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isMs) Color(0xFF00A4EF).copy(alpha = 0.25f)
                                                    else colors.activePalette.soft
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isMs) Color(0xFF00A4EF) else colors.activePalette.primary,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            androidx.compose.foundation.text.BasicText(
                                                text = activeType.uppercase(),
                                                style = androidx.compose.ui.text.TextStyle(
                                                    color = if (isMs) Color(0xFF38BDF8) else colors.activePalette.primary,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            )
                                        }
                                    }
                                    if (activeUUID.isNotEmpty()) {
                                        androidx.compose.foundation.text.BasicText(
                                            text = "UUID: ${activeUUID.take(16)}...",
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color(0xFF64748B),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                }

                                HorizonPill(onClick = {
                                    activeName = BlazeNativeBridge.getActiveAccountName()
                                    activeType = BlazeNativeBridge.getActiveAccountType()
                                    activeUUID = BlazeNativeBridge.getActiveAccountUUID()
                                }) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = "Refresh",
                                        style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 9.sp)
                                    )
                                }
                            }

                            // Online Sync Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                HorizonButton(
                                    text = if (isOperating) "Fetching..." else "Fetch Account Skin",
                                    active = true,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (isOperating) return@HorizonButton
                                        coroutineScope.launch {
                                            isOperating = true
                                            statusMessage = "Fetching skin & capes for $activeName..."
                                            try {
                                                val isMicrosoft = activeType.equals("microsoft", ignoreCase = true)
                                                val token = if (isMicrosoft) BlazeNativeBridge.getActiveAccessToken() else ""
                                                val result: SkinCapeResult? = when {
                                                    isMicrosoft && token.isNotEmpty() -> {
                                                        WardrobeService.fetchMicrosoftProfileWithTextures(token)
                                                    }
                                                    isMicrosoft && activeUUID.isNotEmpty() -> {
                                                        WardrobeService.fetchMojangSessionProfile(activeUUID)
                                                    }
                                                    activeType.lowercase() in listOf("elyby", "ely.by") -> {
                                                        WardrobeService.fetchElyBySkinAndCape(activeName)
                                                    }
                                                    else -> {
                                                        WardrobeService.fetchProfileByName(activeName)
                                                    }
                                                }

                                                if (result != null) {
                                                    skinBitmap = result.skin
                                                    armGeometry = result.model
                                                    selectedPreset = "account"
                                                    accountCapes = result.capes
                                                    if (result.cape != null) {
                                                        capeBitmap = result.cape
                                                        selectedCapePreset = result.capeName ?: "account"
                                                        activeCapeId = result.profile?.activeCape?.id ?: ""
                                                        statusMessage = "Loaded ${result.model} skin & ${result.capeName ?: "cape"}!"
                                                    } else {
                                                        capeBitmap = null
                                                        activeCapeId = ""
                                                        selectedCapePreset = "none"
                                                        statusMessage = "Loaded ${result.model} skin for $activeName"
                                                    }
                                                } else {
                                                    statusMessage = "Could not find online skin for $activeName"
                                                }
                                            } catch (e: Exception) {
                                                statusMessage = "Fetch error: ${e.message}"
                                            } finally {
                                                isOperating = false
                                            }
                                        }
                                    }
                                )

                                val isMicrosoft = activeType.equals("microsoft", ignoreCase = true)
                                HorizonButton(
                                    text = if (isOperating) "Uploading..." else "Upload to Microsoft",
                                    accent = isMicrosoft,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (isOperating) return@HorizonButton
                                        if (!isMicrosoft) {
                                            statusMessage = "Active account is $activeType. Microsoft account required to upload."
                                            return@HorizonButton
                                        }
                                        val token = BlazeNativeBridge.getActiveAccessToken()
                                        if (token.isEmpty()) {
                                            statusMessage = "No Microsoft access token. Re-login in Accounts dialog."
                                            return@HorizonButton
                                        }
                                        val current = skinBitmap
                                        if (current == null) {
                                            statusMessage = "No skin loaded in studio to upload."
                                            return@HorizonButton
                                        }
                                        coroutineScope.launch {
                                            isOperating = true
                                            statusMessage = "Uploading ${armGeometry} skin to Microsoft account..."
                                            try {
                                                val res = WardrobeService.uploadSkinToMicrosoft(
                                                    accessToken = token,
                                                    skinBitmap = current,
                                                    isSlim = armGeometry == "slim"
                                                )
                                                if (res.isSuccess) {
                                                    statusMessage = res.getOrNull() ?: "Skin uploaded successfully to Microsoft account!"
                                                    val updated = WardrobeService.fetchMicrosoftProfileWithTextures(token)
                                                    if (updated != null) {
                                                        skinBitmap = updated.skin
                                                        armGeometry = updated.model
                                                        accountCapes = updated.capes
                                                        if (updated.cape != null) {
                                                            capeBitmap = updated.cape
                                                        }
                                                    }
                                                } else {
                                                    statusMessage = "Upload error: ${res.exceptionOrNull()?.message}"
                                                }
                                            } catch (e: Exception) {
                                                statusMessage = "Upload error: ${e.message}"
                                            } finally {
                                                isOperating = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Skin Presets Row
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        androidx.compose.foundation.text.BasicText(
                            text = "Skin Presets",
                            style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PresetPill(
                                label = "Steve (Classic)",
                                active = selectedPreset == "steve",
                                onClick = { loadDefaultSteve() },
                                modifier = Modifier.weight(1f)
                            )
                            PresetPill(
                                label = "Alex (Slim)",
                                active = selectedPreset == "alex",
                                onClick = { loadDefaultAlex() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Capes Row (Account Capes / Authentic Capes)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = if (accountCapes.isNotEmpty()) "Account Capes (${accountCapes.size})" else "Cape",
                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                            if (selectedCapePreset.isNotEmpty() && selectedCapePreset != "none") {
                                androidx.compose.foundation.text.BasicText(
                                    text = selectedCapePreset,
                                    style = androidx.compose.ui.text.TextStyle(color = colors.activePalette.primary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PresetPill(
                                label = "None",
                                active = capeBitmap == null || selectedCapePreset == "none",
                                onClick = {
                                    activeCapeId = ""
                                    capeBitmap = null
                                    selectedCapePreset = "none"
                                    val token = BlazeNativeBridge.getActiveAccessToken()
                                    if (activeType.equals("microsoft", ignoreCase = true) && token.isNotEmpty()) {
                                        coroutineScope.launch {
                                            val res = WardrobeService.changeActiveCape(token, "")
                                            if (res.isSuccess) statusMessage = "Cape reset on Microsoft account"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(0.7f)
                            )
                            if (accountCapes.isNotEmpty()) {
                                accountCapes.take(3).forEach { cape ->
                                    val isSelected = activeCapeId == cape.id || (activeCapeId.isEmpty() && cape.isUsing && capeBitmap != null)
                                    PresetPill(
                                        label = cape.alias.take(12),
                                        active = isSelected,
                                        onClick = {
                                            activeCapeId = cape.id
                                            selectedCapePreset = cape.alias
                                            coroutineScope.launch {
                                                val bmp = WardrobeService.downloadBitmap(cape.url)
                                                if (bmp != null) capeBitmap = bmp
                                                val token = BlazeNativeBridge.getActiveAccessToken()
                                                if (activeType.equals("microsoft", ignoreCase = true) && token.isNotEmpty()) {
                                                    val res = WardrobeService.changeActiveCape(token, cape.id)
                                                    if (res.isSuccess) statusMessage = "Activated ${cape.alias} on Microsoft account!"
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                PresetPill(
                                    label = if (capeBitmap != null) "Custom Cape" else "No Online Capes",
                                    active = capeBitmap != null,
                                    onClick = { capePicker.launch(arrayOf("image/png", "image/*")) },
                                    modifier = Modifier.weight(1.3f)
                                )
                            }
                        }
                    }

                    // Arm Geometry Segmented
                    Column {
                        androidx.compose.foundation.text.BasicText(
                            text = "Arm Geometry",
                            style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                        HorizonSegmented(
                            options = listOf("Slim (3px)", "Classic (4px)"),
                            selectedIndex = if (armGeometry == "slim") 0 else 1,
                            onSelect = { armGeometry = if (it == 0) "slim" else "classic" },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Dual-Layer Toggles
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        androidx.compose.foundation.text.BasicText(
                            text = "Outer 3D Overlays (64x64)",
                            style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Hat Layer", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = hatLayer, onCheckedChange = { hatLayer = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Jacket Layer", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = jacketLayer, onCheckedChange = { jacketLayer = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Sleeves Layer", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = sleeveLayer, onCheckedChange = { sleeveLayer = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Pants Layer", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = pantsLayer, onCheckedChange = { pantsLayer = it })
                        }
                    }

                    // SAF Import Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizonButton(
                            text = "Import Skin (SAF)",
                            modifier = Modifier.weight(1f),
                            onClick = { skinPicker.launch(arrayOf("image/png", "image/*")) }
                        )
                        HorizonButton(
                            text = "Import Cape",
                            modifier = Modifier.weight(1f),
                            onClick = { capePicker.launch(arrayOf("image/png", "image/*")) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (statusMessage.isNotEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        text = statusMessage,
                        style = androidx.compose.ui.text.TextStyle(
                            color = colors.activePalette.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Apply Action
                HorizonButton(
                    text = "APPLY SKIN & CAPE",
                    accent = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            val activeName = BlazeNativeBridge.getActiveAccountName()
                            val isSlim = armGeometry == "slim"
                            val deterministicUuid = BlazeNativeBridge.getLocalUUIDWithSkinModel(activeName, isSlim)

                            val skinsDir = File(context.filesDir, "wardrobe/skins")
                            skinsDir.mkdirs()
                            val target = File(skinsDir, "current.png")
                            skinBitmap?.let { bmp ->
                                target.outputStream().use { out ->
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                            }

                            val capesDir = File(context.filesDir, "wardrobe/capes")
                            capesDir.mkdirs()
                            val capeTarget = File(capesDir, "current.png")
                            if (capeBitmap != null && selectedCapePreset != "none") {
                                capeTarget.outputStream().use { out ->
                                    capeBitmap?.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                            } else if (selectedCapePreset == "none") {
                                if (capeTarget.exists()) capeTarget.delete()
                            }

                            val configJson = JSONObject(BlazeNativeBridge.nativeGetConfigJson())
                            configJson.put("custom_skin_path", target.absolutePath)
                            configJson.put("arm_geometry", armGeometry)
                            configJson.put("player_uuid", deterministicUuid)
                            BlazeNativeBridge.nativeSaveConfigJson(configJson.toString())
                            statusMessage = "Applied $armGeometry skin for $activeName ($deterministicUuid)"
                        } catch (e: Exception) {
                            statusMessage = "Error saving skin: ${e.message}"
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PresetPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val bg = if (active) colors.activePalette.primary else Color(0x1AFFFFFF)
    val fg = if (active) Color(0xFF020617) else Color(0xFFE2E8F0)

    Box(
        modifier = modifier
            .pressBounce(targetScale = 0.94f, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (active) colors.activePalette.primary else Color(0x26FFFFFF), RoundedCornerShape(8.dp))
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                color = fg,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

// -------------------------------------------------------------
// 3D Engine: Math Types, Projection, Lighting, and Skin Cuboids
// -------------------------------------------------------------

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    fun rotateX(pivotY: Float, pivotZ: Float, angleDeg: Float): Vec3 {
        if (angleDeg == 0f) return this
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val relY = y - pivotY
        val relZ = z - pivotZ
        return Vec3(
            x,
            pivotY + relY * cosA - relZ * sinA,
            pivotZ + relY * sinA + relZ * cosA
        )
    }

    fun project(yaw: Float, pitch: Float, viewWidth: Float, viewHeight: Float, zoom: Float): ProjectedPoint {
        val radYaw = Math.toRadians(yaw.toDouble())
        val cosY = cos(radYaw).toFloat()
        val sinY = sin(radYaw).toFloat()
        val xRot = x * cosY + z * sinY
        val zRot = -x * sinY + z * cosY

        val radPitch = Math.toRadians(pitch.toDouble())
        val cosP = cos(radPitch).toFloat()
        val sinP = sin(radPitch).toFloat()
        val yCent = y - 16.0f
        val yRot = yCent * cosP - zRot * sinP
        val zFinal = yCent * sinP + zRot * cosP

        val scale = viewHeight / 38.0f * zoom
        val perspective = 120.0f / (120.0f + zFinal)
        val screenX = viewWidth * 0.5f + xRot * scale * perspective
        val screenY = viewHeight * 0.5f - yRot * scale * perspective
        return ProjectedPoint(screenX, screenY, zFinal)
    }
}

private data class ProjectedPoint(val x: Float, val y: Float, val z: Float)

private data class RenderFace(
    val p0: Vec3,
    val p1: Vec3,
    val p2: Vec3,
    val p3: Vec3,
    val bitmap: Bitmap,
    val light: Float,
    val isOuter: Boolean
)

private data class ProjectedFace(
    val p0: ProjectedPoint,
    val p1: ProjectedPoint,
    val p2: ProjectedPoint,
    val p3: ProjectedPoint,
    val bitmap: Bitmap,
    val light: Float,
    val avgZ: Float
)

private class FaceTextures(
    val top: Bitmap?,
    val bottom: Bitmap?,
    val right: Bitmap?,
    val front: Bitmap?,
    val left: Bitmap?,
    val back: Bitmap?
)

private fun extractCuboidTextures(
    bmp: Bitmap,
    u: Int, v: Int,
    w: Int, h: Int, d: Int
): FaceTextures? {
    if (u + d + w + d > bmp.width || v + d + h > bmp.height) {
        return null
    }

    fun crop(x: Int, y: Int, width: Int, height: Int): Bitmap? {
        if (x + width > bmp.width || y + height > bmp.height || width <= 0 || height <= 0) return null
        val sub = Bitmap.createBitmap(bmp, x, y, width, height)
        var hasAlpha = false
        val pixels = IntArray(width * height)
        sub.getPixels(pixels, 0, width, 0, 0, width, height)
        for (p in pixels) {
            if ((p ushr 24) > 8) {
                hasAlpha = true
                break
            }
        }
        return if (hasAlpha) sub else null
    }

    return FaceTextures(
        top = crop(u + d, v, w, d),
        bottom = crop(u + d + w, v, w, d),
        right = crop(u, v + d, d, h),
        front = crop(u + d, v + d, w, h),
        left = crop(u + d + w, v + d, d, h),
        back = crop(u + d + w + d, v + d, w, h)
    )
}

private class SkinFaceCache(
    skin: Bitmap,
    cape: Bitmap?,
    val armSlim: Boolean
) {
    val armWidth = if (armSlim) 3 else 4
    val modern = skin.height >= 64

    val head = extractCuboidTextures(skin, 0, 0, 8, 8, 8)
    val hat = extractCuboidTextures(skin, 32, 0, 8, 8, 8)

    val torso = extractCuboidTextures(skin, 16, 16, 8, 12, 4)
    val jacket = if (modern) extractCuboidTextures(skin, 16, 32, 8, 12, 4) else null

    val rightArm = extractCuboidTextures(skin, 40, 16, armWidth, 12, 4)
    val rightSleeve = if (modern) extractCuboidTextures(skin, 40, 32, armWidth, 12, 4) else null

    val leftArm = if (modern) {
        extractCuboidTextures(skin, 32, 48, armWidth, 12, 4)
    } else {
        extractCuboidTextures(skin, 40, 16, armWidth, 12, 4)
    }
    val leftSleeve = if (modern) extractCuboidTextures(skin, 48, 48, armWidth, 12, 4) else null

    val rightLeg = extractCuboidTextures(skin, 0, 16, 4, 12, 4)
    val rightPants = if (modern) extractCuboidTextures(skin, 0, 32, 4, 12, 4) else null

    val leftLeg = if (modern) {
        extractCuboidTextures(skin, 16, 48, 4, 12, 4)
    } else {
        extractCuboidTextures(skin, 0, 16, 4, 12, 4)
    }
    val leftPants = if (modern) extractCuboidTextures(skin, 0, 48, 4, 12, 4) else null

    val capeTextures = if (cape != null) extractCuboidTextures(cape, 0, 0, 10, 16, 1) else null
}

private fun addCuboidFaces(
    faces: MutableList<RenderFace>,
    cx: Float, cy: Float, cz: Float,
    w: Float, h: Float, d: Float,
    inflate: Float,
    pivotY: Float, pivotZ: Float, swingX: Float,
    textures: FaceTextures?,
    isOuter: Boolean
) {
    if (textures == null) return

    val x0 = cx - w / 2f - inflate
    val x1 = cx + w / 2f + inflate
    val y0 = cy - h / 2f - inflate
    val y1 = cy + h / 2f + inflate
    val z0 = cz - d / 2f - inflate
    val z1 = cz + d / 2f + inflate

    fun pt(x: Float, y: Float, z: Float): Vec3 {
        return Vec3(x, y, z).rotateX(pivotY, pivotZ, swingX)
    }

    // 0: Front face (z0 facing camera when yaw=0)
    textures.front?.let { bmp ->
        faces.add(
            RenderFace(
                p0 = pt(x0, y1, z0),
                p1 = pt(x1, y1, z0),
                p2 = pt(x1, y0, z0),
                p3 = pt(x0, y0, z0),
                bitmap = bmp,
                light = 1.0f,
                isOuter = isOuter
            )
        )
    }

    // 1: Back face (z1)
    textures.back?.let { bmp ->
        faces.add(
            RenderFace(
                p0 = pt(x1, y1, z1),
                p1 = pt(x0, y1, z1),
                p2 = pt(x0, y0, z1),
                p3 = pt(x1, y0, z1),
                bitmap = bmp,
                light = 0.90f,
                isOuter = isOuter
            )
        )
    }

    // 2: Left face (+X)
    textures.left?.let { bmp ->
        faces.add(
            RenderFace(
                p0 = pt(x1, y1, z0),
                p1 = pt(x1, y1, z1),
                p2 = pt(x1, y0, z1),
                p3 = pt(x1, y0, z0),
                bitmap = bmp,
                light = 0.78f,
                isOuter = isOuter
            )
        )
    }

    // 3: Right face (-X)
    textures.right?.let { bmp ->
        faces.add(
            RenderFace(
                p0 = pt(x0, y1, z1),
                p1 = pt(x0, y1, z0),
                p2 = pt(x0, y0, z0),
                p3 = pt(x0, y0, z1),
                bitmap = bmp,
                light = 0.82f,
                isOuter = isOuter
            )
        )
    }

    // 4: Top face (+Y)
    textures.top?.let { bmp ->
        faces.add(
            RenderFace(
                p0 = pt(x0, y1, z1),
                p1 = pt(x1, y1, z1),
                p2 = pt(x1, y1, z0),
                p3 = pt(x0, y1, z0),
                bitmap = bmp,
                light = 1.0f,
                isOuter = isOuter
            )
        )
    }

    // 5: Bottom face (-Y)
    textures.bottom?.let { bmp ->
        faces.add(
            RenderFace(
                p0 = pt(x0, y0, z0),
                p1 = pt(x1, y0, z0),
                p2 = pt(x1, y0, z1),
                p3 = pt(x0, y0, z1),
                bitmap = bmp,
                light = 0.65f,
                isOuter = isOuter
            )
        )
    }
}

/**
 * Genuine 3D Minecraft Character Renderer with Depth Sorting, Back-Face Culling,
 * Directional Shading, and Poly-To-Poly Texture Projection.
 */
@Composable
private fun Skin3DRenderer(
    faceCache: SkinFaceCache,
    yaw: Float,
    pitch: Float,
    armSlim: Boolean,
    hatLayer: Boolean,
    jacketLayer: Boolean,
    sleeveLayer: Boolean,
    pantsLayer: Boolean,
    animState: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "playerAnim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cX = size.width / 2f
        val cY = size.height / 2f

        val swing = when (animState) {
            "walk" -> sin(animPhase.toDouble()).toFloat() * 26f
            else -> 0f
        }
        val bob = when (animState) {
            "walk" -> abs(sin(animPhase.toDouble())).toFloat() * 0.8f
            "idle" -> sin(animPhase.toDouble()).toFloat() * 0.4f
            "crouch" -> -2.0f
            else -> 0f
        }
        val armSway = when (animState) {
            "idle" -> sin(animPhase.toDouble()).toFloat() * 2f
            else -> 0f
        }
        val isCrouch = animState == "crouch"

        // Ground contact shadow
        drawOval(
            color = Color(0x38000000),
            topLeft = Offset(cX - 46f, cY + 98f + (if (isCrouch) 8f else 0f)),
            size = androidx.compose.ui.geometry.Size(92f, 22f)
        )

        val armW = if (armSlim) 3f else 4f
        val faces = mutableListOf<RenderFace>()

        // 1. Head & Hat
        val headPivotY = 24f + bob
        val headTilt = if (isCrouch) -14f else (sin(animPhase.toDouble() * 0.5).toFloat() * 1.5f)
        val headZOff = if (isCrouch) -2f else 0f
        addCuboidFaces(
            faces = faces,
            cx = 0f, cy = 28f + bob, cz = headZOff,
            w = 8f, h = 8f, d = 8f,
            inflate = 0f,
            pivotY = headPivotY, pivotZ = headZOff, swingX = headTilt,
            textures = faceCache.head,
            isOuter = false
        )
        if (hatLayer) {
            addCuboidFaces(
                faces = faces,
                cx = 0f, cy = 28f + bob, cz = headZOff,
                w = 8f, h = 8f, d = 8f,
                inflate = 0.5f,
                pivotY = headPivotY, pivotZ = headZOff, swingX = headTilt,
                textures = faceCache.hat,
                isOuter = true
            )
        }

        // 2. Torso & Jacket
        val torsoTilt = if (isCrouch) 14f else 0f
        val torsoZOff = if (isCrouch) -1f else 0f
        addCuboidFaces(
            faces = faces,
            cx = 0f, cy = 18f + bob, cz = torsoZOff,
            w = 8f, h = 12f, d = 4f,
            inflate = 0f,
            pivotY = 12f, pivotZ = 0f, swingX = torsoTilt,
            textures = faceCache.torso,
            isOuter = false
        )
        if (jacketLayer) {
            addCuboidFaces(
                faces = faces,
                cx = 0f, cy = 18f + bob, cz = torsoZOff,
                w = 8f, h = 12f, d = 4f,
                inflate = 0.25f,
                pivotY = 12f, pivotZ = 0f, swingX = torsoTilt,
                textures = faceCache.jacket,
                isOuter = true
            )
        }

        // 3. Right Arm & Sleeve
        val rArmSwing = if (isCrouch) 12f else (swing + armSway)
        addCuboidFaces(
            faces = faces,
            cx = -4f - armW / 2f, cy = 18f + bob, cz = torsoZOff,
            w = armW, h = 12f, d = 4f,
            inflate = 0f,
            pivotY = 23f + bob, pivotZ = torsoZOff, swingX = rArmSwing,
            textures = faceCache.rightArm,
            isOuter = false
        )
        if (sleeveLayer) {
            addCuboidFaces(
                faces = faces,
                cx = -4f - armW / 2f, cy = 18f + bob, cz = torsoZOff,
                w = armW, h = 12f, d = 4f,
                inflate = 0.25f,
                pivotY = 23f + bob, pivotZ = torsoZOff, swingX = rArmSwing,
                textures = faceCache.rightSleeve,
                isOuter = true
            )
        }

        // 4. Left Arm & Sleeve
        val lArmSwing = if (isCrouch) 12f else (-swing - armSway)
        addCuboidFaces(
            faces = faces,
            cx = 4f + armW / 2f, cy = 18f + bob, cz = torsoZOff,
            w = armW, h = 12f, d = 4f,
            inflate = 0f,
            pivotY = 23f + bob, pivotZ = torsoZOff, swingX = lArmSwing,
            textures = faceCache.leftArm,
            isOuter = false
        )
        if (sleeveLayer) {
            addCuboidFaces(
                faces = faces,
                cx = 4f + armW / 2f, cy = 18f + bob, cz = torsoZOff,
                w = armW, h = 12f, d = 4f,
                inflate = 0.25f,
                pivotY = 23f + bob, pivotZ = torsoZOff, swingX = lArmSwing,
                textures = faceCache.leftSleeve,
                isOuter = true
            )
        }

        // 5. Right Leg & Pants
        val rLegSwing = if (isCrouch) -10f else -swing
        addCuboidFaces(
            faces = faces,
            cx = -2f, cy = 6f, cz = 0f,
            w = 4f, h = 12f, d = 4f,
            inflate = 0f,
            pivotY = 12f, pivotZ = 0f, swingX = rLegSwing,
            textures = faceCache.rightLeg,
            isOuter = false
        )
        if (pantsLayer) {
            addCuboidFaces(
                faces = faces,
                cx = -2f, cy = 6f, cz = 0f,
                w = 4f, h = 12f, d = 4f,
                inflate = 0.25f,
                pivotY = 12f, pivotZ = 0f, swingX = rLegSwing,
                textures = faceCache.rightPants,
                isOuter = true
            )
        }

        // 6. Left Leg & Pants
        val lLegSwing = if (isCrouch) -10f else swing
        addCuboidFaces(
            faces = faces,
            cx = 2f, cy = 6f, cz = 0f,
            w = 4f, h = 12f, d = 4f,
            inflate = 0f,
            pivotY = 12f, pivotZ = 0f, swingX = lLegSwing,
            textures = faceCache.leftLeg,
            isOuter = false
        )
        if (pantsLayer) {
            addCuboidFaces(
                faces = faces,
                cx = 2f, cy = 6f, cz = 0f,
                w = 4f, h = 12f, d = 4f,
                inflate = 0.25f,
                pivotY = 12f, pivotZ = 0f, swingX = lLegSwing,
                textures = faceCache.leftPants,
                isOuter = true
            )
        }

        // 7. Cape
        if (faceCache.capeTextures != null) {
            val capeAngle = if (isCrouch) 28f else (12f + abs(swing) * 0.5f)
            addCuboidFaces(
                faces = faces,
                cx = 0f, cy = 16f + bob, cz = if (isCrouch) 1.5f else 2.5f,
                w = 10f, h = 16f, d = 1f,
                inflate = 0f,
                pivotY = 24f + bob, pivotZ = if (isCrouch) 0.5f else 2.0f, swingX = capeAngle,
                textures = faceCache.capeTextures,
                isOuter = false
            )
        }

        // Project vertices and filter back-faces
        val visibleFaces = mutableListOf<ProjectedFace>()
        for (face in faces) {
            val p0 = face.p0.project(yaw, pitch, size.width, size.height, 1.0f)
            val p1 = face.p1.project(yaw, pitch, size.width, size.height, 1.0f)
            val p2 = face.p2.project(yaw, pitch, size.width, size.height, 1.0f)
            val p3 = face.p3.project(yaw, pitch, size.width, size.height, 1.0f)

            // Screen-space 2D cross product for back-face culling
            val cross = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)
            if (cross > 0.001f) {
                val avgZ = (p0.z + p1.z + p2.z + p3.z) / 4f
                visibleFaces.add(
                    ProjectedFace(p0, p1, p2, p3, face.bitmap, face.light, avgZ)
                )
            }
        }

        // Painter's algorithm: sort by depth descending (furthest first, closest last)
        visibleFaces.sortByDescending { it.avgZ }

        // Render sorted faces with hardware-accelerated poly-to-poly matrix mapping
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val matrix = Matrix()
            val paint = Paint().apply {
                isFilterBitmap = false
                isAntiAlias = false
            }

            for (face in visibleFaces) {
                val srcPts = floatArrayOf(
                    0f, 0f,
                    face.bitmap.width.toFloat(), 0f,
                    face.bitmap.width.toFloat(), face.bitmap.height.toFloat(),
                    0f, face.bitmap.height.toFloat()
                )
                val dstPts = floatArrayOf(
                    face.p0.x, face.p0.y,
                    face.p1.x, face.p1.y,
                    face.p2.x, face.p2.y,
                    face.p3.x, face.p3.y
                )

                if (matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
                    val shade = (255 * face.light).toInt().coerceIn(0, 255)
                    paint.colorFilter = LightingColorFilter(
                        android.graphics.Color.rgb(shade, shade, shade),
                        0
                    )
                    nativeCanvas.save()
                    nativeCanvas.concat(matrix)
                    nativeCanvas.drawBitmap(face.bitmap, 0f, 0f, paint)
                    nativeCanvas.restore()
                }
            }
        }
    }
}

// Built-in authentic pixel textures
private fun createSteveTexture(): Bitmap {
    val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val skinCol = android.graphics.Color.rgb(187, 131, 98)
    val hairCol = android.graphics.Color.rgb(55, 36, 26)
    val eyeCol = android.graphics.Color.rgb(44, 22, 119)
    val shirtCol = android.graphics.Color.rgb(0, 140, 140)
    val jeansCol = android.graphics.Color.rgb(40, 50, 110)
    val shoeCol = android.graphics.Color.rgb(70, 70, 70)

    for (y in 0 until 64) {
        for (x in 0 until 64) {
            val col = when {
                y in 0..7 && x in 8..15 -> hairCol
                y in 0..7 && x in 16..23 -> hairCol
                y in 8..15 && x in 8..15 -> if (y in 12..13 && (x == 9 || x == 14)) eyeCol else skinCol
                y in 8..15 && x in 0..7 -> hairCol
                y in 8..15 && x in 16..31 -> hairCol
                y in 16..19 && x in 20..27 -> shirtCol
                y in 20..31 && x in 16..39 -> shirtCol
                y in 20..31 && (x in 40..47 || x in 32..39) -> if (y >= 28) skinCol else shirtCol
                y in 0..15 && x in 0..15 -> jeansCol
                y in 16..31 && x in 0..15 -> if (y >= 28) shoeCol else jeansCol
                y in 48..63 && x in 0..31 -> if (y >= 60) shoeCol else jeansCol
                y in 48..63 && x in 32..47 -> if (y >= 60) skinCol else shirtCol
                else -> android.graphics.Color.TRANSPARENT
            }
            bmp.setPixel(x, y, col)
        }
    }
    return bmp
}

private fun createAlexTexture(): Bitmap {
    val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val skinCol = android.graphics.Color.rgb(215, 176, 146)
    val hairCol = android.graphics.Color.rgb(179, 86, 38)
    val eyeCol = android.graphics.Color.rgb(58, 111, 62)
    val tunicCol = android.graphics.Color.rgb(83, 123, 60)
    val pantsCol = android.graphics.Color.rgb(75, 54, 35)
    val bootCol = android.graphics.Color.rgb(50, 40, 30)

    for (y in 0 until 64) {
        for (x in 0 until 64) {
            val col = when {
                y in 0..8 && x in 8..15 -> hairCol
                y in 0..8 && x in 16..23 -> hairCol
                y in 9..15 && x in 8..15 -> if (y in 12..13 && (x == 9 || x == 14)) eyeCol else skinCol
                y in 9..15 && (x in 0..7 || x in 16..31) -> hairCol
                y in 16..19 && x in 20..27 -> tunicCol
                y in 20..31 && x in 16..39 -> tunicCol
                y in 20..31 && (x in 40..46 || x in 32..38) -> if (y >= 28) skinCol else tunicCol
                y in 16..31 && x in 0..15 -> if (y >= 28) bootCol else pantsCol
                y in 48..63 && x in 0..31 -> if (y >= 60) bootCol else pantsCol
                y in 48..63 && x in 32..46 -> if (y >= 60) skinCol else tunicCol
                else -> android.graphics.Color.TRANSPARENT
            }
            bmp.setPixel(x, y, col)
        }
    }
    return bmp
}

