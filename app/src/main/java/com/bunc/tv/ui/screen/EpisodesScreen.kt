package com.bunc.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.model.Episode
import com.bunc.tv.ui.component.onFocusChangedCompat
import com.bunc.tv.ui.theme.*

@Composable
fun EpisodesScreen(
    seriesId: Int,
    title: String,
    onPlay: (String, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val repo = ServiceLocator.repo

    val episodes by produceState<List<Episode>?>(initialValue = null, seriesId) {
        value = try { repo.episodes(seriesId) } catch (e: Exception) { emptyList() }
    }

    Column(Modifier.fillMaxSize().background(BuncNavyDeep).padding(24.dp)) {
        Text(
            text = title.ifBlank { "Episodes" },
            color = BuncText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        when (val eps = episodes) {
            null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = BuncGold)
            }
            else -> if (eps.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No episodes found", color = BuncTextDim)
                }
            } else {
                LazyColumn {
                    items(eps, key = { it.id }) { ep ->
                        EpisodeRow(ep.title) {
                            onPlay(repo.episodeUrl(ep), "$title — ${ep.title}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = label,
        color = if (focused) BuncNavyDeep else BuncText,
        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BuncGold else BuncNavy)
            .onFocusChangedCompat { focused = it }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}
