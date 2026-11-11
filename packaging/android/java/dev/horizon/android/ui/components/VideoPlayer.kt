package dev.horizon.android.ui.components

import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.FloatRange
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.horizon.android.R

/**
 * High-performance hardware-accelerated looping video background composable.
 * Powered by AndroidX Media3 ExoPlayer with TextureView surface to eliminate black flash
 * and z-ordering artifacts in Compose hierarchies.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    muted: Boolean = true,
    @FloatRange(from = 0.0, to = 1.0) volume: Float = if (muted) 0.0f else 1.0f,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    refreshTrigger: Any? = null
) {
    val context = LocalContext.current
    val player = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory).build().apply {
            val audioAttr = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttr, false)

            this.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            this.volume = volume
        }
    }

    val playerView = remember {
        val view = try {
            LayoutInflater.from(context).inflate(R.layout.player_texture_view, null) as PlayerView
        } catch (_: Throwable) {
            PlayerView(context).apply {
                useController = false
            }
        }

        view.apply {
            this.player = player
            this.resizeMode = resizeMode
        }
    }

    LaunchedEffect(videoUri, refreshTrigger) {
        val mediaItem = MediaItem.fromUri(videoUri)
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    LaunchedEffect(autoPlay) {
        player.playWhenReady = autoPlay
    }

    LaunchedEffect(loop) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(muted, volume) {
        player.volume = if (muted) 0.0f else volume
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    player.playWhenReady = autoPlay
                }
                Lifecycle.Event.ON_STOP -> {
                    player.playWhenReady = false
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier.fillMaxSize()
    )
}
