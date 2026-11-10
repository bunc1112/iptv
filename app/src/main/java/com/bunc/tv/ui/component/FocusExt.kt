package com.bunc.tv.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/** Reports just the focused boolean, so callers don't import FocusState everywhere. */
fun Modifier.onFocusChangedCompat(block: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { block(it.isFocused) }
