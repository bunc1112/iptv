package com.bunc.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunc.tv.ui.theme.BuncGold
import com.bunc.tv.ui.theme.BuncNavy
import com.bunc.tv.ui.theme.BuncTextDim

/**
 * A focusable poster/channel tile. Scales up and shows a gold border when focused,
 * so the cursor is always obvious on a TV remote.
 */
@Composable
fun ContentCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    posterAspect: Float = 2f / 3f,
    fitImage: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")

    Column(
        modifier = modifier
            .padding(8.dp)
            .scale(scale)
            .clickable { onClick() }
            .onFocusChangedCompat { focused = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(posterAspect)
                .clip(RoundedCornerShape(12.dp))
                .background(BuncNavy)
                .border(
                    BorderStroke(if (focused) 3.dp else 1.dp, if (focused) BuncGold else Color(0x22FFFFFF)),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = if (fitImage) ContentScale.Fit else ContentScale.Crop,
                    modifier = if (fitImage) Modifier.fillMaxSize().padding(10.dp) else Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = title.take(2).uppercase(),
                    color = BuncGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            text = title,
            color = if (focused) MaterialTheme.colorScheme.onBackground else BuncTextDim,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}
