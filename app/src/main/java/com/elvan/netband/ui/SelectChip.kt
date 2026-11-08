package com.elvan.netband.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compatibility wrapper for older call sites. New UI should use [FilterPill] directly.
 */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterPill(text = label, selected = selected, onClick = onClick, modifier = modifier)
}
