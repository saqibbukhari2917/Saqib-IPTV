package com.saqib.iptv

data class TvChannel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String = "",
    private val countryOverride: String = ""
) {
    val country: String
        get() {
            if (countryOverride.isNotBlank()) return countryOverride
            val value = "$group $name".lowercase()
            return when {
                listOf("pakistan", "pak", "geo", "ary", "hum tv", "ptv", "express", "bol news").any { it in value } -> "Pakistan"
                listOf("india", "indian", "zee", "star", "sony", "colors", "sun tv", "ndtv", "aaj tak").any { it in value } -> "India"
                else -> "Other"
            }
        }
}

object M3uParser {
    fun parse(content: String, country: String = ""): List<TvChannel> {
        val result = mutableListOf<TvChannel>()
        var pendingName: String? = null
        var pendingGroup = ""
        var pendingLogo = ""
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingName = line.substringAfterLast(',', "").trim().ifBlank { "Unknown channel" }
                    pendingGroup = Regex("""group-title=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1).orEmpty()
                    pendingLogo = Regex("""tvg-logo=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1).orEmpty()
                }
                line.isNotEmpty() && !line.startsWith("#") && pendingName != null -> {
                    if (line.startsWith("http://", true) || line.startsWith("https://", true)) {
                        result += TvChannel(pendingName!!, line, pendingGroup, pendingLogo, country)
                    }
                    pendingName = null
                    pendingGroup = ""
                    pendingLogo = ""
                }
            }
        }
        return result.distinctBy { it.url }
    }
}
