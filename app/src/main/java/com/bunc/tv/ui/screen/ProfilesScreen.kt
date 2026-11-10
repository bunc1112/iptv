package com.bunc.tv.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bunc.tv.R
import com.bunc.tv.data.local.SavedAccount
import com.bunc.tv.ui.component.onFocusChangedCompat
import com.bunc.tv.ui.theme.*

@Composable
fun ProfilesScreen(
    onUseProfile: () -> Unit,
    onAddProfile: () -> Unit,
    vm: ProfilesViewModel = viewModel()
) {
    Box(
        modifier = Modifier.fillMaxSize().background(BuncNavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.bunc_wordmark),
                contentDescription = "Bunc",
                modifier = Modifier.width(170.dp).padding(bottom = 10.dp)
            )
            Text(
                "Choose profile",
                color = BuncTextDim,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            vm.profiles.forEach { p ->
                ProfileRow(
                    profile = p,
                    onUse = { vm.use(p, onUseProfile) },
                    onDelete = { vm.delete(p) }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            AddRow(onAddProfile)

            vm.error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        if (vm.connecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xCC0A1130)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BuncGold)
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: SavedAccount, onUse: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main select button
        var focused by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) BuncGold else BuncNavy)
                .onFocusChangedCompat { focused = it }
                .clickable { onUse() }
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                profile.name.ifBlank { if (profile.type == "m3u") "Playlist" else profile.user.ifBlank { "Profile" } },
                color = if (focused) BuncNavyDeep else BuncText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (profile.type == "m3u") "M3U playlist" else "Xtream · ${profile.host.removePrefix("http://").removePrefix("https://")}",
                color = if (focused) BuncNavyDeep else BuncTextDim,
                fontSize = 12.sp
            )
        }
        // Delete button
        var delFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (delFocused) MaterialTheme.colorScheme.tertiary else BuncNavy)
                .onFocusChangedCompat { delFocused = it }
                .clickable { onDelete() }
                .padding(14.dp)
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove profile",
                tint = if (delFocused) BuncNavyDeep else BuncTextDim
            )
        }
    }
}

@Composable
private fun AddRow(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) BuncCyan else BuncNavy)
            .onFocusChangedCompat { focused = it }
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = if (focused) BuncNavyDeep else BuncGold
        )
        Text(
            "Add new profile",
            color = if (focused) BuncNavyDeep else BuncText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
