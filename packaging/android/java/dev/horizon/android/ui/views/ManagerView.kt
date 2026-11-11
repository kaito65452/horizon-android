package dev.horizon.android.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.horizon.android.ui.components.HorizonButton
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.components.HorizonPill
import dev.horizon.android.ui.theme.LocalBlazeColors

@Composable
fun ManagerView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    var subTab by remember { mutableStateOf("library") }
    var filterType by remember { mutableStateOf("all") }
    var actionStatus by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sub-tabs: Library vs File Manager + SAF actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("library" to "Library", "files" to "File Manager").forEach { (key, label) ->
                    val isSel = subTab == key
                    HorizonPill(
                        backgroundColor = if (isSel) colors.activePalette.primary else Color(0x1AFFFFFF),
                        borderColor = if (isSel) colors.activePalette.primary else Color(0x26FFFFFF),
                        onClick = { subTab = key }
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = label,
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isSel) Color(0xFF020617) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // SAF Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizonButton(
                    text = "Export Worlds (SAF)",
                    onClick = {
                        val ok = BlazeNativeBridge.nativeExportWorlds()
                        actionStatus = if (ok) "Worlds exported to SAF." else "Export failed."
                    }
                )
                HorizonButton(
                    text = "Import .hmod / .zip",
                    onClick = {
                        actionStatus = "Select an .hmod, .zip or shader package from storage."
                    }
                )
            }
        }

        // Installed Content Glass Panel
        HorizonGlass(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (actionStatus.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x3310B981))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = actionStatus,
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF6EE7B7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Installed items
                val installed = listOf(
                    Triple("Complex Voice Chat (ported by Asano Saryu)", "Built-in • UDP 3D proximity chat with RNNoise & AGC", "Enabled"),
                    Triple("Sodium (Vulkan Mobile)", "Built-in • Modern Vulkan chunk mesh pipeline", "Enabled"),
                    Triple("Faithful 64x", "Resource Pack • High-definition 64x vanilla textures", "Active"),
                    Triple("Complementary Reimagined", "Shader Pack • Optimized mobile shader pipeline", "Active")
                )

                installed.forEach { (title, subtitle, status) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            androidx.compose.foundation.text.BasicText(
                                text = title,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            androidx.compose.foundation.text.BasicText(
                                text = subtitle,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2610B981))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = status,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
