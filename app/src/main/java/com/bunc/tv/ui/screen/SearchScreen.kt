package com.bunc.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bunc.tv.data.PlaybackQueue
import com.bunc.tv.data.model.Content
import com.bunc.tv.ui.component.ContentCard
import com.bunc.tv.ui.theme.*

@Composable
fun SearchScreen(
    onPlay: (String, String) -> Unit,
    onPlayList: (List<PlaybackQueue.Item>, Int) -> Unit,
    onOpenSeries: (Int, String) -> Unit,
    vm: SearchViewModel = viewModel()
) {
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val columns = if (isWide) 6 else 3
    val fieldFocus = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val hasResults = vm.live.isNotEmpty() || vm.movies.isNotEmpty() || vm.series.isNotEmpty()

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BuncNavyDeep)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.onQuery(it) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = BuncGold) },
            placeholder = { Text("Search channels, movies, series…", color = BuncTextDim) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (hasResults) runCatching { firstResult.requestFocus() }
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                // TV remote: Down jumps from the search box into the results.
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && hasResults) {
                        runCatching { firstResult.requestFocus() }; true
                    } else false
                },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BuncGold,
                unfocusedBorderColor = BuncTextDim,
                focusedTextColor = BuncText,
                unfocusedTextColor = BuncText,
                cursorColor = BuncGold
            )
        )

        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                vm.loadingCatalog -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = BuncGold)
                    Spacer(Modifier.height(10.dp))
                    Text("Loading catalog…", color = BuncTextDim)
                }
                vm.query.trim().length < 2 -> Text(
                    "Type at least 2 letters",
                    color = BuncTextDim,
                    modifier = Modifier.align(Alignment.Center)
                )
                !hasResults -> Text(
                    "No results for “${vm.query.trim()}”",
                    color = BuncTextDim,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize()
                ) {
                    var firstAssigned = false
                    fun LazyGridScope.section(
                        title: String,
                        list: List<Content>,
                        isLive: Boolean,
                        prefix: String,
                        onClick: (Content) -> Unit
                    ) {
                        if (list.isEmpty()) return
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h-$prefix") {
                            Text(
                                "$title (${list.size})",
                                color = BuncGold,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        val isFirstSection = !firstAssigned
                        firstAssigned = true
                        items(list.size, key = { "$prefix-${list[it].id}" }) { i ->
                            val c = list[i]
                            ContentCard(
                                title = c.name,
                                imageUrl = c.logo,
                                onClick = { onClick(c) },
                                posterAspect = if (isLive) 16f / 9f else 2f / 3f,
                                fitImage = isLive,
                                modifier = if (isFirstSection && i == 0) Modifier.focusRequester(firstResult) else Modifier
                            )
                        }
                    }

                    section("Live TV", vm.live, true, "l") { c ->
                        val items = vm.live.map { PlaybackQueue.Item(it.name, vm.liveUrl(it)) }
                        onPlayList(items, vm.live.indexOf(c).coerceAtLeast(0))
                    }
                    section("Movies", vm.movies, false, "m") { c -> onPlay(vm.movieUrl(c), c.name) }
                    section("Series", vm.series, false, "s") { c -> onOpenSeries(c.seriesId ?: c.id, c.name) }
                }
            }
        }
    }
}
