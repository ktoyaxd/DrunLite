package com.drun.lite.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drun.lite.R
import com.drun.lite.model.DrunUser
import com.drun.lite.model.SavedAccount
import com.drun.lite.ui.theme.DrunThemeColors
import com.drun.lite.util.DrunUtils

@Composable
fun SettingsScreen(
    user: DrunUser?,
    displayName: String,
    onOpenAccount: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSlyapa: () -> Unit,
    onTestNotification: () -> Unit
) {
    val avatarUrl = DrunUtils.getUserAvatarUrl(user)
    val phone = user?.phone?.ifBlank { user.phoneNumber } ?: ""
    val username = user?.username?.takeIf { it.isNotBlank() }?.let { "@${it.removePrefix("@")}" } ?: ""
    val subtitleLine = listOf(phone, username).filter { it.isNotBlank() }.joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DrunThemeColors.TgBg)
            .padding(bottom = 64.dp)
    ) {
        // .header (no back-btn on main settings screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(DrunThemeColors.HeaderBg)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Настройки",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = DrunThemeColors.TgText
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // .settings-profile
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (avatarUrl != null) DrunThemeColors.TgBtnBg
                            else DrunUtils.settingsAvatarColor(displayName)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = displayName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 32.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = DrunThemeColors.TgText
                )
                if (subtitleLine.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitleLine,
                        fontSize = 14.sp,
                        color = DrunThemeColors.TgTextSecondary
                    )
                }
            }

            // .settings-list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DrunThemeColors.HeaderBg)
            ) {
                SettingsListItem(
                    iconBg = Color(0xFF2196F3),
                    iconContent = { Text("👤", fontSize = 18.sp) },
                    title = "Аккаунт",
                    subtitle = "Номер, имя пользователя, «О себе»",
                    onClick = onOpenAccount
                )
                HorizontalDivider(color = DrunThemeColors.SettingsItemBorder)
                SettingsListItem(
                    iconBg = Color(0xFF9C27B0),
                    iconContent = { Text("💾", fontSize = 18.sp) },
                    title = "Данные и память",
                    subtitle = "Настройки загрузки медиафайлов",
                    onClick = onOpenData
                )
                HorizontalDivider(color = DrunThemeColors.SettingsItemBorder)
                SettingsListItem(
                    iconBg = Color(0xFFF59E0B),
                    iconContent = {
                        Image(
                            painter = painterResource(R.drawable.icon_slyapa),
                            contentDescription = "Шляпы",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    title = "Шляпы",
                    subtitle = null,
                    onClick = onOpenSlyapa
                )
                HorizontalDivider(color = DrunThemeColors.SettingsItemBorder)
                SettingsListItem(
                    iconBg = Color(0xFF10B981),
                    iconContent = { Text("🔔", fontSize = 18.sp) },
                    title = "Тестовое уведомление",
                    subtitle = "Проверить системные уведомления",
                    onClick = onTestNotification
                )
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    iconBg: Color,
    iconContent: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    titleColor: Color = DrunThemeColors.TgText,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                color = titleColor
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = DrunThemeColors.TgTextSecondary
                )
            }
        }
    }
}

@Composable
fun AccountScreen(
    user: DrunUser?,
    displayName: String,
    savedAccounts: List<SavedAccount>,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DrunThemeColors.TgBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(DrunThemeColors.HeaderBg)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", color = DrunThemeColors.TgText, fontSize = 22.sp)
            }
            Text(
                text = "Аккаунт",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = DrunThemeColors.TgText
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Name & Bio block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DrunThemeColors.HeaderBg)
                    .padding(16.dp)
            ) {
                Text("Ваше имя", fontSize = 12.sp, color = DrunThemeColors.TgTextSecondary)
                Spacer(Modifier.height(8.dp))
                Text(displayName, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DrunThemeColors.TgText)
                Spacer(Modifier.height(16.dp))
                Text("О себе", fontSize = 12.sp, color = DrunThemeColors.TgTextSecondary)
                Spacer(Modifier.height(8.dp))
                Text(user?.bio?.ifBlank { "—" } ?: "—", fontSize = 14.sp, color = DrunThemeColors.TgText)
            }

            Spacer(Modifier.height(16.dp))

            // Add account + Accounts list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DrunThemeColors.HeaderBg)
            ) {
                SettingsListItem(
                    iconBg = DrunThemeColors.TgBtnBg.copy(alpha = 0.1f),
                    iconContent = { Text("➕", color = DrunThemeColors.TgBtnBg, fontSize = 16.sp) },
                    title = "Добавить аккаунт",
                    subtitle = null,
                    titleColor = DrunThemeColors.TgBtnBg,
                    onClick = { Toast.makeText(context, "В разработке", Toast.LENGTH_SHORT).show() }
                )

                val displayAccounts = if (savedAccounts.isEmpty()) {
                    listOf(SavedAccount(id = user?.id ?: "", name = displayName, username = user?.username ?: ""))
                } else savedAccounts

                displayAccounts.forEach { acc ->
                    HorizontalDivider(color = DrunThemeColors.SettingsItemBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DrunThemeColors.TgBtnBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = acc.name.ifBlank { "?" }.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = acc.name.ifBlank { acc.username },
                            fontSize = 16.sp,
                            color = DrunThemeColors.TgText,
                            modifier = Modifier.weight(1f)
                        )
                        Text("✓", color = DrunThemeColors.TgTextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Logout button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DrunThemeColors.HeaderBg)
            ) {
                SettingsListItem(
                    iconBg = DrunThemeColors.ErrorRed.copy(alpha = 0.1f),
                    iconContent = {
                        Image(
                            painter = painterResource(R.drawable.icon_exit),
                            contentDescription = "Выход",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    title = "Выход",
                    subtitle = null,
                    titleColor = DrunThemeColors.ErrorRed,
                    onClick = onLogout
                )
            }
        }
    }
}
