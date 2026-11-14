package com.bunc.tv.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.LaunchedEffect
import com.bunc.tv.data.FolderOrder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bunc.tv.R
import com.bunc.tv.data.model.Category
import com.bunc.tv.data.model.Content
import com.bunc.tv.data.model.Section
import com.bunc.tv.ui.component.ContentCard
import com.bunc.tv.ui.component.onFocusChangedCompat
import com.bunc.tv.ui.theme.*

@Composable
fun HomeScreen(
    onPlay: (String, String) -> Unit,
    onPlayList: (List<com.bunc.tv.data.PlaybackQueue.Item>, Int) -> Unit,
    onOpenSeries: (Int, String) -> Unit,
    onLogout: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    // Re-sort folders when the sorting settings change.
    LaunchedEffect(FolderOrder.version) {
        if (FolderOrder.version > 0) vm.reorder()
    }

    // Responsive sizing: phones get fewer, bigger tiles; TV/large screens get more.
    val widthDp = LocalConfiguration.current.screenWidthDp
    val isWide = widthDp >= 600
    val railWidth = if (isWide) 240.dp else 136.dp
    val liveCols = if (isWide) 5 else 2
    val vodCols = if (isWide) 6 else 3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BuncNavyDeep)
            .systemBarsPadding()
    ) {
        TopBar(
            sections = vm.sections,
            current = vm.section,
            onSection = { vm.selectSection(it) },
            onSearch = onSearch,
            onSettings = onSettings,
            onLogout = { vm.switchProfile(onLogout) }
        )

        Row(Modifier.fillMaxSize()) {
            CategoryRail(
                categories = vm.categories,
                selectedId = vm.selectedCategory,
                onSelect = { vm.selectCategory(it) },
                modifier = Modifier.width(railWidth).fillMaxHeight()
            )

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    vm.loadingContent -> CircularProgressIndicator(
                        color = BuncGold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    vm.error != null -> Text(
                        vm.error ?: "Error",
                        color = BuncTextDim,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    vm.content.isEmpty() -> Text(
                        "Nothing here yet",
                        color = BuncTextDim,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> {
                        val live = vm.section == Section.LIVE
                        ContentGrid(
                            items = vm.content,
                            columns = if (live) liveCols else vodCols,
                            isLive = live,
                            onClick = { c -> handleClick(vm, c, onPlay, onPlayList, onOpenSeries) }
                        )
                    }
                }
            }
        }
    }
}

private fun handleClick(
    vm: HomeViewModel,
    c: Content,
    onPlay: (String, String) -> Unit,
    onPlayList: (List<com.bunc.tv.data.PlaybackQueue.Item>, Int) -> Unit,
    onOpenSeries: (Int, String) -> Unit
) {
    when (c.section) {
        Section.LIVE -> {
            // Hand the whole channel list to the player so Up/Down zaps channels.
            val list = vm.content.map { com.bunc.tv.data.PlaybackQueue.Item(it.name, vm.liveUrl(it)) }
            val start = vm.content.indexOf(c).coerceAtLeast(0)
            onPlayList(list, start)
        }
        Section.MOVIES -> onPlay(vm.movieUrl(c), c.name)
        Section.SERIES -> onOpenSeries(c.seriesId ?: c.id, c.name)
    }
}

@Composable
private fun TopBar(
    sections: List<Section>,
    current: Section,
    onSection: (Section) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BuncNavy)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.bunc_wordmark),
            contentDescription = "Bunc",
            modifier = Modifier.height(28.dp)
        )
        Spacer(Modifier.width(14.dp))
        // Tabs can scroll if they don't all fit on a narrow phone
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sections.forEach { s -> SectionTab(s.label(), s == current) { onSection(s) } }
            IconTab(Icons.Filled.Search, "Search", onSearch)
        }
        Spacer(Modifier.width(10.dp))
        IconButtonBox(Icons.Filled.Settings, "Settings", onSettings)
        Spacer(Modifier.width(8.dp))
        LogoutButton(onLogout)
    }
}

@Composable
private fun SectionTab(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> BuncGold
        focused -> BuncNavyTop
        else -> BuncNavy
    }
    Text(
        text = label,
        color = if (selected) BuncNavyDeep else BuncText,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 15.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChangedCompat { focused = it }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun IconTab(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) BuncCyan else BuncNavyTop)
            .onFocusChangedCompat { focused = it }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) BuncNavyDeep else BuncGold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (focused) BuncNavyDeep else BuncText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun IconButtonBox(icon: ImageVector, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) BuncGold else BuncNavyTop)
            .onFocusChangedCompat { focused = it }
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Icon(icon, contentDescription = description, tint = if (focused) BuncNavyDeep else BuncText)
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) BuncGold else BuncNavyTop)
            .onFocusChangedCompat { focused = it }
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Logout,
            contentDescription = "Log out",
            tint = if (focused) BuncNavyDeep else BuncText
        )
    }
}

@Composable
private fun CategoryRail(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.background(BuncNavy).padding(vertical = 8.dp)
    ) {
        items(categories, key = { it.id.ifBlank { "all" } }) { cat ->
            CategoryRow(cat, cat.id == selectedId) { onSelect(cat.id) }
        }
    }
}

@Composable
private fun CategoryRow(cat: Category, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> BuncGold
        selected -> BuncNavyTop
        else -> BuncNavy
    }
    Text(
        text = cat.name,
        color = if (focused) BuncNavyDeep else if (selected) BuncGold else BuncText,
        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .onFocusChangedCompat { focused = it }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    )
}

@Composable
private fun ContentGrid(
    items: List<Content>,
    columns: Int,
    isLive: Boolean,
    onClick: (Content) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().padding(10.dp)
    ) {
        items(items, key = { "${it.section}-${it.id}" }) { c ->
            ContentCard(
                title = c.name,
                imageUrl = c.logo,
                onClick = { onClick(c) },
                posterAspect = if (isLive) 16f / 9f else 2f / 3f,
                fitImage = isLive
            )
        }
    }
}

private fun Section.label(): String = when (this) {
    Section.LIVE -> "Live TV"
    Section.MOVIES -> "Movies"
    Section.SERIES -> "Series"
}
