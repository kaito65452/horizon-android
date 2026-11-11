package dev.horizon.android.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.theme.LocalBlazeColors

@Composable
fun HomeView(
    onLaunchGame: () -> Unit,
    onNavigate: (String) -> Unit,
    profileName: String = "Rust",
    engineVersion: String = "26.3 • Blaze Engine",
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var noticeDismissed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ROW 1: Hero Banner (.mx-hero)
        HorizonGlass(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.35f),
            cornerRadius = 16.dp,
            backgroundColor = Color(0xD9081220)
        ) {
            // Gradient backdrop evoking underwater aquatic scene
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF041829),
                                Color(0xFF0A2E44),
                                Color(0xFF0D3B54)
                            )
                        )
                    )
            )

            // Hero content layer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row inside Hero: Engine chip + Feats pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title
                    Column {
                        androidx.compose.foundation.text.BasicText(
                            text = "BLAZE",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                        androidx.compose.foundation.text.BasicText(
                            text = "PLAY WITHOUT LIMITS",
                            style = androidx.compose.ui.text.TextStyle(
                                color = colors.activePalette.primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        )
                    }

                    // Engine Chip
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x26FFFFFF))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = engineVersion,
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Bottom row inside Hero: Notice banner (if visible) + Feats pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!noticeDismissed) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33000000))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Built-in Complex Voice Chat (ported by Asano Saryu) active",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 10.sp
                                )
                            )
                            androidx.compose.foundation.text.BasicText(
                                text = "✕",
                                modifier = Modifier.clickable { noticeDismissed = true },
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    // Feats pill: FAST | STABLE | CUSTOMIZABLE
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x26000000))
                            .border(1.dp, Color(0x26FFFFFF), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("FAST", "STABLE", "CUSTOMIZABLE").forEachIndexed { i, f ->
                            if (i > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66FFFFFF))
                                )
                            }
                            androidx.compose.foundation.text.BasicText(
                                text = f,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = colors.activePalette.primary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }

        // ROW 2: Action Deck (.mx-row2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Selected Profile Card
            HorizonGlass(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onNavigate("skin") }
                    ),
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        androidx.compose.foundation.text.BasicText(
                            text = "SELECTED PROFILE",
                            style = androidx.compose.ui.text.TextStyle(
                                color = colors.activePalette.primary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        androidx.compose.foundation.text.BasicText(
                            text = profileName,
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        androidx.compose.foundation.text.BasicText(
                            text = "26.3 • Blaze Engine • Ready",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "▼",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 8.sp
                            )
                        )
                    }
                }
            }

            // EDIT Button
            HorizonGlass(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onNavigate("controls") }
                    ),
                cornerRadius = 14.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = "EDIT",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Glowing PLAY Button
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                colors.activePalette.playStart,
                                colors.activePalette.playMid,
                                colors.activePalette.playEnd
                            )
                        )
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onLaunchGame
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = "▶",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFF0F172A),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                    Column {
                        androidx.compose.foundation.text.BasicText(
                            text = "PLAY",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF0F172A),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                        androidx.compose.foundation.text.BasicText(
                            text = "26.3 • Blaze Engine",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF1E293B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // ROW 3: Shortcut Cards (.mx-row3)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 1: BROWSE RESOURCES
            HorizonGlass(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onNavigate("browse") }
                    ),
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        androidx.compose.foundation.text.BasicText(
                            text = "BROWSE RESOURCES",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        androidx.compose.foundation.text.BasicText(
                            text = "Mods • Resource Packs • Shader Packs",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "→",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // Card 2: MANAGER
            HorizonGlass(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onNavigate("manager") }
                    ),
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        androidx.compose.foundation.text.BasicText(
                            text = "MANAGER",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        androidx.compose.foundation.text.BasicText(
                            text = "Manage Installed Mods, Packs and Worlds",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "→",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
