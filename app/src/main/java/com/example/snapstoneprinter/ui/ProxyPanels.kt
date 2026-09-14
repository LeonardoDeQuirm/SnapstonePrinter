package com.example.snapstoneprinter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tone controls, deliberately behind a sheet.
 *
 * These live off the main surface on purpose: the one big target on the screen is PRINT, and a
 * slider sitting next to it is a slider that gets dragged by accident. Everything here re-dithers
 * the CACHED source art (see [ProxyGeneratorViewModel.scheduleRedither]) - no network round-trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToneSheet(
    uiState: ProxyGeneratorUiState,
    onContrastChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InlineToneControls(
                uiState = uiState,
                onContrastChange = onContrastChange,
                onBrightnessChange = onBrightnessChange,
                onReset = onReset
            )
        }
    }
}

/**
 * The slider block itself. Shared by [ToneSheet] (compact windows) and the expanded-window side
 * panel, so there is exactly one implementation of the tone UI.
 */
@Composable
fun InlineToneControls(
    uiState: ProxyGeneratorUiState,
    onContrastChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Dither tone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (uiState.isRedithering) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            text = "Applied before Floyd-Steinberg, on top of auto-levels. " +
                "Re-dithers the art already in memory - never refetches it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        ToneSlider(
            icon = { Icon(Icons.Rounded.Contrast, contentDescription = null) },
            label = "Contrast",
            valueLabel = String.format(Locale.US, "%.2f\u00d7", uiState.contrast),
            value = uiState.contrast,
            range = ProxyGeneratorViewModel.MIN_CONTRAST..ProxyGeneratorViewModel.MAX_CONTRAST,
            onValueChange = onContrastChange
        )

        ToneSlider(
            icon = { Icon(Icons.Rounded.LightMode, contentDescription = null) },
            label = "Brightness",
            valueLabel = "${uiState.brightness.roundToInt()}",
            value = uiState.brightness,
            range = ProxyGeneratorViewModel.MIN_BRIGHTNESS..ProxyGeneratorViewModel.MAX_BRIGHTNESS,
            onValueChange = onBrightnessChange
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        TextButton(
            onClick = onReset,
            enabled = !uiState.isToneMappingNeutral,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reset to auto-levels")
        }
    }
}

@Composable
private fun ToneSlider(
    icon: @Composable () -> Unit,
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        )
    }
}

/**
 * Session history. Each row is one PULL, which may be more than one slip - reprinting a DFC fires
 * both dispatches again, in order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    history: List<HistoryEntry>,
    onReprint: (HistoryEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "This session",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Last ${ProxyGeneratorViewModel.MAX_HISTORY} pulls. Reprint sends every " +
                    "slip again, one at a time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (history.isEmpty()) {
                Text(
                    text = "Nothing pulled yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(history, key = { it.id }) { entry ->
                        HistoryRow(entry = entry, onReprint = { onReprint(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onReprint: () -> Unit) {
    val thumbnail = remember(entry.id) { entry.slips.firstOrNull()?.bitmap?.asImageBitmap() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 4.dp)
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp, 56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.White),
                contentScale = ContentScale.Crop,
                // Same reasoning as the main preview: this is 1-bit output, do not smooth it.
                filterQuality = FilterQuality.None
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.cardName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(entry.typeLine ?: "")
                    if (entry.slipCount > 1) {
                        if (isNotEmpty()) append(" \u2014 ")
                        append("${entry.slipCount} slips")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onReprint, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Print, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Reprint")
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=914dp,dpi=420")
@Composable
private fun ToneSheetContentPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(24.dp)) {
            ToneSlider(
                icon = { Icon(Icons.Rounded.Contrast, contentDescription = null) },
                label = "Contrast",
                valueLabel = "1.40\u00d7",
                value = 1.4f,
                range = 0.5f..3f,
                onValueChange = {}
            )
        }
    }
}
