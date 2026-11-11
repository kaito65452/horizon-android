package dev.horizon.android.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.horizon.android.HorizonActivity
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.diagnostics.ProcessTracker
import dev.horizon.android.service.BlazeKeepAliveService
import dev.horizon.android.ui.animation.horizontalSlideFadeTransition
import dev.horizon.android.ui.components.AccountManagerDialog
import dev.horizon.android.ui.components.QuitDialog
import dev.horizon.android.ui.components.TeardownLifecycleManager
import dev.horizon.android.ui.components.VideoPlayer
import dev.horizon.android.ui.theme.BlazeColors
import dev.horizon.android.ui.theme.BlazePalettes
import dev.horizon.android.ui.theme.BlazeTheme
import dev.horizon.android.ui.theme.ThemeState
import dev.horizon.android.ui.topbar.BlazeTopBar
import dev.horizon.android.ui.views.*
import org.json.JSONObject
import java.io.File

/**
 * Root Launcher Activity hosting the lightweight Jetpack Compose Landscape Frontend.
 */
class BlazeLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen()

        // Ensure engine data is unpacked and initialize JNI backend
        BlazeNativeBridge.ensureEngineData(this)
        BlazeNativeBridge.initIfNeeded(this)
        BlazeKeepAliveService.start(this)

        setContent {
            var currentTab by remember { mutableStateOf("home") }
            var activeAccountName by remember { mutableStateOf(BlazeNativeBridge.getActiveAccountName()) }
            var showAccountDialog by remember { mutableStateOf(false) }
            var showQuitDialog by remember { mutableStateOf(false) }

            // Intercept system Back button to display fluent Quit Confirmation Dialog
            BackHandler(enabled = true) {
                showQuitDialog = true
            }

            fun refreshThemeConfig() {
                try {
                    val configJson = JSONObject(BlazeNativeBridge.nativeGetConfigJson())
                    val theme = configJson.optJSONObject("theme")
                    val bgStyle = theme?.optString("bg_style", "mc") ?: "mc"
                    val customBgUri = theme?.optString("custom_bg_uri", null)
                    val customBgType = theme?.optString("custom_bg_type", null)
                    val presetStr = when {
                        theme?.has("preset") == true -> {
                            val p = theme.get("preset")
                            if (p is Number) {
                                when (p.toInt()) {
                                    1 -> "emerald"
                                    2 -> "crimson"
                                    3 -> "violet"
                                    4 -> "amber"
                                    else -> "cyan"
                                }
                            } else {
                                p.toString()
                            }
                        }
                        theme?.has("palette_idx") == true -> {
                            when (theme.optInt("palette_idx", 0)) {
                                1 -> "emerald"
                                2 -> "crimson"
                                3 -> "violet"
                                4 -> "amber"
                                else -> "cyan"
                            }
                        }
                        else -> "cyan"
                    }
                    val activePaletteKey = when (presetStr.lowercase()) {
                        "emerald", "green" -> "emerald"
                        "crimson", "red" -> "crimson"
                        "violet" -> "violet"
                        "amber", "gold" -> "amber"
                        else -> "cyan"
                    }
                    val opacity = if (theme != null && theme.has("glass_opacity")) {
                        theme.optDouble("glass_opacity", 0.72).toFloat()
                    } else if (configJson.has("panel_opacity")) {
                        configJson.optDouble("panel_opacity", 72.0).toFloat() / 100f
                    } else {
                        0.72f
                    }
                    val blur = if (theme != null && theme.has("glass_blur")) {
                        theme.optDouble("glass_blur", 16.0).toFloat()
                    } else if (configJson.has("panel_blur")) {
                        configJson.optDouble("panel_blur", 16.0).toFloat()
                    } else {
                        16.0f
                    }
                    ThemeState.update(
                        accentKey = activePaletteKey,
                        panelOpacity = opacity.coerceIn(0.40f, 0.98f),
                        blurRadiusDp = blur.coerceIn(0f, 32f),
                        backgroundType = bgStyle,
                        customBgUri = customBgUri,
                        customBgType = customBgType
                    )
                } catch (_: Throwable) {}
            }

            LaunchedEffect(Unit) {
                refreshThemeConfig()
                activeAccountName = BlazeNativeBridge.getActiveAccountName()
            }

            val themeConfig by ThemeState.state.collectAsState()
            val currentPalette = BlazePalettes.fromKey(themeConfig.accentKey)
            val blazeColors = BlazeColors(
                background = Color(0xFF05070B),
                activePalette = currentPalette,
                cardOpacity = themeConfig.panelOpacity,
                cardBlurDp = themeConfig.blurRadiusDp,
                glassBg = Color(0xFF0F1523).copy(alpha = themeConfig.panelOpacity),
                cardHeroBg = Color(0xFF0A1120).copy(alpha = themeConfig.panelOpacity.coerceAtMost(0.9f))
            )

            val tabsOrder = listOf("home", "downloads", "manager", "browse", "skin", "settings")

            BlazeTheme(colors = blazeColors) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Layer 1: Hardware-accelerated background (Video / Photo / OLED / Theme Gradient)
                    BlazeBackgroundLayer(
                        bgStyle = themeConfig.backgroundType,
                        customUri = themeConfig.customBgUri,
                        customType = themeConfig.customBgType,
                        blurDp = themeConfig.blurRadiusDp
                    )

                    // Layer 2: Main Application Interface
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Navigation Pill Bar
                        BlazeTopBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            accountName = activeAccountName,
                            onAccountClick = { showAccountDialog = true },
                            keepAliveActive = true,
                            onQuitClick = { showQuitDialog = true }
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
                                    "settings" -> SettingsView(
                                        onPaletteChanged = { paletteKey ->
                                            ThemeState.update(accentKey = paletteKey)
                                            refreshThemeConfig()
                                        },
                                        onThemeChanged = {
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

                    // Layer 4: Fluent Quit Confirmation Modal Dialog
                    if (showQuitDialog) {
                        QuitDialog(
                            onDismissRequest = { showQuitDialog = false },
                            onConfirmQuit = {
                                showQuitDialog = false
                                TeardownLifecycleManager.executeTeardown(this@BlazeLauncherActivity)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreen()
        }
    }

    private fun applyFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        }
        val decorView = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            decorView.windowInsetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    override fun onResume() {
        super.onResume()
        ProcessTracker.isGameRunning = false
    }

    override fun onDestroy() {
        BlazeKeepAliveService.stop(this)
        super.onDestroy()
    }

    private fun launchGame() {
        ProcessTracker.isGameRunning = true
        BlazeNativeBridge.nativeLaunchGame(0)
        val intent = Intent(this, HorizonActivity::class.java)
        startActivity(intent)
    }
}

/**
 * Background Layer: Renders custom looping muted video (.mp4/.webm), photo (.png/.jpg),
 * pure OLED black, or ambient themed gradient.
 * Supports hardware-accelerated blur via RenderEffect (Android 12+) or ambient scrim fallback.
 */
@Composable
private fun BlazeBackgroundLayer(
    bgStyle: String,
    customUri: String?,
    customType: String?,
    blurDp: Float = 0f,
    modifier: Modifier = Modifier
) {
    val blurModifier = if (blurDp > 0f && Build.VERSION.SDK_INT >= 31) {
        Modifier.blur(blurDp.dp)
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize().then(blurModifier)) {
        when {
            bgStyle == "custom" && !customUri.isNullOrEmpty() -> {
                val isVideo = customType == "video" ||
                    customUri.endsWith(".mp4", ignoreCase = true) ||
                    customUri.endsWith(".webm", ignoreCase = true) ||
                    customUri.endsWith(".mkv", ignoreCase = true)

                if (isVideo) {
                    val uri = if (customUri.startsWith("content://") || customUri.startsWith("file://")) {
                        Uri.parse(customUri)
                    } else {
                        Uri.fromFile(File(customUri))
                    }
                    VideoPlayer(
                        videoUri = uri,
                        modifier = Modifier.fillMaxSize(),
                        autoPlay = true,
                        loop = true,
                        muted = true
                    )
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
private fun PhotoBackground(photoPath: String, modifier: Modifier = Modifier) {
    val bitmap = remember(photoPath) {
        try {
            val baseBmp = BitmapFactory.decodeFile(photoPath) ?: return@remember null
            val exif = try {
                ExifInterface(photoPath)
            } catch (_: Throwable) {
                null
            }
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            if (!matrix.isIdentity) {
                Bitmap.createBitmap(baseBmp, 0, 0, baseBmp.width, baseBmp.height, matrix, true)
            } else {
                baseBmp
            }
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
