package com.saqib.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private data class CricketPlatform(val name: String, val detail: String, val url: String)

private val cricketPlatforms = listOf(
    CricketPlatform("Myco", "Official digital partner for ICC events in Pakistan", "https://www.myco.io/"),
    CricketPlatform("Tamasha", "Official streaming partner in Pakistan; match access may vary", "https://tamashaweb.com/"),
    CricketPlatform("Tapmad", "Official sports streaming partner; subscription or login may be required", "https://www.tapmad.com/"),
    CricketPlatform("ARY ZAP", "Official streaming partner for selected cricket coverage", "https://aryzap.com/"),
    CricketPlatform("PTV Sports", "Official Pakistani sports broadcaster", "https://www.ptv-sports.com.pk/live-streaming/"),
    CricketPlatform("Geo Super", "Pakistani sports broadcaster; coverage depends on rights", "https://www.geo.tv/")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("iptv_settings", MODE_PRIVATE)
        setContent {
            var playlistUrl by remember { mutableStateOf(prefs.getString("playlist_url", "") ?: "") }
            var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
            var selectedTab by remember { mutableStateOf("All") }
            var searchText by remember { mutableStateOf("") }
            var status by remember { mutableStateOf("Loading public Pakistan, India and sports channels...") }
            var loading by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            fun refreshChannels() {
                if (loading) return
                loading = true
                status = "Loading playlists..."
                scope.launch {
                    try {
                        val loaded = withContext(Dispatchers.IO) {
                            val sports = try {
                                M3uParser.parse(downloadPlaylist("https://iptv-org.github.io/iptv/categories/sports.m3u"), "Sports")
                            } catch (_: Exception) { emptyList() }

                            val baseChannels = if (playlistUrl.isBlank()) {
                                val pakistan = downloadPlaylist("https://iptv-org.github.io/iptv/countries/pk.m3u")
                                val india = downloadPlaylist("https://iptv-org.github.io/iptv/countries/in.m3u")
                                M3uParser.parse(pakistan, "Pakistan") +
                                    M3uParser.parse(india, "India") + sports
                            } else {
                                val link = playlistUrl.trim()
                                require(link.startsWith("http://") || link.startsWith("https://")) {
                                    "Please enter a valid http:// or https:// playlist link."
                                }
                                M3uParser.parse(downloadPlaylist(link)) + sports
                            }

                            val unique = baseChannels.distinctBy { it.url }
                            withContext(Dispatchers.Main) {
                                channels = emptyList()
                                status = "Checking 0 / ${unique.size} streams..."
                            }
                            val gate = Semaphore(16)
                            val working = mutableListOf<TvChannel>()
                            var checked = 0
                            unique.map { channel ->
                                async(Dispatchers.IO) {
                                    gate.withPermit {
                                        val available = isStreamResponding(channel.url)
                                        withContext(Dispatchers.Main) {
                                            checked++
                                            if (available) {
                                                working.add(channel)
                                                channels = working.toList()
                                            }
                                            status = "Checking $checked / ${unique.size} streams... Found ${channels.size} responding."
                                        }
                                    }
                                }
                            }.awaitAll()
                            Pair(working.toList(), unique.size)
                        }

                        status = if (loaded.first.isEmpty()) {
                            "No working streams responded. Tap Refresh to check again."
                        } else {
                            "Showing ${loaded.first.size} responding channels out of ${loaded.second}. Refresh to check again."
                        }
                        if (playlistUrl.isNotBlank()) prefs.edit().putString("playlist_url", playlistUrl.trim()).apply()
                    } catch (e: Exception) {
                        status = "Could not load channels: ${e.localizedMessage ?: "connection error"}"
                    } finally {
                        loading = false
                    }
                }
            }

            LaunchedEffect(Unit) { refreshChannels() }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF36C98F),
                background = Color(0xFF101512),
                surface = Color(0xFF19211C)
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("SAQIB IPTV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Pakistan, India, sports channels & cricket live links", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = playlistUrl, onValueChange = { playlistUrl = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Optional: your own M3U playlist URL") },
                            placeholder = { Text("Leave empty to use built-in channels") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { refreshChannels() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (loading) "Checking streams..." else "Refresh Available Channels")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (loading) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All", "Pakistan", "India", "Sports", "Cricket Live").forEach { tab ->
                                FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab },
                                    label = { Text(tab) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (selectedTab == "Cricket Live") {
                            Text("Official cricket streaming services", fontWeight = FontWeight.SemiBold)
                            Text("Tap a service to open its website/app. Live match availability, login and subscription depend on broadcast rights.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(cricketPlatforms) { platform ->
                                    Card(modifier = Modifier.fillMaxWidth().clickable {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(platform.url)))
                                    }, shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(platform.name, fontWeight = FontWeight.SemiBold)
                                                Text(platform.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("OPEN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = searchText, onValueChange = { searchText = it },
                                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search channels") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            val visible = channels.filter {
                                (selectedTab == "All" || it.country == selectedTab) &&
                                    it.name.contains(searchText, ignoreCase = true)
                            }
                            Text("${visible.size} available channels", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            if (visible.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (loading) "Checking which streams respond..." else "No available channels found. Tap Refresh to try again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(visible) { channel ->
                                        Card(modifier = Modifier.fillMaxWidth().clickable {
                                            startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                                putExtra("channel_name", channel.name)
                                                putExtra("channel_url", channel.url)
                                            })
                                        }, shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(channel.name, fontWeight = FontWeight.SemiBold)
                                                    Text(channel.group.ifBlank { channel.country }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Text("PLAY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun downloadPlaylist(link: String): String {
    val connection = (URL(link).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20000
        readTimeout = 30000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "SaqibIPTV/1.0")
    }
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/** A successful HTTP response is only a rough availability check, not a playback guarantee. */
private fun isStreamResponding(link: String): Boolean {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(link).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 2500
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SaqibIPTV/1.0 (Android)")
            setRequestProperty("Range", "bytes=0-511")
        }
        val response = connection!!.responseCode
        if (response !in 200..299) return false
        val contentType = connection!!.contentType.orEmpty().lowercase()
        if ("text/html" in contentType) return false
        val input = connection!!.inputStream
        val sample = ByteArray(512)
        val count = input.read(sample)
        input.close()
        if (count <= 0) return false
        val prefix = String(sample, 0, count, Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
        !prefix.startsWith("<!doctype html") &&
            !prefix.startsWith("<html") &&
            !prefix.startsWith("<head") &&
            !prefix.startsWith("<body")
    } catch (_: Exception) {
        false
    } finally {
        connection?.disconnect()
    }
}
