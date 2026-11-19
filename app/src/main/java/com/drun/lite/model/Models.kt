package com.drun.lite.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DrunUser(
    val id: String = "",
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val phoneNumber: String = "",
    val bio: String = "",
    val avatar: JsonElement? = null,
    val image: JsonElement? = null,
    val ProfileImg: JsonElement? = null,
    val emoji: String = "",
    val coins: Int? = null,
    val moderator: Boolean = false,
    val isModerator: Boolean = false,
    val customVerified: Boolean = false,
    val verified: Boolean = false,
    val isBusiness: Boolean = false,
    val tag: String = "",
    val tagColor: String = "",
    val tagGradient: String = "",
    val collectionId: String = "_pb_users_auth_",
    val collectionName: String = "users"
)

@Serializable
data class SavedAccount(
    val id: String,
    val name: String,
    val username: String,
    val avatar: String? = null
)

@Serializable
data class DrunChannel(
    val id: String = "",
    val name: String = "",
    val type: String = "group",
    val creatorId: String = "",
    val creator: String = "",
    val createdBy: String = "",
    val members: List<String> = emptyList(),
    val admins: JsonElement? = null,
    val created: String = "",
    val updated: String = ""
)

@Serializable
data class FriendRequest(
    val id: String = "",
    val from: String = "",
    val to: String = "",
    val status: String = ""
)

@Serializable
data class ReplyPayload(
    val id: String = "",
    val content: String = "",
    val senderName: String = "",
    val senderId: String = "",
    val quoted: Boolean = false,
    val type: String = ""
)

@Serializable
data class DrunMessage(
    val id: String = "",
    val content: String = "",
    val text: String = "",
    val senderId: String = "",
    val channelId: String = "",
    val type: String = "text",
    val image: String = "",
    val replyTo: JsonElement? = null,
    val created: String = "",
    val timestamp: String = "",
    val edited: Boolean = false,
    val status: String = "sent",
    val collectionId: String = "messages",
    val collectionName: String = "messages"
)

data class ChatSidebarItem(
    val key: String,
    val type: String, // "news", "group", "channel", "friend"
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val timestamp: String,
    val creatorId: String = "",
    val createdBy: String = "",
    val admins: List<String> = emptyList()
)

data class StickerInfo(
    val id: String,
    val url: String,
    val name: String
)

enum class ScreenRoute {
    LOGIN,
    CHATS,
    MESSAGES,
    SETTINGS,
    ACCOUNT,
    DATA,
    SLYAPA
}
