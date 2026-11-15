package com.bunc.tv.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bunc.tv.data.PlaybackQueue
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.model.Program
import com.bunc.tv.ui.component.onFocusChangedCompat
import com.bunc.tv.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val hourFmt get() = SimpleDateFormat("HH:mm", Locale.getDefault())

internal fun hm(ms: Long): String = hourFmt.format(Date(ms))

@Composable
fun GuideScreen(
    onPlay: (List<PlaybackQueue.Item>, Int) -> Unit,
    vm: GuideViewModel = viewModel()
) {
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val listWidth = if (isWide) 340.dp else 150.dp
    val selectedReq = remember { FocusRequester() }

    // Clock for the header and the NOW marker / progress bars.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BuncNavyDeep)
            .systemBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().background(BuncNavy).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TV Guide", color = BuncText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text(
                vm.channels.getOrNull(vm.selected)?.title ?: "",
                color = BuncGold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(hm(now), color = BuncTextDim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        when {
            vm.loadingChannels -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = BuncGold)
            }
            vm.channels.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No channels", color = BuncTextDim)
            }
            else -> Row(Modifier.fillMaxSize()) {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.selected)
                LaunchedEffect(vm.channels) {
                    delay(150) // let the list lay out first
                    runCatching { selectedReq.requestFocus() }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(listWidth).fillMaxHeight().background(BuncNavy),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    itemsIndexed(vm.channels, key = { i, _ -> i }) { i, ch ->
                        GuideChannelRow(
                            channel = ch,
                            selected = i == vm.selected,
                            showNow = isWide,
                            modifier = if (i == vm.selected) Modifier.focusRequester(selectedReq) else Modifier,
                            onFocused = { vm.select(i) },
                            // Remote: focusing selects, OK plays. Touch: first tap selects, second tap plays.
                            onClick = { if (i == vm.selected) onPlay(vm.channels, i) else vm.select(i) }
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ScheduleList(
                        programs = vm.programs,
                        now = now,
                        onWatch = { onPlay(vm.channels, vm.selected) },
                        backToChannels = { runCatching { selectedReq.requestFocus() }.isSuccess }
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    channel: PlaybackQueue.Item,
    selected: Boolean,
    showNow: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val id = channel.streamId
    val nowTitle = if (showNow && id != null) {
        produceState<String?>(null, id) {
            value = runCatching { ServiceLocator.repo.nowPlaying(id)?.title }.getOrNull()
        }.value
    } else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> BuncGold
                    selected -> BuncNavyTop
                    else -> Color.Transparent
                }
            )
            .onFocusChangedCompat { focused = it; if (it) onFocused() }
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 46.dp, height = 30.dp), contentAlignment = Alignment.Center) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.title,
                color = if (focused) BuncNavyDeep else BuncText,
                fontSize = 14.sp,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!nowTitle.isNullOrBlank()) {
                Text(
                    nowTitle,
                    color = if (focused) BuncNavyDeep else BuncGold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private sealed class GuideEntry {
    data class Day(val label: String) : GuideEntry()
    data class Show(val program: Program) : GuideEntry()
}

private fun dayLabel(ms: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, 1)
    if (same(day, today)) return "Tomorrow"
    return SimpleDateFormat("EEEE d.M.", Locale.getDefault()).format(Date(ms))
}

@Composable
private fun ScheduleList(
    programs: List<Program>?,
    now: Long,
    onWatch: () -> Unit,
    backToChannels: () -> Boolean
) {
    when {
        programs == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = BuncGold)
        }
        programs.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text("No guide information for this channel", color = BuncTextDim)
        }
        else -> {
            val entries = remember(programs) {
                val out = ArrayList<GuideEntry>()
                var last = ""
                for (p in programs) {
                    val label = dayLabel(p.start)
                    if (label != last) { out.add(GuideEntry.Day(label)); last = label }
                    out.add(GuideEntry.Show(p))
                }
                out
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(entries, key = { e ->
                    when (e) {
                        is GuideEntry.Day -> "d-${e.label}"
                        is GuideEntry.Show -> "p-${e.program.start}"
                    }
                }) { e ->
                    when (e) {
                        is GuideEntry.Day -> Text(
                            e.label,
                            color = BuncTextDim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp)
                        )
                        is GuideEntry.Show -> ProgramRow(e.program, now, onWatch, backToChannels)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(p: Program, now: Long, onWatch: () -> Unit, backToChannels: () -> Boolean) {
    var focused by remember { mutableStateOf(false) }
    val on = p.isOn(now)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on || focused) BuncNavy else Color.Transparent)
            .border(
                BorderStroke(if (focused) 2.dp else 0.dp, if (focused) BuncGold else Color.Transparent),
                RoundedCornerShape(10.dp)
            )
            // Left on the remote goes back to the channel list (to the selected channel).
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) backToChannels() else false
            }
            .onFocusChangedCompat { focused = it }
            .clickable { onWatch() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${hm(p.start)} – ${hm(p.end)}",
                color = if (on) BuncGold else BuncTextDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (on) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "NOW",
                    color = BuncNavyDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BuncGold)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            p.title,
            color = BuncText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (on) {
            LinearProgressIndicator(
                progress = { p.progress(now) },
                color = BuncGold,
                trackColor = BuncNavyTop,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp)
            )
        }
        if (p.description.isNotBlank()) {
            Text(
                p.description,
                color = BuncTextDim,
                fontSize = 13.sp,
                maxLines = if (on || focused) 5 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
