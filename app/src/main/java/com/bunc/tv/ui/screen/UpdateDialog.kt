package com.bunc.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bunc.tv.data.Updater
import com.bunc.tv.ui.theme.*
import kotlinx.coroutines.launch

/** Shown when an update is found (or after a manual check). */
@Composable
fun UpdateDialog() {
    if (!Updater.showDialog) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = remember { FocusRequester() }
    val st = Updater.state

    Dialog(onDismissRequest = { Updater.dismiss() }) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BuncNavy)
                .padding(24.dp)
        ) {
            when (st) {
                is Updater.State.Checking, Updater.State.Idle -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = BuncGold, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Checking for updates…", color = BuncText, fontSize = 17.sp)
                    }
                }
                is Updater.State.UpToDate -> {
                    Title("Bunc is up to date")
                    Text("You have the newest version (${Updater.currentVersion}).", color = BuncTextDim)
                    Spacer(Modifier.height(18.dp))
                    DialogButton("OK", primary, gold = true) { Updater.dismiss() }
                }
                is Updater.State.Available -> {
                    Title("Update available")
                    Text("Bunc ${st.release.name} is ready (you have ${Updater.currentVersion}).", color = BuncText)
                    if (st.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            st.release.notes.take(600),
                            color = BuncTextDim,
                            fontSize = 13.sp,
                            modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogButton("Update now", primary, gold = true, modifier = Modifier.weight(1f)) {
                            scope.launch { Updater.downloadAndInstall(context, st.release) }
                        }
                        DialogButton("Later", null, gold = false, modifier = Modifier.weight(1f)) { Updater.dismiss() }
                    }
                }
                is Updater.State.Downloading -> {
                    Title("Downloading ${st.release.name}…")
                    LinearProgressIndicator(
                        progress = { st.progress },
                        color = BuncGold,
                        trackColor = BuncNavyTop,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(st.progress * 100).toInt()} %", color = BuncTextDim)
                }
                is Updater.State.Failed -> {
                    Title("Update")
                    Text(st.message, color = BuncText)
                    Spacer(Modifier.height(18.dp))
                    DialogButton("Close", primary, gold = true) { Updater.dismiss() }
                }
            }
        }
    }
    LaunchedEffect(st::class) { runCatching { primary.requestFocus() } }
}

@Composable
private fun Title(text: String) {
    Text(text, color = BuncText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DialogButton(
    label: String,
    focus: FocusRequester?,
    gold: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (gold) BuncGold else BuncNavyTop,
            contentColor = if (gold) BuncNavyDeep else BuncText
        )
    ) { Text(label) }
}
