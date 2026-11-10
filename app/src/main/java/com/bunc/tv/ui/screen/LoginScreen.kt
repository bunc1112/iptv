package com.bunc.tv.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bunc.tv.R
import com.bunc.tv.ui.theme.BuncCyan
import com.bunc.tv.ui.theme.BuncGold
import com.bunc.tv.ui.theme.BuncNavy
import com.bunc.tv.ui.theme.BuncNavyDeep
import com.bunc.tv.ui.theme.BuncTextDim

@Composable
fun LoginScreen(
    onConnected: () -> Unit,
    vm: LoginViewModel = viewModel()
) {
    // Explicit focus targets so the TV remote (and the Fire TV keyboard's Next)
    // move between fields deterministically, regardless of layout.
    val rTab = remember { FocusRequester() }
    val rName = remember { FocusRequester() }
    val rHost = remember { FocusRequester() }
    val rUser = remember { FocusRequester() }
    val rPass = remember { FocusRequester() }
    val rM3u = remember { FocusRequester() }
    val rConnect = remember { FocusRequester() }

    val xtream = vm.mode == "xtream"
    val lastFieldReq = if (xtream) rPass else rM3u
    var connectFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(BuncNavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.bunc_wordmark),
                contentDescription = "Bunc",
                modifier = Modifier.width(180.dp).padding(bottom = 24.dp)
            )

            // Mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeButton(
                    "Xtream", xtream,
                    Modifier
                        .weight(1f)
                        .focusRequester(rTab)
                        .focusProperties { down = rName }
                ) { vm.mode = "xtream" }
                ModeButton(
                    "M3U", !xtream,
                    Modifier
                        .weight(1f)
                        .focusProperties { down = rName }
                ) { vm.mode = "m3u" }
            }

            Spacer(Modifier.height(16.dp))

            Field(vm.name, { vm.name = it }, "Profile name (optional)",
                self = rName, up = rTab, down = if (xtream) rHost else rM3u)

            if (xtream) {
                Field(vm.host, { vm.host = it }, "Server URL (http://host:port)",
                    self = rHost, up = rName, down = rUser)
                Field(vm.user, { vm.user = it }, "Username",
                    self = rUser, up = rHost, down = rPass)
                Field(vm.pass, { vm.pass = it }, "Password",
                    self = rPass, up = rUser, down = rConnect, isPassword = true, imeDone = true)
            } else {
                Field(vm.m3u, { vm.m3u = it }, "M3U playlist URL",
                    self = rM3u, up = rName, down = rConnect, imeDone = true)
            }

            vm.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.tertiary)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { vm.connect(onConnected) },
                enabled = !vm.loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectFocused) BuncCyan else BuncGold,
                    contentColor = BuncNavyDeep
                ),
                border = if (connectFocused) BorderStroke(3.dp, BuncNavyDeep) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(rConnect)
                    .focusProperties { up = lastFieldReq }
                    .onFocusChanged { connectFocused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                            lastFieldReq.requestFocus(); true
                        } else false
                    },
                shape = RoundedCornerShape(12.dp)
            ) {
                if (vm.loading) {
                    CircularProgressIndicator(color = BuncNavyDeep, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Connect", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        border = if (focused) BorderStroke(3.dp, BuncCyan) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) BuncGold else BuncNavy,
            contentColor = if (selected) BuncNavyDeep else BuncTextDim
        )
    ) { Text(label) }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    self: FocusRequester,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    isPassword: Boolean = false,
    imeDone: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = if (imeDone) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { down?.requestFocus() },
            onDone = { down?.requestFocus() }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .focusRequester(self)
            .focusProperties {
                if (down != null) this.down = down
                if (up != null) this.up = up
            }
            // Hard-wire remote up/down so Fire TV can't send focus somewhere random.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> if (down != null) { down.requestFocus(); true } else false
                    Key.DirectionUp -> if (up != null) { up.requestFocus(); true } else false
                    else -> false
                }
            },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BuncGold,
            unfocusedBorderColor = BuncTextDim,
            focusedLabelColor = BuncGold
        )
    )
}
