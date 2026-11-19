package com.drun.lite

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drun.lite.data.CacheCategoryStat
import com.drun.lite.data.DrunLocalStorage
import com.drun.lite.data.DrunNotificationHelper
import com.drun.lite.model.*
import com.drun.lite.network.PocketBaseClient
import com.drun.lite.ui.screens.*
import com.drun.lite.ui.theme.DrunLiteTheme
import com.drun.lite.ui.theme.DrunThemeColors
import com.drun.lite.util.DrunUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.sse.EventSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrunLiteTheme {
                DrunLiteRootApp(onExitApp = { finish() })
            }
        }
    }
}

@Composable
private fun DrunLiteRootApp(onExitApp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pb = remember { PocketBaseClient(context) }
    val storage = remember { DrunLocalStorage(context) }
    val notifier = remember { DrunNotificationHelper(context) }

    var currentRoute by remember {
        mutableStateOf(if (pb.isValid) ScreenRoute.CHATS else ScreenRoute.LOGIN)
    }
    var currentUser by remember { mutableStateOf(pb.currentUser) }
    var displayName by remember { mutableStateOf("Загрузка...") }

    // Login state
    var loginLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf(false) }

    // Sidebar / chats state
    var chatsList by remember { mutableStateOf<List<ChatSidebarItem>>(emptyList()) }
    var chatsLoading by remember { mutableStateOf(false) }
    val usersCache = remember { mutableStateMapOf<String, DrunUser>().apply { putAll(storage.getProfileCache()) } }

    // Active chat state
    var activeChat by remember { mutableStateOf<ChatSidebarItem?>(null) }
    val activeMessages = remember { mutableStateListOf<DrunMessage>() }
    var messagesLoading by remember { mutableStateOf(false) }
    var canWriteInChat by remember { mutableStateOf(true) }
    var chatLockReason by remember { mutableStateOf("") }
    var isLoadingOlder by remember { mutableStateOf(false) }

    // Settings / Data / Slyapa state
    var cacheStats by remember { mutableStateOf<List<CacheCategoryStat>>(emptyList()) }
    var slyapaBalanceText by remember { mutableStateOf("...") }

    fun resolveUserDisplayName(u: DrunUser?): String {
        if (u == null) return "Пользователь"
        if (!DrunUtils.isEmptyName(u.name)) return u.name
        if (u.username.isNotBlank()) return "@${u.username.removePrefix("@")}"
        return "Пользователь"
    }

    suspend fun refreshSidebarChats() {
        val me = pb.currentUser?.id ?: return
        chatsLoading = true
        try {
            val rawChannels = pb.getFullChannels()
            val mine = rawChannels.filter { c ->
                c.creatorId == me ||
                    c.createdBy == me ||
                    c.members.contains(me) ||
                    DrunUtils.parseAdmins(c.admins, pb.json).contains(me)
            }

            val reqs = pb.getAcceptedFriendRequests(me)
            val friendIds = reqs.mapNotNull { r ->
                val other = if (r.from == me) r.to else r.from
                other.takeIf { it.isNotBlank() && it != me }
            }.distinct()

            val friends = if (friendIds.isNotEmpty()) {
                pb.fetchUsersByIds(friendIds).onEach { u ->
                    usersCache[u.id] = u
                    storage.mergeProfileToCache(u)
                }
            } else emptyList()

            val targetIds = linkedSetOf<String>().apply {
                mine.forEach { if (it.id.isNotBlank()) add(it.id) }
                friends.forEach { add(DrunUtils.dmKey(me, it.id)) }
                add("news")
                add("__global__")
            }.toList()

            val lastMsgMap = mutableMapOf<String, DrunMessage?>()
            for (chunk in targetIds.chunked(10)) {
                val results = chunk.map { tid ->
                    scope.async {
                        val latest = pb.getMessages(channelId = tid, page = 1, perPage = 1).firstOrNull()
                        tid to latest
                    }
                }.awaitAll()
                results.forEach { (tid, msg) -> lastMsgMap[tid] = msg }
            }

            fun previewOf(m: DrunMessage?): String {
                if (m == null) return "Нет сообщений"
                val raw = m.content.ifBlank { m.text }
                if (raw.isNotBlank()) return raw
                if (m.image.isNotBlank()) return "📷 Фото"
                return ""
            }

            val newsMsg = lastMsgMap["news"]
            val globalMsg = lastMsgMap["__global__"]

            val pinned = listOf(
                ChatSidebarItem(
                    key = "news",
                    type = "news",
                    id = "news",
                    name = "Новости",
                    lastMessage = previewOf(newsMsg),
                    time = DrunUtils.formatTime(newsMsg?.timestamp?.ifBlank { newsMsg.created }),
                    timestamp = newsMsg?.timestamp?.ifBlank { newsMsg.created } ?: ""
                ),
                ChatSidebarItem(
                    key = "__global__",
                    type = "group",
                    id = "__global__",
                    name = "Общий чат",
                    lastMessage = previewOf(globalMsg),
                    time = DrunUtils.formatTime(globalMsg?.timestamp?.ifBlank { globalMsg.created }),
                    timestamp = globalMsg?.timestamp?.ifBlank { globalMsg.created } ?: ""
                )
            )

            val friendItems = friends.map { f ->
                val chatKey = DrunUtils.dmKey(me, f.id)
                val msg = lastMsgMap[chatKey]
                var text = previewOf(msg)
                if (msg?.senderId == me) text = "Вы: $text"
                if (text.startsWith("e2ee:")) text = "🔒 Зашифровано"
                val fname = if (!DrunUtils.isEmptyName(f.name)) f.name
                else if (f.username.isNotBlank()) "@${f.username.removePrefix("@")}"
                else "Собеседник"

                ChatSidebarItem(
                    key = "friend-${f.id}",
                    type = "friend",
                    id = chatKey,
                    name = fname,
                    lastMessage = text,
                    time = DrunUtils.formatTime(msg?.timestamp?.ifBlank { msg.created }),
                    timestamp = msg?.timestamp?.ifBlank { msg.created } ?: ""
                )
            }.sortedByDescending { it.timestamp }

            val channelItems = mine.map { c ->
                val isChannel = c.type == "channel"
                val msg = lastMsgMap[c.id]
                var text = previewOf(msg)
                if (text.startsWith("e2ee:")) text = "🔒 Зашифровано"
                ChatSidebarItem(
                    key = "${if (isChannel) "channel" else "group"}-${c.id}",
                    type = if (isChannel) "channel" else "group",
                    id = c.id,
                    name = c.name.ifBlank { "Без названия" },
                    lastMessage = text,
                    time = DrunUtils.formatTime(msg?.timestamp?.ifBlank { msg.created }),
                    timestamp = msg?.timestamp?.ifBlank { msg.created } ?: "",
                    creatorId = c.creatorId,
                    createdBy = c.createdBy,
                    admins = DrunUtils.parseAdmins(c.admins, pb.json)
                )
            }.sortedByDescending { it.timestamp }

            chatsList = pinned + friendItems + channelItems
        } finally {
            chatsLoading = false
        }
    }

    suspend fun openSelectedChat(item: ChatSidebarItem) {
        activeChat = item
        currentRoute = ScreenRoute.MESSAGES
        activeMessages.clear()
        isLoadingOlder = false

        val me = pb.currentUser?.id ?: ""
        val userObj = pb.currentUser
        var allowed = true
        var reason = ""

        if (item.id == "news" || item.type == "news") {
            val isGlobalMod = userObj?.moderator == true || userObj?.isModerator == true
            if (!isGlobalMod) {
                allowed = false
                reason = "Канал новостей только для чтения"
            }
        } else if (item.type == "channel") {
            val ch = pb.getChannelOne(item.id)
            if (ch != null) {
                val creator = ch.creatorId.ifBlank { ch.creator.ifBlank { ch.createdBy } }
                val admins = DrunUtils.parseAdmins(ch.admins, pb.json)
                val isAdmin = creator == me || admins.contains(me)
                if (!isAdmin && userObj?.moderator != true && userObj?.isModerator != true) {
                    allowed = false
                    reason = "Только админы могут писать"
                }
            } else {
                allowed = false
                reason = "Ошибка прав"
            }
        }
        canWriteInChat = allowed
        chatLockReason = reason

        val cached = storage.loadMessagesFromCache(item.id)
        if (cached.isNotEmpty()) {
            activeMessages.addAll(cached.reversed())
        } else {
            messagesLoading = true
        }

        try {
            val items = pb.getMessages(channelId = item.id, page = 1, perPage = 50)
            val missingIds = items.map { it.senderId }
                .filter { it.isNotBlank() && !usersCache.containsKey(it) }
                .distinct()
            if (missingIds.isNotEmpty()) {
                pb.fetchUsersByIds(missingIds).forEach { u ->
                    usersCache[u.id] = u
                    storage.mergeProfileToCache(u)
                }
            }
            storage.saveMessagesToCache(item.id, items)
            activeMessages.clear()
            activeMessages.addAll(items.reversed())
        } finally {
            messagesLoading = false
        }
    }

    suspend fun loadSlyapaBalance() {
        val userId = pb.currentUser?.id ?: run {
            slyapaBalanceText = "—"
            return
        }
        val cachedCoins = pb.currentUser?.coins
        slyapaBalanceText = cachedCoins?.toString() ?: "..."

        var coins: Int? = null
        val refreshed = pb.authRefresh()
        if (refreshed != null) {
            currentUser = refreshed
            displayName = resolveUserDisplayName(refreshed)
            if (refreshed.coins != null) coins = refreshed.coins
        }
        if (coins == null) {
            coins = pb.getUserOne(userId)?.coins
        }
        if (coins == null) {
            val kv = pb.getKvItem("drun-dev-economy:v1")
            coins = kv?.jsonObject?.get("accounts")?.jsonObject?.get(userId)?.jsonObject?.get("hats")?.jsonPrimitive?.intOrNull
        }
        slyapaBalanceText = coins?.toString() ?: "—"
    }

    // Initial load when already logged in
    LaunchedEffect(Unit) {
        if (pb.isValid) {
            displayName = resolveUserDisplayName(pb.currentUser)
            val refreshed = pb.authRefresh()
            if (refreshed != null) {
                currentUser = refreshed
                displayName = resolveUserDisplayName(refreshed)
                storage.mergeProfileToCache(refreshed)
            }
            refreshSidebarChats()
        }
    }

    // Realtime SSE + 5s polling fallback for active chat (js/chat.js)
    DisposableEffect(currentRoute, activeChat?.id) {
        var sseSource: EventSource? = null
        val chatId = activeChat?.id
        if (currentRoute == ScreenRoute.MESSAGES && chatId != null) {
            sseSource = pb.subscribeToMessages(channelIdFilter = chatId) { action, record ->
                if (action == "create" && record.channelId == chatId) {
                    scope.launch {
                        if (activeMessages.none { it.id == record.id }) {
                            if (record.senderId.isNotBlank() && !usersCache.containsKey(record.senderId)) {
                                pb.fetchUsersByIds(listOf(record.senderId)).forEach { u ->
                                    usersCache[u.id] = u
                                    storage.mergeProfileToCache(u)
                                }
                            }
                            activeMessages.add(record)
                        }
                    }
                }
            }
        }
        onDispose {
            sseSource?.cancel()
        }
    }

    LaunchedEffect(currentRoute, activeChat?.id) {
        val chatId = activeChat?.id
        if (currentRoute == ScreenRoute.MESSAGES && chatId != null) {
            while (true) {
                delay(5000)
                val latest = pb.getMessages(channelId = chatId, page = 1, perPage = 10)
                val newOnes = latest.reversed().filter { m -> activeMessages.none { it.id == m.id } }
                if (newOnes.isNotEmpty()) {
                    activeMessages.addAll(newOnes)
                }
            }
        }
    }

    // Android hardware Back button handling (js/app.js)
    BackHandler(enabled = currentRoute != ScreenRoute.LOGIN) {
        when (currentRoute) {
            ScreenRoute.MESSAGES -> {
                activeChat = null
                currentRoute = ScreenRoute.CHATS
                scope.launch { refreshSidebarChats() }
            }
            ScreenRoute.ACCOUNT, ScreenRoute.DATA, ScreenRoute.SLYAPA -> {
                currentRoute = ScreenRoute.SETTINGS
            }
            ScreenRoute.SETTINGS -> {
                currentRoute = ScreenRoute.CHATS
            }
            else -> onExitApp()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DrunThemeColors.TgBg)
            .systemBarsPadding()
    ) {
        when (currentRoute) {
            ScreenRoute.LOGIN -> {
                LoginScreen(
                    isLoading = loginLoading,
                    showError = loginError,
                    onSubmit = { loginInput, passInput ->
                        scope.launch {
                            loginError = false
                            loginLoading = true
                            val res = pb.login(loginInput, passInput)
                            if (res.isSuccess) {
                                val u = res.getOrNull()!!
                                currentUser = u
                                displayName = resolveUserDisplayName(u)
                                storage.saveAccountToStorage(u)
                                storage.mergeProfileToCache(u)
                                currentRoute = ScreenRoute.CHATS
                                val refreshed = pb.authRefresh()
                                if (refreshed != null) {
                                    currentUser = refreshed
                                    displayName = resolveUserDisplayName(refreshed)
                                }
                                refreshSidebarChats()
                            } else {
                                loginError = true
                            }
                            loginLoading = false
                        }
                    }
                )
            }

            ScreenRoute.CHATS -> {
                ChatsListScreen(
                    chats = chatsList,
                    isLoading = chatsLoading,
                    onOpenChat = { item ->
                        scope.launch { openSelectedChat(item) }
                    }
                )
            }

            ScreenRoute.MESSAGES -> {
                val chat = activeChat
                if (chat != null) {
                    MessagesScreen(
                        chat = chat,
                        currentUserId = currentUser?.id ?: "",
                        usersCache = usersCache,
                        pb = pb,
                        messages = activeMessages,
                        isLoading = messagesLoading,
                        canWrite = canWriteInChat,
                        lockReason = chatLockReason,
                        onLoadOlder = {
                            if (!isLoadingOlder && activeMessages.isNotEmpty()) {
                                val oldestDate = activeMessages.first().created
                                if (oldestDate.isNotBlank()) {
                                    scope.launch {
                                        isLoadingOlder = true
                                        try {
                                            val older = pb.getMessages(
                                                channelId = chat.id,
                                                page = 1,
                                                perPage = 50,
                                                beforeDate = oldestDate
                                            )
                                            if (older.isNotEmpty()) {
                                                val missingIds = older.map { it.senderId }
                                                    .filter { it.isNotBlank() && !usersCache.containsKey(it) }
                                                    .distinct()
                                                if (missingIds.isNotEmpty()) {
                                                    pb.fetchUsersByIds(missingIds).forEach { u ->
                                                        usersCache[u.id] = u
                                                        storage.mergeProfileToCache(u)
                                                    }
                                                }
                                                val existingIds = activeMessages.map { it.id }.toSet()
                                                val toPrepend = older.reversed().filter { it.id !in existingIds }
                                                activeMessages.addAll(0, toPrepend)
                                            }
                                        } finally {
                                            isLoadingOlder = false
                                        }
                                    }
                                }
                            }
                        },
                        onSendText = { text, reply ->
                            val myId = currentUser?.id ?: return@MessagesScreen
                            var targetChannelId = chat.id
                            if (chat.type == "friend") {
                                val otherId = targetChannelId.split(":").find { it != myId }
                                if (otherId != null) targetChannelId = DrunUtils.dmKey(myId, otherId)
                            }
                            scope.launch {
                                val res = pb.sendTextMessage(targetChannelId, myId, text, reply)
                                if (res.isSuccess) {
                                    val sent = res.getOrNull()!!
                                    if (activeMessages.none { it.id == sent.id }) {
                                        activeMessages.add(sent)
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Ошибка отправки: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onSendImageBytes = { bytes, reply ->
                            val myId = currentUser?.id ?: return@MessagesScreen
                            scope.launch {
                                val res = pb.sendImageMessage(
                                    channelId = chat.id,
                                    senderId = myId,
                                    imageBytes = bytes,
                                    fileName = "cropped.jpg",
                                    replyTo = reply
                                )
                                if (res.isSuccess) {
                                    val sent = res.getOrNull()!!
                                    if (activeMessages.none { it.id == sent.id }) {
                                        activeMessages.add(sent)
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Ошибка: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onBack = {
                            activeChat = null
                            currentRoute = ScreenRoute.CHATS
                            scope.launch { refreshSidebarChats() }
                        }
                    )
                }
            }

            ScreenRoute.SETTINGS -> {
                SettingsScreen(
                    user = currentUser,
                    displayName = displayName,
                    onOpenAccount = { currentRoute = ScreenRoute.ACCOUNT },
                    onOpenData = {
                        cacheStats = storage.getCacheStats()
                        currentRoute = ScreenRoute.DATA
                    },
                    onOpenSlyapa = {
                        currentRoute = ScreenRoute.SLYAPA
                        scope.launch { loadSlyapaBalance() }
                    },
                    onTestNotification = { notifier.showTestNotification() }
                )
            }

            ScreenRoute.ACCOUNT -> {
                AccountScreen(
                    user = currentUser,
                    displayName = displayName,
                    savedAccounts = storage.getSavedAccounts(),
                    onBack = { currentRoute = ScreenRoute.SETTINGS },
                    onLogout = {
                        pb.clearAuth()
                        storage.clearSavedAccounts()
                        currentUser = null
                        chatsList = emptyList()
                        activeMessages.clear()
                        currentRoute = ScreenRoute.LOGIN
                    }
                )
            }

            ScreenRoute.DATA -> {
                DataScreen(
                    stats = cacheStats,
                    formatBytes = { storage.formatBytes(it) },
                    onClearCache = {
                        storage.clearAllCache()
                        cacheStats = storage.getCacheStats()
                    },
                    onBack = { currentRoute = ScreenRoute.SETTINGS }
                )
            }

            ScreenRoute.SLYAPA -> {
                SlyapaScreen(
                    balanceText = slyapaBalanceText,
                    onBack = { currentRoute = ScreenRoute.SETTINGS }
                )
            }
        }

        // Bottom navigation bar (#mainBottomNav: visible ONLY on CHATS and SETTINGS)
        if (currentRoute == ScreenRoute.CHATS || currentRoute == ScreenRoute.SETTINGS) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(DrunThemeColors.NavBg)
            ) {
                HorizontalDivider(color = DrunThemeColors.DividerColor)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chatsSelected = currentRoute == ScreenRoute.CHATS
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                currentRoute = ScreenRoute.CHATS
                                scope.launch { refreshSidebarChats() }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "💬",
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Чаты",
                            fontSize = 11.sp,
                            color = if (chatsSelected) DrunThemeColors.TgBtnBg else DrunThemeColors.TgTextSecondary
                        )
                    }

                    val settingsSelected = currentRoute == ScreenRoute.SETTINGS
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { currentRoute = ScreenRoute.SETTINGS },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⚙️",
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Настройки",
                            fontSize = 11.sp,
                            color = if (settingsSelected) DrunThemeColors.TgBtnBg else DrunThemeColors.TgTextSecondary
                        )
                    }
                }
            }
        }
    }
}
