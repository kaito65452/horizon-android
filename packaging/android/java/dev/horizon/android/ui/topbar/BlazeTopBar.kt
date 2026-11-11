package dev.horizon.android.ui.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.ui.components.HorizonPill
import dev.horizon.android.ui.theme.LocalBlazeColors

@Composable
fun BlazeTopBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    accountName: String = "Player",
    onAccountClick: (() -> Unit)? = null,
    keepAliveActive: Boolean = true,
    downloadCount: Int = 0,
    onQuitClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Brand Lockup
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onTabSelected("home") }
            )
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.activePalette.primary),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = "B",
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF020617),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                androidx.compose.foundation.text.BasicText(
                    text = "Blaze",
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.text.BasicText(
                    text = "LAUNCHER",
                    style = androidx.compose.ui.text.TextStyle(
                        color = colors.activePalette.primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }

        // Center: Pill Navigation Tabs
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val tabs = listOf(
                "home" to "Home",
                "downloads" to "Downloads",
                "manager" to "Manager",
                "browse" to "Browse",
                "skin" to "Skin"
            )

            tabs.forEach { (key, label) ->
                val isSelected = currentTab == key
                val bg = if (isSelected) colors.activePalette.primary else Color.Transparent
                val fg = if (isSelected) Color(0xFF020617) else Color(0xFFCBD5E1)

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bg)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onTabSelected(key) }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = label,
                            style = androidx.compose.ui.text.TextStyle(
                                color = fg,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                        if (key == "downloads" && downloadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF020617) else colors.activePalette.primary)
                            )
                        }
                    }
                }
            }
        }

        // Right: Status & Quick Action Pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Keep-Alive Shield Pill
            HorizonPill(
                backgroundColor = if (keepAliveActive) Color(0x2610B981) else Color(0x1AFFFFFF),
                borderColor = if (keepAliveActive) Color(0x4010B981) else Color(0x1AFFFFFF),
                onClick = { onTabSelected("settings") }
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (keepAliveActive) Color(0xFF34D399) else Color(0xFF94A3B8))
                )
                Spacer(Modifier.width(5.dp))
                androidx.compose.foundation.text.BasicText(
                    text = "KEEP-ALIVE",
                    style = androidx.compose.ui.text.TextStyle(
                        color = if (keepAliveActive) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Account Pill
            HorizonPill(onClick = { onAccountClick?.invoke() ?: onTabSelected("skin") }) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(colors.activePalette.primary),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = accountName.firstOrNull()?.uppercase() ?: "P",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFF020617),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
                Spacer(Modifier.width(6.dp))
                androidx.compose.foundation.text.BasicText(
                    text = accountName,
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFF1F5F9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Settings Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (currentTab == "settings") colors.activePalette.primary else Color(0x1AFFFFFF))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onTabSelected("settings") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = "⚙",
                    style = androidx.compose.ui.text.TextStyle(
                        color = if (currentTab == "settings") Color(0xFF020617) else Color.White,
                        fontSize = 13.sp
                    )
                )
            }

            // Quick Exit Button
            if (onQuitClick != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1AEF4444))
                        .border(1.dp, Color(0x33EF4444), RoundedCornerShape(8.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onQuitClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = "⏻",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
