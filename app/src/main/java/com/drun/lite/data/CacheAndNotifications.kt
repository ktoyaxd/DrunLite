package com.drun.lite.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drun.lite.R
import com.drun.lite.model.DrunMessage
import com.drun.lite.model.DrunUser
import com.drun.lite.model.SavedAccount
import com.drun.lite.util.DrunUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

data class CacheCategoryStat(
    val key: String,
    val name: String,
    val colorHex: Long,
    val folderName: String,
    val sizeBytes: Long
)

class DrunLocalStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("drunlite_local_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val baseCacheDir: File
        get() = File(context.cacheDir, "Drun").apply { mkdirs() }

    // Categories from js/cache.js
    private val categoriesMeta = listOf(
        Triple("sticker", "Drun Sticker", 0xFFF59E0BL to "Stickers"),
        Triple("image", "Drun Image", 0xFF3B82F6L to "Images"),
        Triple("photo", "Drun Photo", 0xFFEC4899L to "Photos"),
        Triple("video", "Drun Video", 0xFF10B981L to "Videos"),
        Triple("doc", "Drun Documents", 0xFF8B5CF6L to "Documents"),
        Triple("musor", "Drun Musor", 0xFF6B7280L to "Other")
    )

    fun getCacheStats(): List<CacheCategoryStat> {
        return categoriesMeta.map { (key, title, colorAndFolder) ->
            val (colorHex, folder) = colorAndFolder
            val dir = File(baseCacheDir, folder).apply { mkdirs() }
            val total = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            CacheCategoryStat(
                key = key,
                name = title,
                colorHex = colorHex,
                folderName = folder,
                sizeBytes = total
            )
        }
    }

    fun clearAllCache() {
        categoriesMeta.forEach { (_, _, colorAndFolder) ->
            val dir = File(baseCacheDir, colorAndFolder.second)
            if (dir.exists()) {
                dir.deleteRecursively()
                dir.mkdirs()
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val k = 1024.0
        val sizes = arrayOf("B", "KB", "MB", "GB")
        val i = floor(ln(bytes.toDouble()) / ln(k)).toInt().coerceIn(0, sizes.lastIndex)
        val value = bytes / k.pow(i.toDouble())
        return String.format(Locale.US, "%.1f %s", value, sizes[i])
    }

    // Messages cache (saveMessagesToCache / loadMessagesFromCache in js/cache.js)
    fun saveMessagesToCache(channelId: String, messages: List<DrunMessage>) {
        try {
            val dir = File(baseCacheDir, "Other").apply { mkdirs() }
            val safeId = channelId.replace(Regex("[^a-zA-Z0-9]"), "_")
            val file = File(dir, "messages_$safeId.json")
            file.writeText(json.encodeToString(messages))
        } catch (_: Exception) {
        }
    }

    fun loadMessagesFromCache(channelId: String): List<DrunMessage> {
        return try {
            val dir = File(baseCacheDir, "Other")
            val safeId = channelId.replace(Regex("[^a-zA-Z0-9]"), "_")
            val file = File(dir, "messages_$safeId.json")
            if (!file.exists()) return emptyList()
            json.decodeFromString<List<DrunMessage>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Profile cache (js/profile.js)
    fun getProfileCache(): MutableMap<String, DrunUser> {
        val raw = prefs.getString("drunlite-profile-cache-v1", null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, DrunUser>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun mergeProfileToCache(user: DrunUser) {
        if (user.id.isBlank()) return
        val cache = getProfileCache()
        val prev = cache[user.id]
        val mergedName = if (!DrunUtils.isEmptyName(user.name)) user.name
        else prev?.name?.takeIf { !DrunUtils.isEmptyName(it) } ?: user.username

        cache[user.id] = user.copy(
            name = mergedName,
            username = user.username.ifBlank { prev?.username ?: "" }
        )
        prefs.edit().putString("drunlite-profile-cache-v1", json.encodeToString(cache)).apply()
    }

    // Saved accounts (js/auth.js & js/settings.js)
    fun saveAccountToStorage(user: DrunUser) {
        val list = getSavedAccounts().filter { it.id != user.id }.toMutableList()
        val avatarStr = DrunUtils.extractFirstString(user.avatar) ?: DrunUtils.extractFirstString(user.ProfileImg)
        list.add(
            SavedAccount(
                id = user.id,
                name = user.name.ifBlank { user.username },
                username = user.username,
                avatar = avatarStr
            )
        )
        prefs.edit().putString("drun_accounts", json.encodeToString(list)).apply()
    }

    fun getSavedAccounts(): List<SavedAccount> {
        val raw = prefs.getString("drun_accounts", null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearSavedAccounts() {
        prefs.edit().remove("drun_accounts").apply()
    }
}

// Exact port of js/notifications.js (channel 'drun_channel', sound R.raw.test, icon R.drawable.dl48x, color #229AF0)
class DrunNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "drun_channel"
    }

    fun showTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Toast.makeText(context, "Разрешение на уведомления не выдано", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.test}")
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "DrunLite",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления DrunLite"
                setSound(soundUri, audioAttrs)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.dl48x)
            .setColor(0xFF229AF0.toInt())
            .setContentTitle("DrunLite")
            .setContentText("Test")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1, notification)
    }
}
