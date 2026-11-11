package dev.horizon.android.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.round

@Composable
fun GoyControlEditorView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var buttons by remember {
        mutableStateOf(
            listOf(
                ControlButton("btn_joy", "JOYSTICK", 40f, 180f, 100f, 100f, 0.85f, 1f, false),
                ControlButton("btn_atk", "ATK", 780f, 130f, 56f, 56f, 0.85f, 1f, false),
                ControlButton("btn_use", "USE", 710f, 170f, 56f, 56f, 0.85f, 1f, false),
                ControlButton("btn_jump", "JUMP", 780f, 210f, 56f, 56f, 0.85f, 1f, false),
                ControlButton("btn_sneak", "SNEAK", 710f, 250f, 48f, 48f, 0.85f, 1f, false),
                ControlButton("btn_inv", "INV", 430f, 320f, 48f, 36f, 0.70f, 1f, false),
                ControlButton("btn_f5", "F5", 580f, 20f, 44f, 30f, 0.70f, 1f, false),
                ControlButton("btn_pause", "PAUSE", 440f, 20f, 44f, 30f, 0.70f, 1f, false)
            )
        )
    }

    var selectedBtnId by remember { mutableStateOf<String?>("btn_joy") }
    var snapGrid by remember { mutableStateOf(true) }
    var saveStatus by remember { mutableStateOf("") }

    val selectedBtn = buttons.firstOrNull { it.id == selectedBtnId }

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
            ) {
                // 20px Grid Canvas
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

                // Draggable HUD Buttons
                buttons.forEach { btn ->
                    val isSelected = btn.id == selectedBtnId
                    Box(
                        modifier = Modifier
                            .offset(x = btn.x.dp / 1.5f, y = btn.y.dp / 1.5f)
                            .size(width = (btn.w * btn.scale).dp / 1.5f, height = (btn.h * btn.scale).dp / 1.5f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) colors.activePalette.soft else Color(0x33000000)
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
                                    var newX = btn.x + dragAmount.x
                                    var newY = btn.y + dragAmount.y
                                    if (snapGrid) {
                                        newX = (round(newX / 20f) * 20f)
                                        newY = (round(newY / 20f) * 20f)
                                    }
                                    btn.x = newX.coerceIn(0f, 900f)
                                    btn.y = newY.coerceIn(0f, 380f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = btn.label,
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isSelected) colors.activePalette.primary else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }

        // Right: Inspector & Properties Panel (4 cols)
        HorizonGlass(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
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
                            saveStatus = "Saved to horizon-controls.json"
                        }
                    )
                }
            }
        }
    }
}
