package dev.horizon.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.ui.theme.BlazePalettes
import dev.horizon.android.ui.theme.LocalBlazeColors

@Composable
fun HorizonGlass(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = LocalBlazeColors.current.glassBorder,
    backgroundColor: Color = LocalBlazeColors.current.glassBg,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius)),
        content = content
    )
}

@Composable
fun HorizonPill(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalBlazeColors.current.pillBg,
    borderColor: Color = LocalBlazeColors.current.pillBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val mod = if (onClick != null) {
        modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    } else {
        modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
    }

    Row(
        modifier = mod.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

@Composable
fun HorizonButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalBlazeColors.current
    val bg = when {
        accent -> colors.activePalette.primary
        active -> colors.activePalette.soft
        else -> colors.pillBg
    }
    val borderCol = when {
        accent -> colors.activePalette.primary
        active -> colors.activePalette.primary
        else -> colors.pillBorder
    }
    val textCol = when {
        accent -> Color(0xFF020617)
        active -> colors.activePalette.primary
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                color = textCol,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun HorizonSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val trackBg = if (checked) colors.activePalette.primary else Color(0x33FFFFFF)
    val thumbCol = if (checked) Color(0xFF0F172A) else Color.White

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(trackBg)
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onCheckedChange(!checked) }
            )
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbCol)
        )
    }
}

@Composable
fun HorizonSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(28.dp)
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange) {
                detectDragGestures { change, _ ->
                    val newFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                    val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange(newValue)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x33FFFFFF))
        )
        // Fill
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.001f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.activePalette.primary)
        )
        // Thumb
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = ((widthPx * fraction) - 9f).coerceAtLeast(0f).toInt(),
                        y = 0
                    )
                }
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, colors.activePalette.primary, CircleShape)
        )
    }
}

@Composable
fun HorizonSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, opt ->
            val isSelected = i == selectedIndex
            val bg = if (isSelected) colors.activePalette.primary else Color.Transparent
            val textCol = if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onSelect(i) }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = opt,
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

@Composable
fun HorizonSwatchRow(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BlazePalettes.All.forEach { pal ->
            val isSel = pal.key == selectedKey || (selectedKey == "cyan" && pal.key == "cyan")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.soft)
                    .border(
                        width = if (isSel) 2.dp else 1.dp,
                        color = if (isSel) pal.primary else Color(0x33FFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onSelect(pal.key) }
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(pal.primary)
                    )
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.text.BasicText(
                        text = pal.name,
                        style = androidx.compose.ui.text.TextStyle(
                            color = if (isSel) pal.primary else Color(0xFFE2E8F0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun HorizonSectionHeader(
    tag: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.text.BasicText(
            text = tag.uppercase(),
            style = androidx.compose.ui.text.TextStyle(
                color = colors.activePalette.primary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )
        androidx.compose.foundation.text.BasicText(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x1AFFFFFF))
        )
        Spacer(Modifier.height(6.dp))
    }
}
