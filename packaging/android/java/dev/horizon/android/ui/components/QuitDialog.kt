package dev.horizon.android.ui.components

import android.app.Activity
import android.os.Build
import android.os.Process
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.horizon.android.R
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.diagnostics.ProcessTracker
import dev.horizon.android.service.BlazeKeepAliveService
import dev.horizon.android.ui.animation.pressBounce
import dev.horizon.android.ui.theme.LocalBlazeColors
import kotlin.system.exitProcess

/**
 * Teardown lifecycle controller executing the 6-step graceful shutdown.
 */
object TeardownLifecycleManager {
    fun executeTeardown(activity: Activity) {
        // Step 2: Signal native engine exit
        try {
            BlazeNativeBridge.signalEngineExit()
        } catch (t: Throwable) {
            System.err.println("Teardown: nativeSignalEngineExit error: ${t.message}")
        }
        ProcessTracker.isGameRunning = false

        // Step 3: Cancel heartbeat / keep-alive tasks
        BlazeKeepAliveService.cancelHeartbeat()

        // Step 4: Stop BlazeKeepAliveService (releasing WakeLock and WifiLock)
        BlazeKeepAliveService.stop(activity)
        ProcessTracker.isServiceRunning = false

        // Step 5: Finish and remove task
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.finishAndRemoveTask()
            } else {
                activity.finishAffinity()
            }
        } catch (t: Throwable) {
            activity.finish()
        }

        // Step 6: Clean process exit
        try {
            Process.killProcess(Process.myPid())
        } catch (t: Throwable) {
            System.err.println("Teardown: killProcess error: ${t.message}")
        }
        exitProcess(0)
    }
}

/**
 * Fluent quit confirmation dialog for Blaze Launcher frontend.
 */
@Composable
fun QuitDialog(
    onDismissRequest: () -> Unit,
    onConfirmQuit: () -> Unit
) {
    val colors = LocalBlazeColors.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99020617)),
            contentAlignment = Alignment.Center
        ) {
            HorizonGlass(
                modifier = Modifier
                    .widthIn(min = 360.dp, max = 440.dp)
                    .padding(20.dp),
                cornerRadius = 20.dp,
                backgroundColor = Color(0xEE0B1220),
                borderColor = Color(0x33FFFFFF)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Warning / Quit Icon Pill
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x26EF4444))
                            .border(1.dp, Color(0x40EF4444), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "⏻",
                            style = TextStyle(
                                color = Color(0xFFEF4444),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // Title
                    BasicText(
                        text = stringResource(R.string.quit_dialog_title),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    )

                    // Description / Message
                    BasicText(
                        text = stringResource(R.string.quit_dialog_message),
                        style = TextStyle(
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 18.sp
                        )
                    )

                    // Subsystem Telemetry Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1AFFFFFF))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "STATUS",
                            style = TextStyle(
                                color = Color(0xFF64748B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        BasicText(
                            text = if (ProcessTracker.isGameRunning) "GAME RUNNING • KEEPALIVE ACTIVE" else "LAUNCHER IDLE",
                            style = TextStyle(
                                color = if (ProcessTracker.isGameRunning) Color(0xFF34D399) else colors.activePalette.primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cancel Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .pressBounce(onClick = onDismissRequest)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = stringResource(R.string.cancel),
                                style = TextStyle(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        // Confirm / Exit Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .pressBounce(onClick = onConfirmQuit)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFDC2626))
                                .border(1.dp, Color(0xFFFF6B6B), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = stringResource(R.string.confirm),
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
