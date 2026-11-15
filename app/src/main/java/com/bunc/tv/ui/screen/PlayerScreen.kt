package com.bunc.tv.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.bunc.tv.data.NavState
import com.bunc.tv.data.PlaybackQueue
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.model.Program
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableLongStateOf
import com.bunc.tv.ui.theme.BuncNavyTop
import com.bunc.tv.ui.theme.BuncTextDim
import com.bunc.tv.ui.theme.BuncGold
import com.bunc.tv.ui.theme.BuncNavyDeep
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Bookkeeping for auto-reconnect; plain fields, only touched on the main thread. */
private class ReconnectState {
    var job: Job? = null
    var attempts = 0
    var lastPosition = -1L
    var lastProgressAt = 0L     // when the playback position last moved
    var steadySince = 0L        // start of the current run of uninterrupted playback
    var pausedByLifecycle = false
}

private const val STALL_TIMEOUT_MS = 12_000L   // live: no progress for this long => reconnect
private const val STEADY_RESET_MS = 15_000L    // this much smooth playback clears the retry counter
private const val MAX_VOD_RETRIES = 5

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, onOpenGuide: () -> Unit) {
    val context = LocalContext.current
    val items = remember { PlaybackQueue.items }
    val isLive = remember { PlaybackQueue.live }
    val canZap = items.size > 1

    // Nothing to play (e.g. the app was restored after being killed) — leave.
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var index by remember { mutableIntStateOf(PlaybackQueue.index.coerceIn(0, items.lastIndex)) }
    var title by remember { mutableStateOf(items.getOrNull(index)?.title ?: "") }
    var nameOverlay by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(false) }   // touch controls (phone)
    var tick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }   // "Reconnecting…" etc.
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }  // now + next
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasGuide = isLive && ServiceLocator.repo.hasEpg

    val scope = rememberCoroutineScope()
    val rs = remember { ReconnectState() }

    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            // Enough cushion to ride out short hiccups, still starts in ~2.5s.
            .setBufferDurationsMs(20_000, 60_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        // Providers often redirect (http -> https, or to another host); allow it and
        // use explicit timeouts so a dead connection is noticed instead of hanging.
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setLoadControl(loadControl)
            // Keep CPU + Wi-Fi awake while playing so the network isn't throttled.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /** Load the current item from scratch and play. */
    fun startFresh() {
        val item = items.getOrNull(index) ?: return
        if (item.url.isBlank()) { status = "This item has no stream address"; return }
        rs.lastPosition = -1L
        rs.lastProgressAt = SystemClock.elapsedRealtime()
        rs.steadySince = 0L
        player.setMediaItem(MediaItem.fromUri(item.url))
        player.prepare()
        player.playWhenReady = true
    }

    /** Stream died (error, ended, or stalled): try again after a short, growing delay. */
    fun scheduleReconnect(error: PlaybackException? = null) {
        if (rs.job?.isActive == true) return
        if (!isLive && rs.attempts >= MAX_VOD_RETRIES) {
            status = "Playback failed. Press Back and try again."
            return
        }
        rs.attempts++
        val waitMs = when {
            rs.attempts <= 1 -> 500L
            rs.attempts == 2 -> 1_500L
            rs.attempts <= 4 -> 3_000L
            else -> 8_000L
        }
        status = if (rs.attempts > 6) "Stream not responding — still trying…" else "Reconnecting…"
        rs.job = scope.launch {
            delay(waitMs)
            rs.lastProgressAt = SystemClock.elapsedRealtime()
            when {
                // Live: always reconnect to the live edge with a fresh request.
                isLive -> startFresh()
                // HLS fell behind the live window: jump back to the newest segment.
                error?.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    player.seekToDefaultPosition(); player.prepare(); player.playWhenReady = true
                }
                // Movie / episode: retry from where it stopped.
                else -> { player.prepare(); player.playWhenReady = true }
            }
        }
    }

    fun zap(delta: Int) {
        if (!canZap) return
        val ni = ((index + delta) % items.size + items.size) % items.size
        index = ni
        PlaybackQueue.index = ni
        title = items[ni].title
        nameOverlay = true
        rs.job?.cancel()
        rs.attempts = 0
        status = null
        startFresh()
    }

    fun openGuide() {
        if (!hasGuide) return
        NavState.guideChannels = items
        NavState.guideStart = index
        onOpenGuide()
    }

    // Now / next for the current channel; refreshed when the programme changes.
    LaunchedEffect(index) {
        epg = emptyList()
        val id = items.getOrNull(index)?.streamId ?: return@LaunchedEffect
        if (!hasGuide) return@LaunchedEffect
        while (true) {
            epg = try { ServiceLocator.repo.nowNext(id) } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e; emptyList()
            }
            clock = System.currentTimeMillis()
            val current = epg.firstOrNull { it.isOn(clock) }
            delay(current?.let { (it.end - clock + 1_000).coerceIn(15_000, 300_000) } ?: 120_000)
        }
    }

    // Player events: errors and "ended" both mean the live stream dropped.
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                scheduleReconnect(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> status = null
                    // A provider closing the connection looks like a normal "end of file".
                    Player.STATE_ENDED -> if (isLive) scheduleReconnect()
                }
            }
        }
        player.addListener(listener)
        startFresh()
        onDispose {
            rs.job?.cancel()
            player.removeListener(listener)
            player.release()
        }
    }

    // Watchdog: a live stream that makes no progress (frozen picture or endless
    // buffering) without reporting an error still gets reconnected.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            val now = SystemClock.elapsedRealtime()
            if (!player.playWhenReady || rs.job?.isActive == true) {
                rs.lastProgressAt = now
                rs.steadySince = 0L
                continue
            }
            val pos = player.currentPosition
            if (player.playbackState == Player.STATE_READY && pos != rs.lastPosition) {
                rs.lastPosition = pos
                rs.lastProgressAt = now
                if (rs.steadySince == 0L) rs.steadySince = now
                if (now - rs.steadySince > STEADY_RESET_MS) rs.attempts = 0
            } else {
                rs.steadySince = 0L
                if (isLive && now - rs.lastProgressAt > STALL_TIMEOUT_MS) scheduleReconnect()
            }
        }
    }

    // Pause when the app goes to the background; pick the stream back up on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    rs.pausedByLifecycle = player.playWhenReady
                    rs.job?.cancel()
                    player.playWhenReady = false
                }
                Lifecycle.Event.ON_START -> if (rs.pausedByLifecycle) {
                    rs.pausedByLifecycle = false
                    if (isLive) startFresh() else player.playWhenReady = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the screen awake while watching (stops the dim/screensaver).
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    BackHandler(onBack = onBack)

    val focusRequester = remember { FocusRequester() }
    if (isLive) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }

    // Auto-hide the channel name after it appears / changes.
    LaunchedEffect(index, nameOverlay) {
        if (nameOverlay) { clock = System.currentTimeMillis(); delay(5000); nameOverlay = false }
    }
    // Auto-hide the touch controls a few seconds after the last tap.
    LaunchedEffect(tick, controls) {
        if (controls) { delay(4000); controls = false }
    }

    var rootModifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    if (isLive) {
        rootModifier = rootModifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.DirectionLeft, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.DirectionRight, Key.ChannelDown -> { zap(1); true }
                    // OK shows what's on; OK again (while it's showing) opens the full guide.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (nameOverlay && hasGuide) openGuide() else nameOverlay = true
                        true
                    }
                    Key.Guide -> { if (hasGuide) openGuide() else nameOverlay = true; true }
                    Key.Info -> { nameOverlay = true; true }
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
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    // For live TV we handle keys ourselves, so the view must not grab focus/controls.
                    useController = !isLive
                    isFocusable = !isLive
                    isFocusableInTouchMode = !isLive
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )

        if (nameOverlay && title.isNotBlank()) {
            val current = epg.firstOrNull { it.isOn(clock) }
            val next = epg.firstOrNull { it.start >= (current?.end ?: clock) }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE60A1130))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (current != null) {
                    Spacer(Modifier.height(8.dp))
                    EpgLine("NOW", current, highlight = true)
                    LinearProgressIndicator(
                        progress = { current.progress(clock) },
                        color = BuncGold,
                        trackColor = BuncNavyTop,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).height(4.dp)
                    )
                }
                if (next != null) {
                    Spacer(Modifier.height(6.dp))
                    EpgLine("NEXT", next, highlight = false)
                }
                if (hasGuide) {
                    Spacer(Modifier.height(8.dp))
                    Text("OK again: full TV guide", color = BuncTextDim, fontSize = 12.sp)
                }
            }
        }

        // Reconnect / failure notice
        status?.let { msg ->
            Text(
                text = msg,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC0A1130))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // Touch controls: tappable Prev / Next channel buttons (for phones/tablets).
        if (isLive && controls) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (canZap) ChannelButton("‹ Prev", Modifier.weight(1f)) { zap(-1); tick++ }
                if (hasGuide) ChannelButton("Guide", Modifier.weight(1f)) { openGuide() }
                if (canZap) ChannelButton("Next ›", Modifier.weight(1f)) { zap(1); tick++ }
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

@Composable
private fun EpgLine(label: String, p: Program, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = if (highlight) BuncNavyDeep else BuncTextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (highlight) BuncGold else BuncNavyTop)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${hm(p.start)}–${hm(p.end)}",
            color = if (highlight) BuncGold else BuncTextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            p.title,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
