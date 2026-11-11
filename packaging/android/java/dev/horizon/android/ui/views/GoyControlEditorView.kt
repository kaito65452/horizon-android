package dev.horizon.android.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.bridge.ControlButton
import dev.horizon.android.ui.components.HorizonButton
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.components.HorizonSectionHeader
import dev.horizon.android.ui.components.HorizonSlider
import dev.horizon.android.ui.components.HorizonSwitch
import dev.horizon.android.ui.theme.LocalBlazeColors
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun GoyControlEditorView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var canvasWidthPx by remember { mutableFloatStateOf(600f) }
    var canvasHeightPx by remember { mutableFloatStateOf(350f) }

    var buttons by remember {
        mutableStateOf(
            listOf(
                ControlButton("btn_joy", "JOYSTICK", 30f, 160f, 80f, 80f, 0.85f, 1f, false),
                ControlButton("btn_atk", "ATK", 440f, 60f, 48f, 48f, 0.85f, 1f, false),
                ControlButton("btn_use", "USE", 380f, 95f, 48f, 48f, 0.85f, 1f, false),
                ControlButton("btn_jump", "JUMP", 440f, 130f, 48f, 48f, 0.85f, 1f, false),
                ControlButton("btn_sneak", "SNEAK", 380f, 165f, 42f, 42f, 0.85f, 1f, false),
                ControlButton("btn_inv", "INV", 220f, 210f, 44f, 32f, 0.70f, 1f, false),
                ControlButton("btn_f5", "F5", 300f, 16f, 38f, 26f, 0.70f, 1f, false),
                ControlButton("btn_pause", "PAUSE", 230f, 16f, 38f, 26f, 0.70f, 1f, false)
            )
        )
    }

    var selectedBtnId by remember { mutableStateOf<String?>("btn_joy") }
    var snapGrid by remember { mutableStateOf(true) }
    var saveStatus by remember { mutableStateOf("") }

    val selectedBtn = buttons.firstOrNull { it.id == selectedBtnId }

    LaunchedEffect(Unit) {
        try {
            val jsonStr = BlazeNativeBridge.nativeGetControlLayoutJson()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ControlButton>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ControlButton(
                        id = obj.optString("id"),
                        label = obj.optString("label"),
                        x = obj.optDouble("x", 40.0).toFloat(),
                        y = obj.optDouble("y", 40.0).toFloat(),
                        w = obj.optDouble("w", 50.0).toFloat(),
                        h = obj.optDouble("h", 50.0).toFloat(),
                        opacity = obj.optDouble("opacity", 0.85).toFloat(),
                        scale = obj.optDouble("scale", 1.0).toFloat(),
                        cameraPassthrough = obj.optBoolean("camera_passthrough", false)
                    )
                )
            }
            if (list.isNotEmpty()) {
                buttons = list
            }
        } catch (_: Throwable) {}
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left: 20px Grid Drag Canvas (8 cols)
        HorizonGlass(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .onSizeChanged {
                        canvasWidthPx = it.width.toFloat()
                        canvasHeightPx = it.height.toFloat()
                    }
            ) {
                // 20px Grid Background
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 20.dp.toPx()
                    val w = size.width
                    val h = size.height

                    var x = 0f
                    while (x < w) {
                        drawLine(
                            color = Color(0x14FFFFFF),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                        x += step
                    }

                    var y = 0f
                    while (y < h) {
                        drawLine(
                            color = Color(0x14FFFFFF),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }
                }

                // Draggable Control Buttons
                buttons.forEach { btn ->
                    val isSelected = btn.id == selectedBtnId
                    val bWidthDp = (btn.w * btn.scale).dp
                    val bHeightDp = (btn.h * btn.scale).dp

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = btn.x.dp.toPx().toInt().coerceIn(0, (canvasWidthPx - 60f).toInt().coerceAtLeast(0)),
                                    y = btn.y.dp.toPx().toInt().coerceIn(0, (canvasHeightPx - 40f).toInt().coerceAtLeast(0))
                                )
                            }
                            .size(width = bWidthDp, height = bHeightDp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) colors.activePalette.soft else Color(0x40000000)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) colors.activePalette.primary else Color(0x4DFFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedBtnId = btn.id }
                            .pointerInput(btn.id, snapGrid) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    selectedBtnId = btn.id
                                    val density = this.density
                                    val dragXDp = dragAmount.x / density
                                    val dragYDp = dragAmount.y / density
                                    var newX = btn.x + dragXDp
                                    var newY = btn.y + dragYDp
                                    if (snapGrid) {
                                        newX = (newX / 20f).roundToInt() * 20f
                                        newY = (newY / 20f).roundToInt() * 20f
                                    }
                                    btn.x = newX.coerceIn(0f, 600f)
                                    btn.y = newY.coerceIn(0f, 320f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = btn.label,
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isSelected) colors.activePalette.primary else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }

        // Right: Inspector & Properties Panel (Scrollable & auto-fit)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizonSectionHeader(tag = "GOY CONTROL", title = "Control Properties")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "Snap to 20px Grid",
                            style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        )
                        HorizonSwitch(checked = snapGrid, onCheckedChange = { snapGrid = it })
                    }

                    if (selectedBtn != null) {
                        androidx.compose.foundation.text.BasicText(
                            text = "Selected: ${selectedBtn.label}",
                            style = androidx.compose.ui.text.TextStyle(
                                color = colors.activePalette.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        // Button Scale
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                androidx.compose.foundation.text.BasicText(text = "Scale", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                                androidx.compose.foundation.text.BasicText(text = "${"%.1f".format(selectedBtn.scale)}x", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                            }
                            HorizonSlider(
                                value = selectedBtn.scale,
                                onValueChange = { selectedBtn.scale = it },
                                valueRange = 0.5f..2.0f
                            )
                        }

                        // Button Opacity
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                androidx.compose.foundation.text.BasicText(text = "Opacity", style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp))
                                androidx.compose.foundation.text.BasicText(text = "${(selectedBtn.opacity * 100).toInt()}%", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                            }
                            HorizonSlider(
                                value = selectedBtn.opacity,
                                onValueChange = { selectedBtn.opacity = it },
                                valueRange = 0.2f..1.0f
                            )
                        }

                        // Camera Passthrough
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(text = "Camera Passthrough", style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp))
                            HorizonSwitch(
                                checked = selectedBtn.cameraPassthrough,
                                onCheckedChange = { selectedBtn.cameraPassthrough = it }
                            )
                        }
                    } else {
                        androidx.compose.foundation.text.BasicText(
                            text = "Tap any button on canvas to edit its scale and opacity.",
                            style = androidx.compose.ui.text.TextStyle(color = Color(0xFF94A3B8), fontSize = 11.sp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (saveStatus.isNotEmpty()) {
                        androidx.compose.foundation.text.BasicText(
                            text = saveStatus,
                            style = androidx.compose.ui.text.TextStyle(color = Color(0xFF34D399), fontSize = 10.sp)
                        )
                    }
                    HorizonButton(
                        text = "SAVE LAYOUT",
                        accent = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val array = JSONArray()
                            buttons.forEach { b ->
                                val obj = JSONObject()
                                obj.put("id", b.id)
                                obj.put("label", b.label)
                                obj.put("x", b.x.toDouble())
                                obj.put("y", b.y.toDouble())
                                obj.put("w", b.w.toDouble())
                                obj.put("h", b.h.toDouble())
                                obj.put("opacity", b.opacity.toDouble())
                                obj.put("scale", b.scale.toDouble())
                                obj.put("camera_passthrough", b.cameraPassthrough)
                                array.put(obj)
                            }
                            val saved = BlazeNativeBridge.nativeSaveControlLayoutJson(array.toString())
                            saveStatus = if (saved) "Saved to horizon-touch.json" else "Failed to save"
                        }
                    )
                }
            }
        }
    }
}
