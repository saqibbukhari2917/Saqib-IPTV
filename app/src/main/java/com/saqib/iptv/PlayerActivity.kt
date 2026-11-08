package com.saqib.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val name = intent.getStringExtra("channel_name") ?: "Live channel"
        val stream = intent.getStringExtra("channel_url").orEmpty()

        var initialError: String? = null
        if (stream.isBlank() || !(stream.startsWith("http://", true) || stream.startsWith("https://", true))) {
            initialError = "Is channel ka stream link valid nahi hai."
        } else {
            try {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("SaqibIPTV/1.0 (Android)")
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(30000)
                    .setAllowCrossProtocolRedirects(true)

                exoPlayer = ExoPlayer.Builder(this)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory))
                    .build().apply {
                        setMediaItem(MediaItem.fromUri(stream))
                        prepare()
                        playWhenReady = true
                    }
            } catch (e: Exception) {
                initialError = "Player start nahi ho saka: ${e.localizedMessage ?: "unknown error"}"
            }
        }

        setContent {
            var playbackError by remember { mutableStateOf(initialError) }

            DisposableEffect(exoPlayer) {
                val player = exoPlayer
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_TIMEOUT -> "Internet connection ya stream server ka response nahi aa raha."
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Is channel ka stream format supported nahi hai."
                            else -> "Ye channel abhi play nahi ho saka. Mumkin hai stream offline ya region-restricted ho."
                        }
                    }
                }
                player?.addListener(listener)
                onDispose { player?.removeListener(listener) }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxSize()) {
                    Text(name, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                    if (playbackError != null) {
                        Text(
                            playbackError ?: "",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (exoPlayer != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = { context ->
                                PlayerView(context).apply {
                                    player = this@PlayerActivity.exoPlayer
                                    useController = true
                                }
                            },
                            update = { it.player = exoPlayer }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    override fun onStop() {
        exoPlayer?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
