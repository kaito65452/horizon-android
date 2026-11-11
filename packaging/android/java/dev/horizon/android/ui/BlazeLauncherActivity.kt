package dev.horizon.android.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.horizon.android.HorizonActivity
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.service.BlazeKeepAliveService
import dev.horizon.android.ui.theme.BlazeColors
import dev.horizon.android.ui.theme.BlazePalettes
import dev.horizon.android.ui.theme.BlazeTheme
import dev.horizon.android.ui.topbar.BlazeTopBar
import dev.horizon.android.ui.views.*

/**
 * Root Launcher Activity hosting the lightweight Jetpack Compose Landscape Frontend.
 */
class BlazeLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize JNI bridge and start persistence keep-alive service
        BlazeNativeBridge.initIfNeeded(this)
        BlazeKeepAliveService.start(this)

        setContent {
            var currentTab by remember { mutableStateOf("home") }
            var activePaletteKey by remember { mutableStateOf("cyan") }

            val currentPalette = BlazePalettes.fromKey(activePaletteKey)
            val blazeColors = BlazeColors(
                background = Color(0xFF05070B),
                activePalette = currentPalette
            )

            BlazeTheme(colors = blazeColors) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(blazeColors.background)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Navigation Pill Bar (.mx-head)
                        BlazeTopBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            accountName = "Player",
                            keepAliveActive = true
                        )

                        // Main Content Stage (Proportional 16:9 Landscape)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            when (currentTab) {
                                "home" -> HomeView(
                                    onLaunchGame = {
                                        if (!BlazeNativeBridge.areAssetsReady()) {
                                            currentTab = "downloads"
                                            BlazeNativeBridge.nativeStartMultiThreadDownload(8)
                                        } else {
                                            launchGame()
                                        }
                                    },
                                    onNavigate = { currentTab = it }
                                )
                                "downloads" -> DownloadsView(
                                    onLaunchGame = { launchGame() }
                                )
                                "manager" -> ManagerView()
                                "browse" -> BrowseView()
                                "skin" -> SkinStudioView()
                                "controls" -> GoyControlEditorView()
                                "settings" -> SettingsView(
                                    onPaletteChanged = { activePaletteKey = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchGame() {
        BlazeNativeBridge.nativeLaunchGame(0)
        val intent = Intent(this, HorizonActivity::class.java)
        startActivity(intent)
    }
}
