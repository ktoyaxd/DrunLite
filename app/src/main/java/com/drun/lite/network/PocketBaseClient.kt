package com.drun.lite.network

import android.content.Context
import android.util.Log
import com.drun.lite.model.*
import com.drun.lite.util.DrunUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PocketBaseClient(context: Context) {

    private val prefs = context.getSharedPreferences("pocketbase_auth", Context.MODE_PRIVATE)
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        private set(value) {
            prefs.edit().putString("token", value).apply()
        }

    var currentUser: DrunUser?
        get() {
            val raw = prefs.getString("record", null) ?: return null
            return try {
                json.decodeFromString<DrunUser>(raw)
            } catch (_: Exception) {
                null
            }
        }
        private set(value) {
            if (value == null) prefs.edit().remove("record").apply()
            else prefs.edit().putString("record", json.encodeToString(value)).apply()
        }

    val isValid: Boolean
        get() = token.isNotBlank() && currentUser?.id?.isNotBlank() == true

    fun saveAuth(newToken: String, record: DrunUser) {
        token = newToken
        currentUser = record
    }

    fun clearAuth() {
        token = ""
        currentUser = null
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun requestBuilder(path: String): Request.Builder {
        val url = if (path.startsWith("http")) path else "${DrunUtils.BASE_URL}$path"
        val b = Request.Builder().url(url)
        val t = token
        if (t.isNotBlank()) {
            b.header("Authorization", t)
        }
        return b
    }

    private suspend fun executeRaw(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            resp.code to bodyStr
        }
    }

    // ==========================================
    // AUTH (exact port of js/auth.js)
    // ==========================================
    private var drunAuthAvailable = true

    suspend fun login(rawLoginInput: String, password: String): Result<DrunUser> {
        val rawLogin = rawLoginInput.trim()
        val isEmail = Regex("^\S+@\S+\.\S+$").matches(rawLogin)
        val noAt = rawLogin.removePrefix("@")
        val candidates = linkedSetOf<String>().apply {
            add(rawLogin)
            add(noAt)
            if (isEmail) {
                add(rawLogin)
            } else {
                add("@$noAt")
                add(noAt.lowercase())
                add("@${noAt.lowercase()}")
            }
        }.filter { it.isNotBlank() }

        val jsonMedia = "application/json; charset=utf-8".toMediaType()

        if (drunAuthAvailable) {
            for (candidate in candidates) {
                try {
                    val payload = buildJsonObject {
                        put("identity", candidate)
                        put("password", password)
                    }.toString().toRequestBody(jsonMedia)

                    val req = requestBuilder("/api/drun/auth/login").post(payload).build()
                    val (code, body) = executeRaw(req)
                    if (code in 200..299) {
                        val obj = json.parseToJsonElement(body).jsonObject
                        val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull == true
                        val tok = obj["token"]?.jsonPrimitive?.contentOrNull
                        val recEl = obj["record"]
                        if (ok && !tok.isNullOrBlank() && recEl != null) {
                            val user = json.decodeFromJsonElement<DrunUser>(recEl)
                            saveAuth(tok, user)
                            return Result.success(user)
                        }
                    } else if (code == 404) {
                        drunAuthAvailable = false
                        break
                    }
                } catch (e: Exception) {
                    Log.w("Auth", "drun login error: ${e.message}")
                }
            }
        }

        for (candidate in candidates) {
            try {
                val payload = buildJsonObject {
                    put("identity", candidate)
                    put("password", password)
                }.toString().toRequestBody(jsonMedia)

                val req = requestBuilder("/api/collections/users/auth-with-password").post(payload).build()
                val (code, body) = executeRaw(req)
                if (code in 200..299) {
                    val obj = json.parseToJsonElement(body).jsonObject
                    val tok = obj["token"]?.jsonPrimitive?.contentOrNull
                    val recEl = obj["record"]
                    if (!tok.isNullOrBlank() && recEl != null) {
                        val user = json.decodeFromJsonElement<DrunUser>(recEl)
                        saveAuth(tok, user)
                        return Result.success(user)
                    }
                } else if (code != 400) {
                    return Result.failure(Exception("HTTP $code"))
                }
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }

        return Result.failure(Exception("Неверный логин или пароль"))
    }

    suspend fun authRefresh(): DrunUser? {
        if (!isValid) return null
        return try {
            val emptyBody = "".toRequestBody("application/json".toMediaType())
            val req = requestBuilder("/api/collections/users/auth-refresh").post(emptyBody).build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) {
                val obj = json.parseToJsonElement(body).jsonObject
                val tok = obj["token"]?.jsonPrimitive?.contentOrNull ?: token
                val recEl = obj["record"] ?: return currentUser
                val user = json.decodeFromJsonElement<DrunUser>(recEl)
                saveAuth(tok, user)
                user
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // USERS & PROFILE (utils.js & profile.js)
    // ==========================================
    private var byIdsAvailable = true

    suspend fun fetchUsersByIds(ids: List<String>): List<DrunUser> {
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<DrunUser>()
        val jsonMedia = "application/json; charset=utf-8".toMediaType()

        if (byIdsAvailable) {
            try {
                for (chunk in ids.chunked(25)) {
                    val payload = buildJsonObject {
                        putJsonArray("ids") { chunk.forEach { add(it) } }
                    }.toString().toRequestBody(jsonMedia)
                    val req = requestBuilder("/api/drun/users/by-ids").post(payload).build()
                    val (code, body) = executeRaw(req)
                    if (code in 200..299) {
                        val obj = json.parseToJsonElement(body).jsonObject
                        val usersEl = obj["users"]
                        when (usersEl) {
                            is JsonArray -> usersEl.forEach { el ->
                                val u = json.decodeFromJsonElement<DrunUser>(el)
                                if (u.id.isNotBlank()) out.add(u)
                            }
                            is JsonObject -> usersEl.values.forEach { el ->
                                val u = json.decodeFromJsonElement<DrunUser>(el)
                                if (u.id.isNotBlank()) out.add(u)
                            }
                            else -> Unit
                        }
                    } else if (code == 404 || code == 403) {
                        byIdsAvailable = false
                        break
                    }
                }
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {
            }
        }

        val missing = ids.filter { id -> out.none { it.id == id } }
        for (chunk in missing.chunked(4)) {
            val filter = chunk.joinToString(" || ") { "id="$it"" }
            try {
                val req = requestBuilder(
                    "/api/collections/users/records?page=1&perPage=${chunk.size}&skipTotal=true&filter=${enc(filter)}"
                ).get().build()
                val (code, body) = executeRaw(req)
                if (code in 200..299) {
                    val obj = json.parseToJsonElement(body).jsonObject
                    val items = obj["items"]?.jsonArray ?: JsonArray(emptyList())
                    items.forEach { out.add(json.decodeFromJsonElement<DrunUser>(it)) }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    suspend fun getUserOne(userId: String): DrunUser? {
        return try {
            val req = requestBuilder("/api/collections/users/records/${enc(userId)}").get().build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) json.decodeFromString<DrunUser>(body) else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getKvItem(key: String): JsonElement? {
        return try {
            val filter = "key = "${key.replace(""", "\"")}""
            val req = requestBuilder("/api/collections/kv_store/records?page=1&perPage=1&filter=${enc(filter)}").get().build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) {
                val items = json.parseToJsonElement(body).jsonObject["items"]?.jsonArray
                val first = items?.firstOrNull()?.jsonObject ?: return null
                val valEl = first["value"] ?: return null
                if (valEl is JsonPrimitive && valEl.isString) {
                    json.parseToJsonElement(valEl.content)
                } else valEl
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // CHANNELS, FRIENDS, MESSAGES (sidebar.js & chat.js)
    // ==========================================
    suspend fun getFullChannels(): List<DrunChannel> {
        return try {
            val req = requestBuilder("/api/collections/channels/records?page=1&perPage=200").get().build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) {
                val items = json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return emptyList()
                items.map { json.decodeFromJsonElement<DrunChannel>(it) }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getChannelOne(channelId: String): DrunChannel? {
        return try {
            val req = requestBuilder("/api/collections/channels/records/${enc(channelId)}").get().build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) json.decodeFromString<DrunChannel>(body) else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getAcceptedFriendRequests(myId: String): List<FriendRequest> {
        return try {
            val filter = "status = "accepted" && (from = "$myId" || to = "$myId")"
            val req = requestBuilder("/api/collections/friendRequests/records?page=1&perPage=200&filter=${enc(filter)}").get().build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) {
                val items = json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return emptyList()
                items.map { json.decodeFromJsonElement<FriendRequest>(it) }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getMessages(channelId: String, page: Int = 1, perPage: Int = 50, beforeDate: String? = null): List<DrunMessage> {
        return try {
            val filter = if (beforeDate != null) {
                "channelId = "$channelId" && created < "$beforeDate""
            } else {
                "channelId = "$channelId""
            }
            val url = "/api/collections/messages/records?page=$page&perPage=$perPage&sort=${enc("-created")}&filter=${enc(filter)}"
            val req = requestBuilder(url).get().build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) {
                val items = json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return emptyList()
                items.map { json.decodeFromJsonElement<DrunMessage>(it) }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun sendTextMessage(
        channelId: String,
        senderId: String,
        content: String,
        replyTo: ReplyPayload? = null
    ): Result<DrunMessage> {
        return try {
            val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val obj = buildJsonObject {
                put("content", content)
                put("senderId", senderId)
                put("channelId", channelId)
                put("type", "text")
                put("timestamp", isoNow)
                put("edited", false)
                put("status", "sent")
                if (replyTo != null) {
                    put("replyTo", json.encodeToString(replyTo))
                }
            }
            val req = requestBuilder("/api/collections/messages/records")
                .post(obj.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) {
                Result.success(json.decodeFromString<DrunMessage>(body))
            } else {
                Result.failure(Exception("Ошибка отправки ($code): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendImageMessage(
        channelId: String,
        senderId: String,
        imageBytes: ByteArray,
        fileName: String = "photo.jpg",
        caption: String = "",
        replyTo: ReplyPayload? = null
    ): Result<DrunMessage> {
        return try {
            val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("image", fileName, imageBytes.toRequestBody("image/jpeg".toMediaType()))
                .addFormDataPart("senderId", senderId)
                .addFormDataPart("channelId", channelId)
                .addFormDataPart("type", "image")
                .addFormDataPart("content", caption)
                .addFormDataPart("timestamp", isoNow)

            if (replyTo != null) {
                mp.addFormDataPart("replyTo", json.encodeToString(replyTo))
            }

            val req = requestBuilder("/api/collections/messages/records")
                .post(mp.build())
                .build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) {
                Result.success(json.decodeFromString<DrunMessage>(body))
            } else {
                Result.failure(Exception("Ошибка отправки фото ($code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // STICKERS (js/stickers.js)
    // ==========================================
    suspend fun getStickerOne(stickerId: String): StickerInfo? {
        return try {
            val req = requestBuilder("/api/collections/mellstickers/records/${enc(stickerId)}").get().build()
            val (code, body) = executeRaw(req)
            if (code in 200..299) {
                val obj = json.parseToJsonElement(body).jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
                val colId = obj["collectionId"]?.jsonPrimitive?.contentOrNull ?: "mellstickers"
                val contentFile = obj["content"]?.jsonPrimitive?.contentOrNull ?: return null
                val url = DrunUtils.fileUrl("mellstickers", id, colId, contentFile)
                var sName = ""
                try {
                    val dataEl = obj["data"]
                    val metaObj = when (dataEl) {
                        is JsonPrimitive -> json.parseToJsonElement(dataEl.content).jsonObject
                        is JsonObject -> dataEl
                        else -> null
                    }
                    sName = metaObj?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                } catch (_: Exception) {
                }
                StickerInfo(id = id, url = url, name = sName)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // POCKETBASE SSE REALTIME (/api/realtime)
    // ==========================================
    fun subscribeToMessages(
        channelIdFilter: String? = null,
        onMessageEvent: (action: String, message: DrunMessage) -> Unit
    ): EventSource {
        val topic = if (channelIdFilter != null) {
            "messages/*?options=${enc("{"filter":"channelId = \"$channelIdFilter\""}")}"
        } else {
            "messages/*"
        }

        val req = Request.Builder()
            .url("${DrunUtils.BASE_URL}/api/realtime")
            .header("Accept", "text/event-stream")
            .build()

        val factory = EventSources.createFactory(sseClient)
        return factory.newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (type == "PB_CONNECT") {
                        val connObj = json.parseToJsonElement(data).jsonObject
                        val clientId = connObj["clientId"]?.jsonPrimitive?.contentOrNull ?: return
                        val subPayload = buildJsonObject {
                            put("clientId", clientId)
                            putJsonArray("subscriptions") { add(topic) }
                        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                        val subReq = requestBuilder("/api/realtime").post(subPayload).build()
                        httpClient.newCall(subReq).execute().close()
                    } else if (type != null && type.startsWith("messages")) {
                        val evObj = json.parseToJsonElement(data).jsonObject
                        val action = evObj["action"]?.jsonPrimitive?.contentOrNull ?: return
                        val recEl = evObj["record"] ?: return
                        val msg = json.decodeFromJsonElement<DrunMessage>(recEl)
                        onMessageEvent(action, msg)
                    }
                } catch (e: Exception) {
                    Log.w("Realtime", "SSE parse error: ${e.message}")
                }
            }
        })
    }
}
