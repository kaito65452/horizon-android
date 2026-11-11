package dev.horizon.android.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.horizon.android.HorizonActivity
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.service.BlazeKeepAliveService
import dev.horizon.android.ui.animation.horizontalSlideFadeTransition
import dev.horizon.android.ui.components.AccountManagerDialog
import dev.horizon.android.ui.theme.BlazeColors
import dev.horizon.android.ui.theme.BlazePalettes
import dev.horizon.android.ui.theme.BlazeTheme
import dev.horizon.android.ui.topbar.BlazeTopBar
import dev.horizon.android.ui.views.*
import org.json.JSONObject

/**
 * Root Launcher Activity hosting the lightweight Jetpack Compose Landscape Frontend.
 */
class BlazeLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure engine data is unpacked and initialize JNI backend
        BlazeNativeBridge.ensureEngineData(this)
        BlazeNativeBridge.initIfNeeded(this)
        BlazeKeepAliveService.start(this)

        setContent {
            var currentTab by remember { mutableStateOf("home") }
            var activePaletteKey by remember { mutableStateOf("cyan") }
            var activeAccountName by remember { mutableStateOf(BlazeNativeBridge.getActiveAccountName()) }
            var showAccountDialog by remember { mutableStateOf(false) }

            // Dynamic Background States
            var bgStyle by remember { mutableStateOf("mc") }
            var customBgUri by remember { mutableStateOf<String?>(null) }
            var customBgType by remember { mutableStateOf<String?>(null) }

            fun refreshThemeConfig() {
                try {
                    val configJson = JSONObject(BlazeNativeBridge.nativeGetConfigJson())
                    val theme = configJson.optJSONObject("theme")
                    if (theme != null) {
                        bgStyle = theme.optString("bg_style", "mc")
                        customBgUri = theme.optString("custom_bg_uri", null)
                        customBgType = theme.optString("custom_bg_type", null)
                        val preset = theme.optInt("preset", 0)
                        activePaletteKey = when (preset) {
                            0 -> "cyan"
                            1 -> "emerald"
                            2 -> "crimson"
                            3 -> "violet"
                            else -> "cyan"
                        }
                    }
                } catch (_: Throwable) {}
            }

            LaunchedEffect(Unit) {
                refreshThemeConfig()
                activeAccountName = BlazeNativeBridge.getActiveAccountName()
            }

            val currentPalette = BlazePalettes.fromKey(activePaletteKey)
            val blazeColors = BlazeColors(
                background = Color(0xFF05070B),
                activePalette = currentPalette
            )

            val tabsOrder = listOf("home", "downloads", "manager", "browse", "skin", "controls", "settings")

            BlazeTheme(colors = blazeColors) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Layer 1: Hardware-accelerated background (Video / Photo / OLED / Theme Gradient)
                    BlazeBackgroundLayer(
                        bgStyle = bgStyle,
                        customUri = customBgUri,
                        customType = customBgType
                    )

                    // Layer 2: Main Application Interface
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Navigation Pill Bar
                        BlazeTopBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            accountName = activeAccountName,
                            onAccountClick = { showAccountDialog = true },
                            keepAliveActive = true
                        )

                        // Main Content Stage (Landscape 16:9 with Fluent Slide-Fade)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    val forward = tabsOrder.indexOf(targetState) >= tabsOrder.indexOf(initialState)
                                    horizontalSlideFadeTransition(forward = forward)
                                },
                                label = "stageTabTransition"
                            ) { tab ->
                                when (tab) {
                                    "home" -> HomeView(
                                        onLaunchGame = {
                                            if (!BlazeNativeBridge.areAssetsReady()) {
                                                currentTab = "downloads"
                                                BlazeNativeBridge.nativeStartMultiThreadDownload(8)
                                            } else {
                                                launchGame()
                                            }
                                        },
                                        onNavigate = { currentTab = it },
                                        onAccountClick = { showAccountDialog = true },
                                        profileName = activeAccountName
                                    )
                                    "downloads" -> DownloadsView(
                                        onLaunchGame = { launchGame() }
                                    )
                                    "manager" -> ManagerView()
                                    "browse" -> BrowseView()
                                    "skin" -> SkinStudioView()
                                    "controls" -> GoyControlEditorView()
                                    "settings" -> SettingsView(
                                        onPaletteChanged = {
                                            activePaletteKey = it
                                            refreshThemeConfig()
                                        },
                                        onOpenAccountManager = { showAccountDialog = true }
                                    )
                                }
                            }
                        }
                    }

                    // Layer 3: Account Manager Modal Dialog
                    if (showAccountDialog) {
                        AccountManagerDialog(
                            onDismissRequest = { showAccountDialog = false },
                            onAccountChanged = { newName ->
                                activeAccountName = newName
                                showAccountDialog = false
                            }
                        )
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

/**
 * Background Layer: Renders custom looping muted video (.mp4/.webm), photo (.png/.jpg),
 * pure OLED black, or ambient themed gradient.
 */
@Composable
private fun BlazeBackgroundLayer(
    bgStyle: String,
    customUri: String?,
    customType: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            bgStyle == "custom" && !customUri.isNullOrEmpty() -> {
                val isVideo = customType == "video" ||
                    customUri.endsWith(".mp4", ignoreCase = true) ||
                    customUri.endsWith(".webm", ignoreCase = true) ||
                    customUri.endsWith(".mkv", ignoreCase = true)

                if (isVideo) {
                    VideoBackground(videoPath = customUri)
                } else {
                    PhotoBackground(photoPath = customUri)
                }
                // Ambient readability overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x8C020617))
                )
            }
            bgStyle == "oled" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF000000))
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF05070B),
                                    Color(0xFF081220),
                                    Color(0xFF030712)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun VideoBackground(videoPath: String, modifier: Modifier = Modifier) {
    var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(videoPath) {
        onDispose {
            playerRef?.release()
            playerRef = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        try {
                            val mp = MediaPlayer().apply {
                                setDataSource(videoPath)
                                setSurface(Surface(surface))
                                isLooping = true
                                setVolume(0f, 0f)
                                prepareAsync()
                                setOnPreparedListener { start() }
                                setOnVideoSizeChangedListener { _, vw, vh ->
                                    if (vw > 0 && vh > 0) {
                                        val matrix = Matrix()
                                        val sx = width.toFloat() / vw.toFloat()
                                        val sy = height.toFloat() / vh.toFloat()
                                        val scale = maxOf(sx, sy)
                                        matrix.setScale(scale, scale)
                                        matrix.postTranslate((width - vw * scale) / 2f, (height - vh * scale) / 2f)
                                        setTransform(matrix)
                                    }
                                }
                            }
                            playerRef = mp
                        } catch (e: Exception) {
                            Log.e("VideoBackground", "Failed to start background video: ${e.message}")
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        playerRef?.release()
                        playerRef = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        }
    )
}

@Composable
private fun PhotoBackground(photoPath: String, modifier: Modifier = Modifier) {
    val bitmap = remember(photoPath) {
        try {
            BitmapFactory.decodeFile(photoPath)
        } catch (_: Throwable) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    }
}
