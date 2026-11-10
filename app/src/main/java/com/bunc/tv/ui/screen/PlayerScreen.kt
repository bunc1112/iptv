package com.bunc.tv.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bunc.tv.data.PlaybackQueue
import com.bunc.tv.ui.theme.BuncGold
import com.bunc.tv.ui.theme.BuncNavyDeep

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val items = remember { PlaybackQueue.items }
    val canZap = items.size > 1

    var index by remember { mutableIntStateOf(PlaybackQueue.index) }
    var title by remember { mutableStateOf(PlaybackQueue.current()?.title ?: "") }
    var nameOverlay by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(false) }   // touch controls (phone)
    var tick by remember { mutableIntStateOf(0) }

    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            PlaybackQueue.current()?.let {
                setMediaItem(MediaItem.fromUri(it.url)); prepare(); playWhenReady = true
            }
        }
    }

    fun zap(delta: Int) {
        if (!canZap) return
        val ni = ((index + delta) % items.size + items.size) % items.size
        index = ni
        PlaybackQueue.index = ni
        val it = items[ni]
        title = it.title
        nameOverlay = true
        player.setMediaItem(MediaItem.fromUri(it.url))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) { onDispose { player.release() } }

    // Keep the screen awake while watching (stops the dim/screensaver).
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    BackHandler(onBack = onBack)

    val focusRequester = remember { FocusRequester() }
    if (canZap) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    // Auto-hide the channel name after it appears / changes.
    LaunchedEffect(index, nameOverlay) {
        if (nameOverlay) { kotlinx.coroutines.delay(2500); nameOverlay = false }
    }
    // Auto-hide the touch controls a few seconds after the last tap.
    LaunchedEffect(tick, controls) {
        if (controls) { kotlinx.coroutines.delay(4000); controls = false }
    }

    var rootModifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    if (canZap) {
        rootModifier = rootModifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.DirectionLeft, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.DirectionRight, Key.ChannelDown -> { zap(1); true }
                    else -> false
                }
            }
            // Tap the screen (touch / phone) to reveal the Prev / Next controls.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controls = !controls; tick++ })
            }
    }

    Box(rootModifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    keepScreenOn = true
                    useController = !canZap
                    isFocusable = !canZap
                    isFocusableInTouchMode = !canZap
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )

        if (nameOverlay && title.isNotBlank()) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC0A1130))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }

        // Touch controls: tappable Prev / Next channel buttons (for phones/tablets).
        if (canZap && controls) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChannelButton("‹ Prev", Modifier.weight(1f)) { zap(-1); tick++ }
                ChannelButton("Next ›", Modifier.weight(1f)) { zap(1); tick++ }
            }
        }
    }
}

@Composable
private fun ChannelButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BuncGold)
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = BuncNavyDeep, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
