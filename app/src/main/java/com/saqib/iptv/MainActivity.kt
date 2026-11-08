package com.saqib.iptv

import android.content.Intent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("iptv_settings", MODE_PRIVATE)
        setContent {
            var playlistUrl by remember { mutableStateOf(prefs.getString("playlist_url", "") ?: "") }
            var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
            var selectedTab by remember { mutableStateOf("All") }
            var searchText by remember { mutableStateOf("") }
            var status by remember { mutableStateOf("Paste your M3U playlist link to load channels.") }
            var loading by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF36C98F),
                background = Color(0xFF101512),
                surface = Color(0xFF19211C)
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("SAQIB IPTV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Live TV • Your playlist, your channels", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(
                            value = playlistUrl, onValueChange = { playlistUrl = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text("M3U playlist URL") },
                            placeholder = { Text("https://example.com/playlist.m3u") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val link = playlistUrl.trim()
                            if (!link.startsWith("http://") && !link.startsWith("https://")) {
                                status = "Please enter a valid http:// or https:// playlist link."
                            } else {
                                loading = true
                                status = "Loading playlist..."
                                prefs.edit().putString("playlist_url", link).apply()
                                scope.launch {
                                    try {
                                        val body = withContext(Dispatchers.IO) {
                                            val connection = (URL(link).openConnection() as HttpURLConnection).apply {
                                                connectTimeout = 15000
                                                readTimeout = 20000
                                                requestMethod = "GET"
                                                setRequestProperty("User-Agent", "SaqibIPTV/1.0")
                                            }
                                            try { connection.inputStream.bufferedReader().use { it.readText() } }
                                            finally { connection.disconnect() }
                                        }
                                        val parsed = M3uParser.parse(body)
                                        channels = parsed
                                        status = if (parsed.isEmpty()) "No channels found. Check that this is a valid M3U playlist." else "Loaded ${parsed.size} channels successfully."
                                    } catch (e: Exception) {
                                        status = "Could not load playlist: ${e.localizedMessage ?: "connection error"}"
                                    } finally { loading = false }
                                }
                            }
                        }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (loading) "Loading..." else "Load / Refresh Channels")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = searchText, onValueChange = { searchText = it },
                            modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search channels") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All", "Pakistan", "India", "Other").forEach { tab ->
                                FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab },
                                    label = { Text(when (tab) { "Pakistan" -> "🇵🇰 Pakistan"; "India" -> "🇮🇳 India"; else -> tab }) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val visible = channels.filter {
                            (selectedTab == "All" || it.country == selectedTab) && it.name.contains(searchText, ignoreCase = true)
                        }
                        Text("${visible.size} channels", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        if (visible.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (channels.isEmpty()) "Your channels will appear here after loading a playlist." else "No channels in this category.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
