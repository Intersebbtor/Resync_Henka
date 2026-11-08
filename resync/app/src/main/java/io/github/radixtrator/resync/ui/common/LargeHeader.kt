package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The big page title Sync puts above every top-level list ("Frontpage", "pics", "Inbox"), with an
 * optional [leading] image (a community icon), an [overline] (subscriber count) and a scrolling row of
 * [chips]. It's the list's first item, so it scrolls away while the toolbar returns on scroll-up.
 */
@Composable
fun LargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    overline: (@Composable () -> Unit)? = null,
    chips: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(top = 52.dp, bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            Column {
                overline?.invoke()
                // Sync's titles are a little smaller than M3's displaySmall, and sit lower.
                Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 42.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = chips,
        )
    }
}

/** A filled, borderless chip like Sync's header chips. [dropdown] adds the ▾ for pickers. */
@Composable
fun HeaderChip(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    dropdown: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp)) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(AssistChipDefaults.IconSize)) } },
        trailingIcon = if (dropdown) {
            { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(AssistChipDefaults.IconSize)) }
        } else null,
        shape = RoundedCornerShape(8.dp),
        border = null,
        modifier = Modifier.height(36.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurface,
            trailingIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** A header chip whose ▾ opens a menu of [options], like Sync's section and sort chips. */
@Composable
fun <T> ChipMenu(label: String, icon: ImageVector, options: List<T>, labelOf: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderChip(label, icon, { open = true }, dropdown = true)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(labelOf(option)) }, onClick = {
                    open = false
                    onPick(option)
                })
            }
        }
    }
}
