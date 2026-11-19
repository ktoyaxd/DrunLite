package com.drun.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drun.lite.ui.theme.DrunThemeColors

@Composable
fun LoginScreen(
    isLoading: Boolean,
    showError: Boolean,
    onSubmit: (login: String, password: String) -> Unit
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DrunThemeColors.TgBg)
            .imePadding()
    ) {
        // .main-content -> .form-wrapper (max-width: 500px, padding: 24px)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Вход",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = DrunThemeColors.TgText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Введите ваш логин и пароль",
                    fontSize = 14.sp,
                    color = DrunThemeColors.TgTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(32.dp))

                // .input-group (Логин)
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DrunThemeColors.TgOutlineFocus,
                        unfocusedBorderColor = DrunThemeColors.TgOutline,
                        focusedLabelColor = DrunThemeColors.TgOutlineFocus,
                        unfocusedLabelColor = DrunThemeColors.TgOutline,
                        focusedTextColor = DrunThemeColors.TgText,
                        unfocusedTextColor = DrunThemeColors.TgText,
                        cursorColor = DrunThemeColors.TgOutlineFocus,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                // .input-group (Пароль)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DrunThemeColors.TgOutlineFocus,
                        unfocusedBorderColor = DrunThemeColors.TgOutline,
                        focusedLabelColor = DrunThemeColors.TgOutlineFocus,
                        unfocusedLabelColor = DrunThemeColors.TgOutline,
                        focusedTextColor = DrunThemeColors.TgText,
                        unfocusedTextColor = DrunThemeColors.TgText,
                        cursorColor = DrunThemeColors.TgOutlineFocus,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (login.isNotBlank() && password.isNotBlank() && !isLoading) {
                                onSubmit(login, password)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Неверный логин или пароль",
                        color = DrunThemeColors.ErrorRed,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // .fab-container (bottom: 32px, right: 24px, 56x56, #229AF0)
        FloatingActionButton(
            onClick = {
                if (login.isNotBlank() && password.isNotBlank() && !isLoading) {
                    onSubmit(login, password)
                }
            },
            shape = CircleShape,
            containerColor = DrunThemeColors.TgBtnBg,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp)
                .size(56.dp)
                .shadow(4.dp, CircleShape)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "→",
                    fontSize = 24.sp,
                    color = Color.White
                )
            }
        }
    }
}
