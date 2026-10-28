package com.drtube.data.mapper

import com.drtube.data.api.PipedSearchItem
import com.drtube.data.api.PipedVideoResponse
import com.drtube.data.api.YoutubeSnippet
import com.drtube.data.api.YoutubeVideoItem
import com.drtube.data.model.Video
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val ID_LOCALE = Locale("id", "ID")

private val ISO_DURATION = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")

private val HTML_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);|&(amp|lt|gt|quot|apos|nbsp);")

private val ISO_INSTANT_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
)

fun YoutubeVideoItem.toVideo(): Video? {
    val videoId = id?.takeIf { it.isNotBlank() } ?: return null
    val snippet = snippet ?: return null
    return Video(
        id = videoId,
        title = decodeHtmlEntities(snippet.title.orEmpty()),
        description = decodeHtmlEntities(snippet.description.orEmpty()),
        thumbnailUrl = snippet.thumbnailUrl(),
        streamUrl = "",
        channelTitle = decodeHtmlEntities(snippet.channelTitle.orEmpty()),
        viewsCount = formatViewCount(statistics?.viewCount?.toLongOrNull()),
        publishedAt = formatRelativeInstant(snippet.publishedAt),
        duration = formatIsoDuration(contentDetails?.duration),
        channelId = snippet.channelId.orEmpty()
    )
}

fun PipedVideoResponse.toVideo(): Video = Video(
    id = videoId,
    title = decodeHtmlEntities(title),
    description = "",
    thumbnailUrl = thumbnail.orEmpty(),
    streamUrl = "",
    channelTitle = decodeHtmlEntities(uploaderName.orEmpty()),
    viewsCount = formatViewCount(views),
    publishedAt = formatRelativeEpochSeconds(uploaded),
    duration = formatDurationSeconds(duration),
    channelId = uploaderUrl?.substringAfterLast('/').orEmpty(),
    channelAvatarUrl = uploaderAvatar.orEmpty()
)

fun PipedSearchItem.toVideo(): Video = Video(
    id = videoId,
    title = decodeHtmlEntities(title),
    description = "",
    thumbnailUrl = thumbnail.orEmpty(),
    streamUrl = "",
    channelTitle = decodeHtmlEntities(uploaderName.orEmpty()),
    viewsCount = formatViewCount(views),
    publishedAt = formatRelativeEpochSeconds(uploaded),
    duration = formatDurationSeconds(duration),
    channelId = uploaderUrl?.substringAfterLast('/').orEmpty(),
    channelAvatarUrl = uploaderAvatar.orEmpty()
)

private fun decodeHtmlEntities(input: String): String {
    if (!input.contains('&')) return input
    return HTML_ENTITY.replace(input) { match ->
        val hex = match.groupValues[1]
        val number = match.groupValues[2]
        val name = match.groupValues[3]
        if (number.isNotEmpty()) {
            val codePoint = if (hex == "x") number.toIntOrNull(16) else number.toIntOrNull()
            if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                String(Character.toChars(codePoint))
            } else {
                match.value
            }
        } else {
            when (name) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> match.value
            }
        }
    }
}

private fun YoutubeSnippet.thumbnailUrl(): String {
    val thumbs = thumbnails ?: return ""
    return thumbs.high?.url ?: thumbs.medium?.url ?: thumbs.default?.url ?: ""
}

fun formatViewCount(count: Long?): String {
    if (count == null || count < 0) return ""
    return when {
        count >= 1_000_000_000 -> formatCompact(count, 1_000_000_000.0) + " M tayangan"
        count >= 1_000_000 -> formatCompact(count, 1_000_000.0) + " jt tayangan"
        count >= 1_000 -> formatCompact(count, 1_000.0) + " rb tayangan"
        else -> "$count tayangan"
    }
}

private fun formatCompact(count: Long, unit: Double): String {
    val value = String.format(ID_LOCALE, "%.1f", count / unit)
    return value.removeSuffix(",0")
}

fun formatIsoDuration(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "P0D") return ""
    val match = ISO_DURATION.find(raw) ?: return ""
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toIntOrNull() ?: 0
    val seconds = match.groupValues[3].toIntOrNull() ?: 0
    if (hours == 0 && minutes == 0 && seconds == 0) return ""
    return buildDuration(hours, minutes, seconds)
}

fun formatDurationSeconds(totalSeconds: Long?): String {
    if (totalSeconds == null || totalSeconds <= 0) return ""
    val hours = (totalSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return buildDuration(hours, minutes, seconds)
}

private fun buildDuration(hours: Int, minutes: Int, seconds: Int): String {
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatRelativeInstant(isoValue: String?): String {
    val millis = parseIsoInstant(isoValue) ?: return ""
    return formatRelativeMillis(millis)
}

fun formatRelativeEpochSeconds(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return ""
    val millis = if (epochSeconds > 10_000_000_000L) epochSeconds else epochSeconds * 1000L
    return formatRelativeMillis(millis)
}

private fun formatRelativeMillis(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    if (diff < 60_000L) return "baru saja"
    val minutes = diff / 60_000L
    if (minutes < 60) return "$minutes menit lalu"
    val hours = minutes / 60
    if (hours < 24) return "$hours jam lalu"
    val days = hours / 24
    if (days < 7) return "$days hari lalu"
    val weeks = days / 7
    if (weeks < 5) return "$weeks minggu lalu"
    val months = days / 30
    if (months < 12) return "$months bulan lalu"
    return "${days / 365} tahun lalu"
}

private fun parseIsoInstant(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    for (pattern in ISO_INSTANT_PATTERNS) {
        val parser = SimpleDateFormat(pattern, Locale.US).apply {
            if (pattern.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val position = ParsePosition(0)
        val date = parser.parse(value, position) ?: continue
        if (position.index == value.length) return date.time
    }
    return null
}
