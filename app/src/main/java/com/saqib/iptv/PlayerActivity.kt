package com.saqib.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("channel_name") ?: "Live channel"
        val stream = intent.getStringExtra("channel_url").orEmpty()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(stream))
            prepare()
            playWhenReady = true
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxSize()) {
                    Text(name, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                    AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                        PlayerView(context).apply { player = this@PlayerActivity.exoPlayer; useController = true }
                    })
                }
            }
        }
    }
    override fun onStop() { super.onStop(); exoPlayer?.pause() }
    override fun onDestroy() { exoPlayer?.release(); exoPlayer = null; super.onDestroy() }
}
