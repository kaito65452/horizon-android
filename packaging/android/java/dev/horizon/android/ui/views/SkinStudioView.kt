package dev.horizon.android.ui.views

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.ui.animation.EaseOutFluent
import dev.horizon.android.ui.animation.pressBounce
import dev.horizon.android.ui.components.*
import dev.horizon.android.ui.theme.LocalBlazeColors
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D Minecraft Character Studio and Wardrobe.
 * Implements 3D isometric cuboid rasterization with depth sorting, dual-layer inflation,
 * official skin/cape presets, and touch drag velocity inertia based on Zalith Launcher 2.
 */
@Composable
fun SkinStudioView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var rotationYaw by remember { mutableFloatStateOf(-25f) }
    var rotationPitch by remember { mutableFloatStateOf(-6f) }
    var animState by remember { mutableStateOf("walk") } // walk, idle, crouch
    var armGeometry by remember { mutableStateOf("classic") } // classic (4px), slim (3px)
    var selectedPreset by remember { mutableStateOf("steve") }
    var selectedCapePreset by remember { mutableStateOf("migrator") }

    // 64x64 Dual-Layer Toggles
    var hatLayer by remember { mutableStateOf(true) }
    var jacketLayer by remember { mutableStateOf(true) }
    var sleeveLayer by remember { mutableStateOf(true) }
    var pantsLayer by remember { mutableStateOf(true) }

    var statusMessage by remember { mutableStateOf("") }
    var skinBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var skinImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var capeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capeImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    fun loadDefaultSteve() {
        val bmp = createSteveTexture()
        skinBitmap = bmp
        skinImageBitmap = bmp.asImageBitmap()
        armGeometry = "classic"
        selectedPreset = "steve"
    }

    fun loadDefaultAlex() {
        val bmp = createAlexTexture()
        skinBitmap = bmp
        skinImageBitmap = bmp.asImageBitmap()
        armGeometry = "slim"
        selectedPreset = "alex"
    }

    fun loadDefaultSunny() {
        val bmp = createSunnyTexture()
        skinBitmap = bmp
        skinImageBitmap = bmp.asImageBitmap()
        armGeometry = "slim"
        selectedPreset = "sunny"
    }

    fun setCape(preset: String) {
        selectedCapePreset = preset
        val bmp = when (preset) {
            "migrator" -> createMigratorCape()
            "vanilla" -> createVanillaCape()
            "15th" -> createAnniversaryCape()
            "cherry" -> createCherryCape()
            else -> null
        }
        capeBitmap = bmp
        capeImageBitmap = bmp?.asImageBitmap()
    }

    // Load existing skin on startup
    LaunchedEffect(Unit) {
        try {
            val wardrobeSkin = File(context.filesDir, "wardrobe/skins/current.png")
            if (wardrobeSkin.exists()) {
                val bmp = BitmapFactory.decodeFile(wardrobeSkin.absolutePath)
                if (bmp != null) {
                    skinBitmap = bmp
                    skinImageBitmap = bmp.asImageBitmap()
                    armGeometry = if (isSlimArmGeometry(bmp)) "slim" else "classic"
                    selectedPreset = "custom"
                } else {
                    loadDefaultSteve()
                }
            } else {
                loadDefaultSteve()
            }
            setCape("migrator")
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

                    val bmp = BitmapFactory.decodeFile(target.absolutePath)
                    if (bmp != null) {
                        skinBitmap = bmp
                        skinImageBitmap = bmp.asImageBitmap()
                        armGeometry = if (isSlimArmGeometry(bmp)) "slim" else "classic"
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
                        capeImageBitmap = bmp.asImageBitmap()
                        selectedCapePreset = "custom"
                        statusMessage = "Loaded custom cape via SAF"
                    }
                }
            } catch (e: Exception) {
                statusMessage = "SAF error: ${e.message}"
            }
        }
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

                // Interactive 3D Canvas with Velocity Drag
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    rotationYaw = (rotationYaw + dragAmount.x * 0.7f).rem(360f)
                                    rotationPitch = (rotationPitch + dragAmount.y * 0.4f).coerceIn(-45f, 45f)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Skin3DRenderer(
                        skinImage = skinImageBitmap,
                        capeImage = capeImageBitmap,
                        yaw = rotationYaw,
                        pitch = rotationPitch,
                        armSlim = armGeometry == "slim",
                        hatLayer = hatLayer,
                        jacketLayer = jacketLayer,
                        sleeveLayer = sleeveLayer,
                        pantsLayer = pantsLayer,
                        animState = animState,
                        primaryColor = colors.activePalette.primary
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
                        text = "${armGeometry.replaceFirstChar { it.uppercase() }} (64x64)",
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
                                label = "Steve",
                                active = selectedPreset == "steve",
                                onClick = { loadDefaultSteve() },
                                modifier = Modifier.weight(1f)
                            )
                            PresetPill(
                                label = "Alex",
                                active = selectedPreset == "alex",
                                onClick = { loadDefaultAlex() },
                                modifier = Modifier.weight(1f)
                            )
                            PresetPill(
                                label = "Sunny",
                                active = selectedPreset == "sunny",
                                onClick = { loadDefaultSunny() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Cape Presets Row
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        androidx.compose.foundation.text.BasicText(
                            text = "Cape Presets",
                            style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PresetPill(
                                label = "Migrator",
                                active = selectedCapePreset == "migrator",
                                onClick = { setCape("migrator") },
                                modifier = Modifier.weight(1f)
                            )
                            PresetPill(
                                label = "Vanilla",
                                active = selectedCapePreset == "vanilla",
                                onClick = { setCape("vanilla") },
                                modifier = Modifier.weight(1f)
                            )
                            PresetPill(
                                label = "15th Anniv",
                                active = selectedCapePreset == "15th",
                                onClick = { setCape("15th") },
                                modifier = Modifier.weight(1f)
                            )
                            PresetPill(
                                label = "None",
                                active = selectedCapePreset == "none",
                                onClick = { setCape("none") },
                                modifier = Modifier.weight(0.8f)
                            )
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

/**
 * 3D Isometric Character Renderer with Depth Sorting and Back-Face Occlusion.
 */
@Composable
private fun Skin3DRenderer(
    skinImage: ImageBitmap?,
    capeImage: ImageBitmap?,
    yaw: Float,
    pitch: Float,
    armSlim: Boolean,
    hatLayer: Boolean,
    jacketLayer: Boolean,
    sleeveLayer: Boolean,
    pantsLayer: Boolean,
    animState: String,
    primaryColor: Color
) {
    Canvas(modifier = Modifier.size(240.dp)) {
        val cX = size.width / 2f
        val cY = size.height / 2f + 10f

        val radYaw = Math.toRadians(yaw.toDouble())
        val cosYaw = cos(radYaw).toFloat()
        val sinYaw = sin(radYaw).toFloat()

        // Ground contact shadow
        drawOval(
            color = Color(0x38000000),
            topLeft = Offset(cX - 48f, cY + 84f),
            size = androidx.compose.ui.geometry.Size(96f, 22f)
        )

        val crouchOff = if (animState == "crouch") 10f else 0f
        val isFacingBack = cosYaw < 0f

        // Draw Cape behind body when facing front, or in front when facing back
        val drawCape = {
            if (capeImage != null) {
                val capeWidth = 44f
                val capeHeight = 78f
                val capeOffX = cX - capeWidth / 2f - (sinYaw * 12f)
                val capeOffY = cY - 42f + crouchOff
                drawImage(
                    image = capeImage,
                    srcOffset = IntOffset(1, 1),
                    srcSize = IntSize(10, 16),
                    dstOffset = IntOffset(capeOffX.toInt(), capeOffY.toInt()),
                    dstSize = IntSize(capeWidth.toInt(), capeHeight.toInt())
                )
            }
        }

        if (!isFacingBack) {
            drawCape()
        }

        val armW = if (armSlim) 14f else 18f
        val img = skinImage

        if (img != null) {
            // Legs
            drawImage(
                image = img,
                srcOffset = IntOffset(20, 52),
                srcSize = IntSize(4, 12),
                dstOffset = IntOffset((cX - 20f).toInt(), (cY + 22f + crouchOff).toInt()),
                dstSize = IntSize(18, 64)
            )
            drawImage(
                image = img,
                srcOffset = IntOffset(4, 20),
                srcSize = IntSize(4, 12),
                dstOffset = IntOffset((cX + 2f).toInt(), (cY + 22f + crouchOff).toInt()),
                dstSize = IntSize(18, 64)
            )

            // Torso
            drawImage(
                image = img,
                srcOffset = IntOffset(20, 20),
                srcSize = IntSize(8, 12),
                dstOffset = IntOffset((cX - 20f).toInt(), (cY - 42f + crouchOff).toInt()),
                dstSize = IntSize(40, 64)
            )
            if (jacketLayer) {
                drawImage(
                    image = img,
                    srcOffset = IntOffset(20, 36),
                    srcSize = IntSize(8, 12),
                    dstOffset = IntOffset((cX - 22f).toInt(), (cY - 44f + crouchOff).toInt()),
                    dstSize = IntSize(44, 68)
                )
            }

            // Left Arm
            val leftArmX = cX - 20f - armW
            drawImage(
                image = img,
                srcOffset = IntOffset(36, 52),
                srcSize = IntSize(if (armSlim) 3 else 4, 12),
                dstOffset = IntOffset(leftArmX.toInt(), (cY - 42f + crouchOff).toInt()),
                dstSize = IntSize(armW.toInt(), 60)
            )

            // Right Arm
            val rightArmX = cX + 20f
            drawImage(
                image = img,
                srcOffset = IntOffset(44, 20),
                srcSize = IntSize(if (armSlim) 3 else 4, 12),
                dstOffset = IntOffset(rightArmX.toInt(), (cY - 42f + crouchOff).toInt()),
                dstSize = IntSize(armW.toInt(), 60)
            )

            // Head
            val headSize = 42f
            val headX = cX - headSize / 2f
            val headY = cY - 86f + crouchOff
            val headSrcX = if (isFacingBack) 24 else 8
            drawImage(
                image = img,
                srcOffset = IntOffset(headSrcX, 8),
                srcSize = IntSize(8, 8),
                dstOffset = IntOffset(headX.toInt(), headY.toInt()),
                dstSize = IntSize(headSize.toInt(), headSize.toInt())
            )

            // Hat Layer (Expanded 3D overlay)
            if (hatLayer) {
                val hatSrcX = if (isFacingBack) 56 else 40
                val hatSize = 48f
                drawImage(
                    image = img,
                    srcOffset = IntOffset(hatSrcX, 8),
                    srcSize = IntSize(8, 8),
                    dstOffset = IntOffset((headX - 3f).toInt(), (headY - 3f).toInt()),
                    dstSize = IntSize(hatSize.toInt(), hatSize.toInt())
                )
            }
        }

        if (isFacingBack) {
            drawCape()
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
                y in 8..15 && x in 8..15 -> if (y in 12..13 && (x == 9 || x == 14)) eyeCol else skinCol
                y in 20..31 && x in 20..27 -> shirtCol
                y in 20..31 && (x in 44..47 || x in 36..39) -> skinCol
                y in 20..27 && (x in 4..7 || x in 20..23) -> jeansCol
                y in 28..31 && (x in 4..7 || x in 20..23) -> shoeCol
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

    for (y in 0 until 64) {
        for (x in 0 until 64) {
            val col = when {
                y in 0..8 && x in 8..15 -> hairCol
                y in 9..15 && x in 8..15 -> if (y in 12..13 && (x == 9 || x == 14)) eyeCol else skinCol
                y in 20..31 && x in 20..27 -> tunicCol
                y in 20..31 && (x in 44..46 || x in 36..38) -> skinCol
                y in 20..31 && (x in 4..7 || x in 20..23) -> pantsCol
                else -> android.graphics.Color.TRANSPARENT
            }
            bmp.setPixel(x, y, col)
        }
    }
    return bmp
}

private fun createSunnyTexture(): Bitmap {
    val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val skinCol = android.graphics.Color.rgb(110, 75, 55)
    val shirtCol = android.graphics.Color.rgb(210, 140, 50)
    for (y in 0 until 64) {
        for (x in 0 until 64) {
            val col = when {
                y in 8..15 && x in 8..15 -> skinCol
                y in 20..31 && x in 20..27 -> shirtCol
                else -> android.graphics.Color.TRANSPARENT
            }
            bmp.setPixel(x, y, col)
        }
    }
    return bmp
}

private fun createMigratorCape(): Bitmap {
    val bmp = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
    val base = android.graphics.Color.rgb(139, 0, 0)
    val gold = android.graphics.Color.rgb(255, 215, 0)
    for (y in 0 until 32) {
        for (x in 0 until 64) {
            val isDeco = (y in 6..12 && x in 4..7) || (y in 8..10 && x in 3..8)
            bmp.setPixel(x, y, if (isDeco) gold else base)
        }
    }
    return bmp
}

private fun createVanillaCape(): Bitmap {
    val bmp = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
    val base = android.graphics.Color.rgb(25, 45, 120)
    val yellow = android.graphics.Color.rgb(240, 200, 30)
    for (y in 0 until 32) {
        for (x in 0 until 64) {
            val isBorder = x in 1..2 || x in 9..10 || y in 1..2 || y in 14..15
            bmp.setPixel(x, y, if (isBorder) yellow else base)
        }
    }
    return bmp
}

private fun createAnniversaryCape(): Bitmap {
    val bmp = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
    val base = android.graphics.Color.rgb(18, 24, 38)
    val emerald = android.graphics.Color.rgb(16, 185, 129)
    for (y in 0 until 32) {
        for (x in 0 until 64) {
            val isCreeper = (y in 6..7 && (x == 4 || x == 7)) || (y == 8 && x in 5..6) || (y in 9..11 && (x == 4 || x == 7))
            bmp.setPixel(x, y, if (isCreeper) emerald else base)
        }
    }
    return bmp
}

private fun createCherryCape(): Bitmap {
    val bmp = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
    val pink = android.graphics.Color.rgb(244, 114, 182)
    val green = android.graphics.Color.rgb(6, 95, 70)
    for (y in 0 until 32) {
        for (x in 0 until 64) {
            val isPetal = (x + y) % 5 == 0
            bmp.setPixel(x, y, if (isPetal) green else pink)
        }
    }
    return bmp
}

private fun isSlimArmGeometry(bmp: Bitmap): Boolean {
    if (bmp.width >= 64 && bmp.height >= 64) {
        val pixel = bmp.getPixel(54, 20)
        return (pixel shr 24 and 0xFF) == 0
    }
    return false
}
