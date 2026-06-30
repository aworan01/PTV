package com.personal.tv.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val groupTitle: String = "",
    val logoUrl: String = "",
    val tvgId: String = "",
    val tvgName: String = "",
    val tvgChno: String = "",           // channel number e.g. "2964" from tvg-chno or name prefix
    val drmType: DrmType = DrmType.NONE,
    val drmLicenseUrl: String = "",
    val drmKeyId: String = "",
    val manifestType: String = "",      // "dash", "hls", "rtsp" — from KODIPROP manifest_type
    val userAgent: String = "",
    val referrer: String = "",
    val isHidden: Boolean = false,
    val isFavourite: Boolean = false,
    val sortOrder: Int = 0
) : Parcelable

@Parcelize
data class Programme(
    val channelId: String,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val category: String = "",
    val icon: String = "",
    val rating: String = ""
) : Parcelable {
    val durationMs: Long get() = endTime - startTime
    val progressFraction: Float get() {
        val now = System.currentTimeMillis()
        if (now < startTime) return 0f
        if (now > endTime) return 1f
        return (now - startTime).toFloat() / durationMs
    }
    val minutesLeft: Int get() {
        val now = System.currentTimeMillis()
        return maxOf(0, ((endTime - now) / 60000).toInt())
    }
    val isLive: Boolean get() {
        val now = System.currentTimeMillis()
        return now in startTime..endTime
    }
}

data class ChannelGroup(
    val name: String,
    val channels: List<Channel>,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val sortOrder: Int = 0
)

data class Playlist(
    val id: String,
    val name: String,
    val url: String,
    val epgUrl: String = "",
    val lastUpdated: Long = 0L,
    val isActive: Boolean = true
)

enum class DrmType { NONE, WIDEVINE, PLAYREADY, CLEARKEY }

data class StreamInfo(
    val resolution: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val hdrType: String = "SDR",
    val bitrate: Long = 0L,
    val videoDecoder: String = "",   // "HW" or "SW"
    val audioDecoder: String = "",   // "HW" or "SW"
    val audioChannels: String = ""   // e.g. "stereo", "5.1"
) {
    val resolutionLabel: String get() = when {
        height >= 2160 -> "4K"
        height >= 1080 -> "FHD"
        height >= 720  -> "HD"
        height > 0     -> "SD"
        resolution.contains("3840") || resolution.contains("2160") -> "4K"
        resolution.contains("1920") || resolution.contains("1080") -> "FHD"
        resolution.contains("1280") || resolution.contains("720")  -> "HD"
        resolution.isNotEmpty() -> "SD"
        else -> ""
    }
    val fpsLabel: String get() = if (fps > 0f) "${fps.toInt()}fps" else ""
    val videoDecoderLabel: String get() = if (videoDecoder.isNotEmpty()) "V:$videoDecoder" else ""
    val audioDecoderLabel: String get() = if (audioDecoder.isNotEmpty()) "A:$audioDecoder" else ""
}

data class NavItem(val route: String, val label: String, val iconRes: Int)
