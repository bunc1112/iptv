package com.bunc.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bunc.tv.data.FolderOrder
import com.bunc.tv.data.model.Section
import com.bunc.tv.ui.component.onFocusChangedCompat
import com.bunc.tv.ui.theme.*

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    var editIndex by remember { mutableStateOf<Int?>(null) }
    val custom = vm.mode == FolderOrder.CUSTOM

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BuncNavyDeep)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Settings", color = BuncText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Folder order", color = BuncTextDim, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        }

        // Which section's folders to arrange
        if (vm.sections.size > 1) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.sections.forEach { s ->
                    Chip(s.title(), selected = s == vm.section) { vm.selectSection(s) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Sorting mode
        item {
            Text("Sort folders by", color = BuncTextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Provider order", vm.mode == FolderOrder.SERVER) { vm.setMode(FolderOrder.SERVER) }
                Chip("A–Z", vm.mode == FolderOrder.AZ) { vm.setMode(FolderOrder.AZ) }
                Chip("My order (1, 2, 3…)", custom) { vm.setMode(FolderOrder.CUSTOM) }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            if (custom) {
                Text(
                    "Use ▲ ▼ to move a folder, or select its number and type the position you want.",
                    color = BuncTextDim, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Sort A–Z first", false) { vm.sortCustomAz() }
                    Chip("Reset to provider order", false) { vm.resetCustom() }
                }
            } else {
                Text(
                    "This is the order folders will appear in. Choose “My order” to arrange them yourself.",
                    color = BuncTextDim, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        if (vm.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BuncGold)
                }
            }
        } else {
            itemsIndexed(vm.folders, key = { _, c -> c.id }) { index, cat ->
                FolderRow(
                    number = index + 1,
                    name = cat.name,
                    editable = custom,
                    canUp = index > 0,
                    canDown = index < vm.folders.lastIndex,
                    onNumber = { editIndex = index },
                    onUp = { vm.move(index, -1) },
                    onDown = { vm.move(index, 1) }
                )
            }
        }
    }

    // Type a position number for a folder
    editIndex?.let { idx ->
        val name = vm.folders.getOrNull(idx)?.name ?: ""
        var text by remember(idx) { mutableStateOf((idx + 1).toString()) }
        AlertDialog(
            onDismissRequest = { editIndex = null },
            containerColor = BuncNavy,
            title = { Text("Move “$name”", color = BuncText) },
            text = {
                Column {
                    Text("New position (1–${vm.folders.size}):", color = BuncTextDim)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BuncGold,
                            focusedTextColor = BuncText,
                            unfocusedTextColor = BuncText
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let { vm.moveTo(idx, it - 1) }
                    editIndex = null
                }) { Text("Move", color = BuncGold) }
            },
            dismissButton = {
                TextButton(onClick = { editIndex = null }) { Text("Cancel", color = BuncTextDim) }
            }
        )
    }
}

@Composable
private fun FolderRow(
    number: Int,
    name: String,
    editable: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onNumber: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BuncNavy)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Position number — select it to type a new position
        var nFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .widthIn(min = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (nFocused) BuncGold else BuncNavyTop)
                .then(
                    if (editable) Modifier.onFocusChangedCompat { nFocused = it }.clickable { onNumber() }
                    else Modifier
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                color = if (nFocused) BuncNavyDeep else BuncGold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Text(
            name,
            color = BuncText,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (editable) {
            ArrowButton(Icons.Filled.KeyboardArrowUp, "Move up", canUp, onUp)
            ArrowButton(Icons.Filled.KeyboardArrowDown, "Move down", canDown, onDown)
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BuncCyan else BuncNavyTop)
            .onFocusChangedCompat { focused = it }
            .clickable(enabled = enabled) { onClick() }
            .padding(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                focused -> BuncNavyDeep
                enabled -> BuncText
                else -> BuncTextDim.copy(alpha = 0.4f)
            }
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = when {
            selected -> BuncNavyDeep
            focused -> BuncNavyDeep
            else -> BuncText
        },
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> BuncCyan
                    selected -> BuncGold
                    else -> BuncNavy
                }
            )
            .onFocusChangedCompat { focused = it }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

private fun Section.title(): String = when (this) {
    Section.LIVE -> "Live TV"
    Section.MOVIES -> "Movies"
    Section.SERIES -> "Series"
}
