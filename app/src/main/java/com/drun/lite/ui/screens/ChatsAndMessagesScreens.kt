package com.drun.lite.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.drun.lite.R
import com.drun.lite.model.*
import com.drun.lite.network.PocketBaseClient
import com.drun.lite.ui.components.CropperModal
import com.drun.lite.ui.theme.DrunThemeColors
import com.drun.lite.util.DrunUtils
import kotlinx.coroutines.launch

@Composable
fun ChatsListScreen(
    chats: List<ChatSidebarItem>,
    isLoading: Boolean,
    onOpenChat: (ChatSidebarItem) -> Unit
) {
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else {
            val q = searchQuery.lowercase()
            chats.filter {
                it.name.lowercase().contains(q) || it.lastMessage.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DrunThemeColors.TgBg)
            .padding(bottom = 64.dp)
    ) {
        // .header (height: 56px, padding: 12px 16px, #212121)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(DrunThemeColors.HeaderBg)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(R.drawable.icon_dlwhite),
                contentDescription = "DrunLite",
                modifier = Modifier.height(28.dp)
            )
            Row {
                IconButton(onClick = {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchQuery = ""
                }) {
                    Text("🔍", color = DrunThemeColors.TgText, fontSize = 18.sp)
                }
                IconButton(onClick = { }) {
                    Text("⋮", color = DrunThemeColors.TgText, fontSize = 22.sp)
                }
            }
        }

        // .search-bar-container
        if (searchVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DrunThemeColors.HeaderBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(DrunThemeColors.TgInputBg)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text("Поиск чатов...", color = DrunThemeColors.TgTextSecondary, fontSize = 16.sp)
                            }
                            inner()
                        }
                    }
                )
            }
        }

        // .chat-list
        if (isLoading && chats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Загрузка чатов...", color = DrunThemeColors.TgTextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredChats, key = { it.key }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(chat) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // .avatar (54x54 circle)
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(DrunUtils.avatarColorFor(chat.name)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.name.ifBlank { "?" }.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = chat.name.ifBlank { "Без названия" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DrunThemeColors.TgText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (chat.time.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = chat.time,
                                        fontSize = 12.sp,
                                        color = DrunThemeColors.TgTextSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            val cleanLast = chat.lastMessage.replace(Regex("[\r\n]+"), " ")
                            Text(
                                text = cleanLast,
                                fontSize = 14.sp,
                                color = DrunThemeColors.TgTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessagesScreen(
    chat: ChatSidebarItem,
    currentUserId: String,
    usersCache: Map<String, DrunUser>,
    pb: PocketBaseClient,
    messages: List<DrunMessage>,
    isLoading: Boolean,
    canWrite: Boolean,
    lockReason: String,
    onLoadOlder: () -> Unit,
    onSendText: (String, ReplyPayload?) -> Unit,
    onSendImageBytes: (ByteArray, ReplyPayload?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var msgInput by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<DrunMessage?>(null) }
    var contextMenuMsg by remember { mutableStateOf<DrunMessage?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var cropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val stickerCache = remember { mutableStateMapOf<String, StickerInfo?>() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) cropBitmap = bmp
                }
            }
        }
    }

    fun resolveSenderName(senderId: String, fallback: String? = null): String {
        if (!fallback.isNullOrBlank() && !DrunUtils.isEmptyName(fallback) && fallback != "Вы") return fallback
        if (senderId == currentUserId) {
            val me = pb.currentUser
            if (me != null) {
                if (!DrunUtils.isEmptyName(me.name)) return me.name
                if (me.username.isNotBlank()) return "@${me.username.removePrefix("@")}"
            }
            return "Вы"
        }
        val u = usersCache[senderId]
        if (u != null) {
            if (!DrunUtils.isEmptyName(u.name)) return u.name
            if (u.username.isNotBlank()) return "@${u.username.removePrefix("@")}"
        }
        return "Собеседник"
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    // Detect top scroll to load older messages
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisible) {
        if (firstVisible == 0 && messages.size >= 50) {
            onLoadOlder()
        }
    }

    val showScrollDown by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isNotEmpty() && lastVisible < messages.lastIndex - 3
        }
    }

    // Cropper modal overlay (#cropModal)
    if (cropBitmap != null) {
        CropperModal(
            sourceBitmap = cropBitmap!!,
            onCancel = { cropBitmap = null },
            onConfirmBytes = { bytes ->
                val rep = replyingTo?.let { r ->
                    ReplyPayload(
                        id = r.id,
                        content = r.content.ifBlank { r.text },
                        senderName = resolveSenderName(r.senderId),
                        senderId = r.senderId,
                        quoted = false
                    )
                }
                replyingTo = null
                cropBitmap = null
                onSendImageBytes(bytes, rep)
            }
        )
        return
    }

    // Fullscreen photo modal (.photo-modal)
    if (fullScreenImageUrl != null) {
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { fullScreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullScreenImageUrl,
                    contentDescription = "Full size",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.9f)
                        .clip(RoundedCornerShape(12.dp))
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Text("✕", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }

    // Reply context menu (.context-menu)
    if (contextMenuMsg != null && canWrite) {
        AlertDialog(
            onDismissRequest = { contextMenuMsg = null },
            containerColor = Color(0xFF2C2C2C),
            title = null,
            text = {
                TextButton(
                    onClick = {
                        replyingTo = contextMenuMsg
                        contextMenuMsg = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("↩️ Ответить", color = Color.White, fontSize = 15.sp)
                }
            },
            confirmButton = {}
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DrunThemeColors.TgBg)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // .msg-header (height: 56px, border-bottom: 1px solid #303030)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(DrunThemeColors.HeaderBg)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Text("←", color = Color.White, fontSize = 22.sp)
                }
                Text(
                    text = chat.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = DrunThemeColors.TgText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = DrunThemeColors.DividerColor)

            // .messages-area
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading && messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Загрузка...", color = DrunThemeColors.TgTextSecondary)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { msg ->
                            val isOut = msg.senderId == currentUserId
                            val rawContent = msg.content.ifBlank { msg.text }
                            val showSenderInfo = !isOut && chat.type in listOf("group", "channel", "news")
                            val replyObj = remember(msg.replyTo) { DrunUtils.parseReplyTo(msg.replyTo, pb.json) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(msg.id) {
                                        detectTapGestures(
                                            onLongPress = {
                                                if (canWrite) contextMenuMsg = msg
                                            }
                                        )
                                    },
                                horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Group/Channel sender avatar (32x32)
                                if (showSenderInfo) {
                                    val senderUser = usersCache[msg.senderId]
                                    val senderName = resolveSenderName(msg.senderId)
                                    val avatarUrl = DrunUtils.getUserAvatarUrl(senderUser)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(DrunUtils.avatarColorFor(senderName)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarUrl != null) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = senderName.take(1).uppercase(),
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }

                                Column(
                                    horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
                                    modifier = Modifier.widthIn(max = 300.dp)
                                ) {
                                    if (showSenderInfo) {
                                        val sName = resolveSenderName(msg.senderId)
                                        Text(
                                            text = sName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = DrunUtils.senderNameColor(sName),
                                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                        )
                                    }

                                    // Sticker message ("mell:<id>")
                                    if (rawContent.startsWith("mell:")) {
                                        val stickerId = rawContent.removePrefix("mell:")
                                        LaunchedEffect(stickerId) {
                                            if (!stickerCache.containsKey(stickerId)) {
                                                stickerCache[stickerId] = pb.getStickerOne(stickerId)
                                            }
                                        }
                                        val st = stickerCache[stickerId]
                                        if (replyObj != null) {
                                            ReplyQuoteBox(
                                                reply = replyObj,
                                                authorName = resolveSenderName(replyObj.senderId, replyObj.senderName),
                                                onClick = {
                                                    val idx = messages.indexOfFirst { it.id == replyObj.id }
                                                    if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                                                }
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.size(140.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (st != null && st.url.isNotBlank()) {
                                                AsyncImage(
                                                    model = st.url,
                                                    contentDescription = st.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(140.dp)
                                                )
                                            } else {
                                                Text("🎞️", fontSize = 32.sp)
                                            }
                                        }
                                    }
                                    // Image message
                                    else if (msg.image.isNotBlank()) {
                                        val imgUrl = DrunUtils.getMessageImageUrl(msg)
                                        Column(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (replyObj != null) {
                                                        if (isOut) DrunThemeColors.BubbleOut else DrunThemeColors.BubbleIn
                                                    } else Color.Transparent
                                                )
                                                .padding(if (replyObj != null) 4.dp else 0.dp)
                                        ) {
                                            if (replyObj != null) {
                                                ReplyQuoteBox(
                                                    reply = replyObj,
                                                    authorName = resolveSenderName(replyObj.senderId, replyObj.senderName),
                                                    onClick = {
                                                        val idx = messages.indexOfFirst { it.id == replyObj.id }
                                                        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                                                    }
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(max = 260.dp)
                                                    .heightIn(max = 320.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { if (imgUrl != null) fullScreenImageUrl = imgUrl }
                                            ) {
                                                AsyncImage(
                                                    model = imgUrl,
                                                    contentDescription = "photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                if (rawContent.isBlank()) {
                                                    Text(
                                                        text = DrunUtils.formatTime(msg.created),
                                                        fontSize = 11.sp,
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(8.dp)
                                                            .background(
                                                                Color.Black.copy(alpha = 0.5f),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (rawContent.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                MessageBubble(
                                                    text = rawContent,
                                                    time = DrunUtils.formatTime(msg.created),
                                                    isOut = isOut,
                                                    reply = null,
                                                    replyAuthor = "",
                                                    onReplyClick = {}
                                                )
                                            }
                                        }
                                    }
                                    // Standard text bubble
                                    else {
                                        val displayText = if (rawContent.startsWith("e2ee:")) "🔒 Зашифровано" else rawContent
                                        MessageBubble(
                                            text = displayText,
                                            time = DrunUtils.formatTime(msg.created),
                                            isOut = isOut,
                                            reply = replyObj,
                                            replyAuthor = replyObj?.let { resolveSenderName(it.senderId, it.senderName) } ?: "",
                                            onReplyClick = {
                                                val targetId = replyObj?.id ?: return@MessageBubble
                                                val idx = messages.indexOfFirst { it.id == targetId }
                                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // .scroll-down-btn
                if (showScrollDown) {
                    FloatingActionButton(
                        onClick = {
                            if (messages.isNotEmpty()) {
                                scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                            }
                        },
                        shape = CircleShape,
                        containerColor = DrunThemeColors.HeaderBg,
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(44.dp)
                            .shadow(4.dp, CircleShape)
                    ) {
                        Text("↓", fontSize = 20.sp, color = Color.White)
                    }
                }
            }

            // #reply-preview panel above input
            if (replyingTo != null && canWrite) {
                val r = replyingTo!!
                val rAuthor = resolveSenderName(r.senderId)
                val rText = r.content.ifBlank { r.text }.ifBlank { if (r.image.isNotBlank()) "📷 Фото" else "Вложение" }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2C2C2C))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .background(DrunThemeColors.TgBtnBg)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ответ: $rAuthor",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DrunThemeColors.TgBtnBg
                        )
                        Text(
                            text = rText,
                            fontSize = 14.sp,
                            color = DrunThemeColors.TgTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { replyingTo = null }) {
                        Text("✕", color = DrunThemeColors.TgTextSecondary, fontSize = 18.sp)
                    }
                }
            }

            // .msg-input-area OR #channel-lock-msg
            if (!canWrite) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DrunThemeColors.HeaderBg)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lockReason,
                        color = DrunThemeColors.TgTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DrunThemeColors.HeaderBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Text("🖼️", fontSize = 20.sp)
                    }

                    BasicTextField(
                        value = msgInput,
                        onValueChange = { msgInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val t = msgInput.trim()
                                if (t.isNotEmpty()) {
                                    val rep = replyingTo?.let { r ->
                                        ReplyPayload(
                                            id = r.id,
                                            content = r.content.ifBlank { r.text },
                                            senderName = resolveSenderName(r.senderId),
                                            senderId = r.senderId,
                                            quoted = false
                                        )
                                    }
                                    msgInput = ""
                                    replyingTo = null
                                    onSendText(t, rep)
                                }
                            }
                        ),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(DrunThemeColors.TgInputBg)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (msgInput.isEmpty()) {
                                    Text("Сообщение...", color = DrunThemeColors.TgTextSecondary, fontSize = 16.sp)
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            val t = msgInput.trim()
                            if (t.isNotEmpty()) {
                                val rep = replyingTo?.let { r ->
                                    ReplyPayload(
                                        id = r.id,
                                        content = r.content.ifBlank { r.text },
                                        senderName = resolveSenderName(r.senderId),
                                        senderId = r.senderId,
                                        quoted = false
                                    )
                                }
                                msgInput = ""
                                replyingTo = null
                                onSendText(t, rep)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DrunThemeColors.TgBtnBg)
                    ) {
                        Text("➤", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    time: String,
    isOut: Boolean,
    reply: ReplyPayload?,
    replyAuthor: String,
    onReplyClick: () -> Unit
) {
    val shape = if (isOut) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (isOut) DrunThemeColors.BubbleOut else DrunThemeColors.BubbleIn)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (reply != null) {
            ReplyQuoteBox(reply = reply, authorName = replyAuthor, onClick = onReplyClick)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 19.5.sp
        )
        if (time.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = time,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ReplyQuoteBox(
    reply: ReplyPayload,
    authorName: String,
    onClick: () -> Unit
) {
    val preview = when {
        reply.content.startsWith("mell:") -> "🎞️ Стикер"
        reply.content.startsWith("📷") || reply.type == "image" -> "📷 Фото"
        else -> reply.content
    }

    Row(
        modifier = Modifier
            .widthIn(min = 180.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(Color.White)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = authorName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
            )
            Text(
                text = preview,
                fontSize = 13.sp,
                color = Color(0xFFEEEEEE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
