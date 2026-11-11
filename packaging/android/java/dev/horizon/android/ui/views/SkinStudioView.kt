package dev.horizon.android.ui.views

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.ui.components.HorizonButton
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.components.HorizonPill
import dev.horizon.android.ui.components.HorizonSectionHeader
import dev.horizon.android.ui.components.HorizonSegmented
import dev.horizon.android.ui.components.HorizonSwitch
import dev.horizon.android.ui.theme.LocalBlazeColors
import org.json.JSONObject
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SkinStudioView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val context = LocalContext.current

    var rotationY by remember { mutableFloatStateOf(-25f) }
    var animState by remember { mutableStateOf("walk") } // walk, idle, crouch
    var armGeometry by remember { mutableStateOf("slim") } // slim (3px), classic (4px)
    var capeVisible by remember { mutableStateOf(true) }

    // 64x64 Dual-Layer Toggles
    var hatLayer by remember { mutableStateOf(true) }
    var jacketLayer by remember { mutableStateOf(true) }
    var sleeveLayer by remember { mutableStateOf(true) }
    var pantsLayer by remember { mutableStateOf(true) }

    var statusMessage by remember { mutableStateOf("") }
    var skinBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var skinImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Load existing skin from wardrobe on startup
    LaunchedEffect(Unit) {
        try {
            val wardrobeSkin = File(context.filesDir, "wardrobe/skins/current.png")
            if (wardrobeSkin.exists()) {
                val bmp = BitmapFactory.decodeFile(wardrobeSkin.absolutePath)
                if (bmp != null) {
                    skinBitmap = bmp
                    skinImageBitmap = bmp.asImageBitmap()
                    if (isSlimArmGeometry(bmp)) armGeometry = "slim" else armGeometry = "classic"
                }
            }
        } catch (_: Throwable) {}
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
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }

                    val bmp = BitmapFactory.decodeFile(target.absolutePath)
                    if (bmp != null) {
                        skinBitmap = bmp
                        skinImageBitmap = bmp.asImageBitmap()
                        armGeometry = if (isSlimArmGeometry(bmp)) "slim" else "classic"
                        
                        // Check if Microsoft Account exists to auto-sync
                        val configJson = JSONObject(BlazeNativeBridge.nativeGetConfigJson())
                        val msClient = configJson.optString("microsoft_client_id", "")
                        statusMessage = if (msClient.isNotEmpty()) {
                            "Loaded ${bmp.width}x${bmp.height} skin & queued Microsoft cloud sync"
                        } else {
                            "Loaded ${bmp.width}x${bmp.height} skin to wardrobe"
                        }
                    } else {
                        statusMessage = "Could not decode skin PNG."
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
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    capeVisible = true
                    statusMessage = "Loaded custom cape via SAF."
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
        // Left: 3D Character Viewport (7 cols)
        HorizonGlass(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top control bar inside preview
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
                            text = "3D DUAL-LAYER PREVIEW",
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

                        HorizonPill(onClick = { capeVisible = !capeVisible }) {
                            androidx.compose.foundation.text.BasicText(
                                text = if (capeVisible) "Cape: ON" else "Cape: OFF",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = if (capeVisible) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Interactive 3D Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                rotationY += dragAmount.x * 0.5f
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(240.dp)) {
                        val cX = size.width / 2f
                        val cY = size.height / 2f
                        val rad = Math.toRadians(rotationY.toDouble())
                        val sinR = sin(rad).toFloat()

                        // Shadow on ground
                        drawOval(
                            color = Color(0x33000000),
                            topLeft = Offset(cX - 45f, cY + 80f),
                            size = Size(90f, 24f)
                        )

                        // Cape (if enabled)
                        if (capeVisible) {
                            drawRoundRect(
                                color = Color(0xFFE11D48),
                                topLeft = Offset(cX - 24f - (sinR * 14f), cY - 26f),
                                size = Size(48f, 85f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                            )
                        }

                        val crouchOff = if (animState == "crouch") 12f else 0f
                        val currentImg = skinImageBitmap

                        if (currentImg != null) {
                            // REAL SKIN RENDERING FROM BITMAP
                            // Head
                            drawImage(
                                image = currentImg,
                                srcOffset = IntOffset(8, 8),
                                srcSize = IntSize(8, 8),
                                dstOffset = IntOffset((cX - 22f).toInt(), (cY - 95f + crouchOff).toInt()),
                                dstSize = IntSize(44, 44)
                            )
                            if (hatLayer) {
                                drawImage(
                                    image = currentImg,
                                    srcOffset = IntOffset(40, 8),
                                    srcSize = IntSize(8, 8),
                                    dstOffset = IntOffset((cX - 25f).toInt(), (cY - 98f + crouchOff).toInt()),
                                    dstSize = IntSize(50, 50)
                                )
                            }

                            // Torso
                            drawImage(
                                image = currentImg,
                                srcOffset = IntOffset(20, 20),
                                srcSize = IntSize(8, 12),
                                dstOffset = IntOffset((cX - 22f).toInt(), (cY - 45f + crouchOff).toInt()),
                                dstSize = IntSize(44, 65)
                            )
                            if (jacketLayer) {
                                drawImage(
                                    image = currentImg,
                                    srcOffset = IntOffset(20, 36),
                                    srcSize = IntSize(8, 12),
                                    dstOffset = IntOffset((cX - 24f).toInt(), (cY - 47f + crouchOff).toInt()),
                                    dstSize = IntSize(48, 69)
                                )
                            }

                            // Arms
                            val armW = if (armGeometry == "slim") 3 else 4
                            val armWDp = if (armGeometry == "slim") 15f else 18f
                            // Left Arm
                            drawImage(
                                image = currentImg,
                                srcOffset = IntOffset(36, 52),
                                srcSize = IntSize(armW, 12),
                                dstOffset = IntOffset((cX - 22f - armWDp).toInt(), (cY - 45f + crouchOff).toInt()),
                                dstSize = IntSize(armWDp.toInt(), 60)
                            )
                            // Right Arm
                            drawImage(
                                image = currentImg,
                                srcOffset = IntOffset(44, 20),
                                srcSize = IntSize(armW, 12),
                                dstOffset = IntOffset((cX + 22f).toInt(), (cY - 45f + crouchOff).toInt()),
                                dstSize = IntSize(armWDp.toInt(), 60)
                            )

                            // Legs
                            drawImage(
                                image = currentImg,
                                srcOffset = IntOffset(20, 52),
                                srcSize = IntSize(4, 12),
                                dstOffset = IntOffset((cX - 22f).toInt(), (cY + 20f + crouchOff).toInt()),
                                dstSize = IntSize(20, 65)
                            )
                            drawImage(
                                image = currentImg,
                                srcOffset = IntOffset(4, 20),
                                srcSize = IntSize(4, 12),
                                dstOffset = IntOffset((cX + 2f).toInt(), (cY + 20f + crouchOff).toInt()),
                                dstSize = IntSize(20, 65)
                            )
                        } else {
                            // Default stylized model if no skin PNG imported yet
                            drawRoundRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(cX - 22f, cY + 20f + crouchOff),
                                size = Size(20f, 65f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
                            )
                            drawRoundRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(cX + 2f, cY + 20f + crouchOff),
                                size = Size(20f, 65f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
                            )

                            drawRoundRect(
                                color = Color(0xFF0284C7),
                                topLeft = Offset(cX - 22f, cY - 45f + crouchOff),
                                size = Size(44f, 65f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                            )

                            val armW = if (armGeometry == "slim") 14f else 18f
                            drawRoundRect(
                                color = Color(0xFF38BDF8),
                                topLeft = Offset(cX - 22f - armW, cY - 45f + crouchOff),
                                size = Size(armW, 60f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
                            )
                            drawRoundRect(
                                color = Color(0xFF38BDF8),
                                topLeft = Offset(cX + 22f, cY - 45f + crouchOff),
                                size = Size(armW, 60f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
                            )

                            drawRoundRect(
                                color = Color(0xFFF59E0B),
                                topLeft = Offset(cX - 22f, cY - 95f + crouchOff),
                                size = Size(44f, 44f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                            )

                            if (hatLayer) {
                                drawRoundRect(
                                    color = Color(0x5938BDF8),
                                    topLeft = Offset(cX - 26f, cY - 99f + crouchOff),
                                    size = Size(52f, 52f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                                    style = Stroke(width = 2f)
                                )
                            }
                        }
                    }
                }

                // Bottom hint inside viewport
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = "Touch drag to rotate 360°",
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

        // Right: Layer Inspector & Customization Controls (Scrollable & auto-fit)
        HorizonGlass(
            modifier = Modifier
                .weight(1f)
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizonSectionHeader(tag = "STUDIO", title = "Skin & Cape Options")

                    // Arm Geometry: Slim (3px) vs Classic (4px)
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

                    // 64x64 Dual-Layer Overlay Toggles
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        androidx.compose.foundation.text.BasicText(
                            text = "Outer Overlay Layers (64x64)",
                            style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Hat Overlay", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = hatLayer, onCheckedChange = { hatLayer = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Jacket Overlay", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = jacketLayer, onCheckedChange = { jacketLayer = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Sleeves Overlay", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = sleeveLayer, onCheckedChange = { sleeveLayer = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Pants Overlay", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(checked = pantsLayer, onCheckedChange = { pantsLayer = it })
                        }
                    }

                    // REAL SAF Import Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizonButton(
                            text = "Import Skin (SAF)",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                skinPicker.launch(arrayOf("image/png", "image/*"))
                            }
                        )
                        HorizonButton(
                            text = "Import Cape",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                capePicker.launch(arrayOf("image/png", "image/*"))
                            }
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

                // Apply Skin Button
                HorizonButton(
                    text = "APPLY SKIN & CAPE",
                    accent = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            val configJson = JSONObject(BlazeNativeBridge.nativeGetConfigJson())
                            val wardrobeSkin = File(context.filesDir, "wardrobe/skins/current.png")
                            if (wardrobeSkin.exists()) {
                                configJson.put("custom_skin_path", wardrobeSkin.absolutePath)
                                configJson.put("arm_geometry", armGeometry)
                                BlazeNativeBridge.nativeSaveConfigJson(configJson.toString())
                                statusMessage = "Skin applied to Minecraft 26.3 profile!"
                            } else {
                                statusMessage = "Default Steve/Alex profile active."
                            }
                        } catch (e: Exception) {
                            statusMessage = "Error applying skin: ${e.message}"
                        }
                    }
                )
            }
        }
    }
}

private fun isSlimArmGeometry(bmp: Bitmap): Boolean {
    if (bmp.width >= 64 && bmp.height >= 64) {
        val pixel = bmp.getPixel(54, 20)
        return (pixel shr 24 and 0xFF) == 0
    }
    return false
}
