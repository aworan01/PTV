package com.personal.tv.data.parser

import com.personal.tv.data.model.Channel
import com.personal.tv.data.model.DrmType
import java.util.UUID

object M3UParser {

    fun parse(content: String): Pair<List<Channel>, String> {
        val lines = content.lines()
        val channels = mutableListOf<Channel>()
        var epgUrl = ""

        val headerLine = lines.firstOrNull { it.startsWith("#EXTM3U") } ?: ""
        epgUrl = extractAttribute(headerLine, "url-tvg")
            .ifEmpty { extractAttribute(headerLine, "x-tvg-url") }

        var i = 0
        var sortOrder = 0

        // Pending state — resets after each channel entry
        var pendingDrmType    = DrmType.NONE
        var pendingLicenseKey = ""   // raw value from license_key= (could be base64, hex, or URL)
        var pendingManifest   = ""   // "dash", "hls", etc.
        var pendingUserAgent  = ""
        var pendingReferrer   = ""
        var pendingInputstream = "" // e.g. "inputstream.adaptive"

        while (i < lines.size) {
            val line = lines[i].trim()

            try {
                when {
                    // ── KODIPROP lines ──────────────────────────────────────────
                    line.startsWith("#KODIPROP:") -> {
                        val kv = line.removePrefix("#KODIPROP:").trim()
                        val key = kv.substringBefore("=").trim()
                        val value = kv.substringAfter("=").trim()

                        when (key) {
                            "inputstreamaddon",
                            "inputstream"                          -> pendingInputstream = value
                            "inputstream.adaptive.manifest_type"  -> pendingManifest = value.lowercase()
                            "inputstream.adaptive.license_type"   -> {
                                pendingDrmType = when (value.trim().lowercase()) {
                                    "com.widevine.alpha"      -> DrmType.WIDEVINE
                                    "com.microsoft.playready" -> DrmType.PLAYREADY
                                    "clearkey"                -> DrmType.CLEARKEY
                                    else                      -> DrmType.NONE
                                }
                            }
                            "inputstream.adaptive.license_key"    -> pendingLicenseKey = value
                        }
                    }

                    // ── VLC options ─────────────────────────────────────────────
                    line.startsWith("#EXTVLCOPT:http-user-agent=") ->
                        pendingUserAgent = line.substringAfter("=").trim()
                    line.startsWith("#EXTVLCOPT:http-referrer=") ->
                        pendingReferrer = line.substringAfter("=").trim()
                    line.startsWith("#EXTVLCOPT:http-origin=") ->
                        pendingReferrer = line.substringAfter("=").trim()

                    // ── EXTINF — main channel line ───────────────────────────────
                    line.startsWith("#EXTINF:") -> {
                        val attrs = parseExtInf(line)
                        val rawName = attrs["name"]?.trim() ?: ""

                        if (rawName.isNotEmpty()) {
                            val url = findNextUrl(lines, i + 1)

                            if (url.isNotEmpty()) {
                                val chno = attrs["tvg-chno"]?.takeIf { it.isNotEmpty() }
                                    ?: extractChannelNumber(rawName)

                                val manifestType = pendingManifest.ifEmpty {
                                    when {
                                        url.contains(".mpd", ignoreCase = true)  -> "dash"
                                        url.contains(".m3u8", ignoreCase = true) -> "hls"
                                        url.startsWith("rtsp://", ignoreCase = true) -> "rtsp"
                                        pendingInputstream.contains("adaptive")  -> "dash"
                                        else -> ""
                                    }
                                }

                                val drmType = when {
                                    pendingDrmType != DrmType.NONE -> pendingDrmType
                                    attrs["drm-type"]?.contains("widevine", ignoreCase = true) == true -> DrmType.WIDEVINE
                                    attrs["drm-type"]?.contains("playready", ignoreCase = true) == true -> DrmType.PLAYREADY
                                    else -> DrmType.NONE
                                }

                                channels.add(
                                    Channel(
                                        id           = UUID.randomUUID().toString(),
                                        name         = rawName,
                                        url          = url,
                                        groupTitle   = attrs["group-title"] ?: "",
                                        logoUrl      = attrs["tvg-logo"] ?: "",
                                        tvgId        = attrs["tvg-id"] ?: "",
                                        tvgName      = attrs["tvg-name"] ?: rawName,
                                        tvgChno      = chno,
                                        drmType      = drmType,
                                        drmLicenseUrl = normaliseLicenseKey(pendingLicenseKey, drmType),
                                        drmKeyId     = "",
                                        manifestType = manifestType,
                                        userAgent    = pendingUserAgent,
                                        referrer     = pendingReferrer,
                                        sortOrder    = sortOrder++
                                    )
                                )
                            }
                        }

                        // Reset all pending state regardless of success
                        pendingDrmType    = DrmType.NONE
                        pendingLicenseKey = ""
                        pendingManifest   = ""
                        pendingUserAgent  = ""
                        pendingReferrer   = ""
                        pendingInputstream = ""
                    }
                }
            } catch (e: Exception) {
                // Skip malformed line/channel, never abort the whole parse
                pendingDrmType    = DrmType.NONE
                pendingLicenseKey = ""
                pendingManifest   = ""
                pendingUserAgent  = ""
                pendingReferrer   = ""
                pendingInputstream = ""
            }
            i++
        }

        return Pair(channels, epgUrl)
    }

    /**
     * Normalises the raw license_key value from KODIPROP into something ExoPlayer can use.
     *
     * ClearKey formats we handle:
     *   1. URL  — starts with http/https  → pass through as license server URL
     *   2. hex  — "kid1:key1,kid2:key2"   → build ClearKey JSON inline
     *   3. base64 — everything else        → treat as base64-encoded JSON or pass as-is
     *
     * Widevine / PlayReady: the value is always a license server URL.
     */
    private fun normaliseLicenseKey(raw: String, drmType: DrmType): String {
        if (raw.isEmpty()) return ""

        return when (drmType) {
            DrmType.CLEARKEY -> {
                when {
                    // Already a URL
                    raw.startsWith("http://", ignoreCase = true) ||
                    raw.startsWith("https://", ignoreCase = true) -> raw

                    // Hex kid:key pairs  e.g. "aabbcc...:ddeeff...,...""
                    raw.contains(":") && raw.matches(Regex("[0-9a-fA-F:, ]+")) -> {
                        buildClearKeyJson(raw)
                    }

                    // Base64 — could be raw key bytes or pre-formed JSON; pass through
                    else -> raw
                }
            }
            // Widevine / PlayReady — value is a license server URL
            else -> raw
        }
    }

    /**
     * Builds a ClearKey JSON licence from hex kid:key pairs.
     * Input:  "kid1hex:key1hex,kid2hex:key2hex"
     * Output: {"keys":[{"kty":"oct","k":"<base64url-key>","kid":"<base64url-kid>"}],"type":"temporary"}
     */
    private fun buildClearKeyJson(hexPairs: String): String {
        val keys = hexPairs.split(",").mapNotNull { pair ->
            val parts = pair.trim().split(":")
            if (parts.size != 2) return@mapNotNull null
            val kid = hexToBase64Url(parts[0].trim()) ?: return@mapNotNull null
            val key = hexToBase64Url(parts[1].trim()) ?: return@mapNotNull null
            """{"kty":"oct","k":"$key","kid":"$kid"}"""
        }
        if (keys.isEmpty()) return hexPairs
        return """{"keys":[${keys.joinToString(",")}],"type":"temporary"}"""
    }

    private fun hexToBase64Url(hex: String): String? {
        return try {
            val bytes = ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    private fun parseExtInf(line: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // Channel name is everything after the last comma
        val commaIdx = line.lastIndexOf(',')
        if (commaIdx >= 0) result["name"] = line.substring(commaIdx + 1).trim()
        // Key="value" attributes — non-greedy match, tolerant of empty values
        Regex("""([\w\-]+)="([^"]*)"""").findAll(line).forEach { m ->
            result[m.groupValues[1].lowercase()] = m.groupValues[2].trim()
        }
        // Also handle unquoted tvg-chno=NNN
        Regex("""tvg-chno=(\d+)""").find(line)?.let {
            result["tvg-chno"] = it.groupValues[1]
        }
        return result
    }

    /** Looks up to 10 lines ahead for the stream URL, skipping # lines */
    private fun findNextUrl(lines: List<String>, startIdx: Int): String {
        for (i in startIdx until minOf(startIdx + 10, lines.size)) {
            val l = lines[i].trim()
            if (l.isNotEmpty() && !l.startsWith("#")) return l
        }
        return ""
    }

    /** Extracts leading channel number from names like "2964. UK: 5 USA HD" or "361." */
    private fun extractChannelNumber(name: String): String {
        return Regex("""^(\d+)[.\s]""").find(name.trim())?.groupValues?.get(1) ?: ""
    }

    private fun extractAttribute(line: String, key: String): String =
        Regex("""$key="([^"]*?)"""").find(line)?.groupValues?.get(1) ?: ""

    private fun resetPending() { /* no-op, used for readability */ }
}
