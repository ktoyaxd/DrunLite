package com.drun.lite.util

import androidx.compose.ui.graphics.Color
import com.drun.lite.model.DrunMessage
import com.drun.lite.model.DrunUser
import com.drun.lite.model.ReplyPayload
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

object DrunUtils {
    const val BASE_URL = "https://api.drun.dev"

    // AVATAR_COLORS from utils.js
    val AvatarColors = listOf(
        Color(0xFFE17076),
        Color(0xFF7BC862),
        Color(0xFFE5CA77),
        Color(0xFF65AADD),
        Color(0xFFA695E7),
        Color(0xFFEE7AAE),
        Color(0xFF6EC9CB),
        Color(0xFFFAA774)
    )

    fun avatarColorFor(name: String): Color {
        val safe = name.ifEmpty { "?" }
        return AvatarColors[safe.length % AvatarColors.size]
    }

    fun senderNameColor(name: String): Color {
        val hue = ((name.length * 45) % 360).toFloat()
        return Color.hsl(hue, 0.70f, 0.70f)
    }

    fun settingsAvatarColor(name: String): Color {
        val hue = ((name.length * 45) % 360).toFloat()
        return Color.hsl(hue, 0.60f, 0.50f)
    }

    fun dmKey(a: String, b: String): String = listOf(a, b).sorted().joinToString(":")

    private val emptyNameRegex = Regex(
        "^(ID\s+[a-z0-9]{4,15}|Пользователь(\s+[a-z0-9]{4,15})?|Контакт(\s+[a-z0-9]{4,15})?|Собеседник)$",
        RegexOption.IGNORE_CASE
    )

    fun isEmptyName(name: String?): Boolean {
        if (name == null) return true
        val t = name.trim()
        return t.isEmpty() || emptyNameRegex.matches(t)
    }

    fun extractFirstString(el: JsonElement?): String? {
        if (el == null || el is JsonNull) return null
        return when (el) {
            is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonArray -> el.firstOrNull()?.let { extractFirstString(it) }
            else -> null
        }
    }

    fun fileUrl(collection: String, recordId: String, collectionIdOrName: String?, filename: String?): String {
        if (recordId.isBlank() || filename.isNullOrBlank()) return ""
        if (filename.startsWith("http://") || filename.startsWith("https://")) return filename
        val col = collectionIdOrName?.takeIf { it.isNotBlank() } ?: collection
        val encCol = URLEncoder.encode(col, "UTF-8").replace("+", "%20")
        val encId = URLEncoder.encode(recordId, "UTF-8").replace("+", "%20")
        val encFile = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        return "$BASE_URL/api/files/$encCol/$encId/$encFile"
    }

    fun getUserAvatarUrl(user: DrunUser?): String? {
        if (user == null) return null
        val filename = extractFirstString(user.avatar)
            ?: extractFirstString(user.image)
            ?: extractFirstString(user.ProfileImg)
            ?: return null
        return fileUrl("users", user.id, user.collectionId.ifBlank { "users" }, filename)
    }

    fun getMessageImageUrl(msg: DrunMessage): String? {
        if (msg.image.isBlank()) return null
        return fileUrl("messages", msg.id, msg.collectionId.ifBlank { "messages" }, msg.image)
    }

    fun parseReplyTo(el: JsonElement?, json: Json): ReplyPayload? {
        if (el == null || el is JsonNull) return null
        return try {
            when (el) {
                is JsonPrimitive -> {
                    val str = el.contentOrNull ?: return null
                    if (str.isBlank()) null else json.decodeFromString<ReplyPayload>(str)
                }
                is JsonObject -> json.decodeFromJsonElement<ReplyPayload>(el)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseAdmins(el: JsonElement?, json: Json): List<String> {
        if (el == null || el is JsonNull) return emptyList()
        return try {
            when (el) {
                is JsonArray -> el.mapNotNull { it.jsonPrimitive.contentOrNull }
                is JsonPrimitive -> {
                    val s = el.contentOrNull ?: return emptyList()
                    json.decodeFromString<List<String>>(s)
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun formatTime(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val parsed = parseIsoDate(dateStr) ?: return ""
        val nowCal = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = parsed }
        val sameDay = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed)
        } else {
            SimpleDateFormat("d MMM", Locale("ru")).format(parsed)
        }
    }

    private fun parseIsoDate(raw: String): Date? {
        val normalized = raw.replace(" ", "T")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                if (p.endsWith("'Z'") || !p.contains("X")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(normalized)
                if (d != null) return d
            } catch (_: Exception) {
            }
        }
        return null
    }
}
