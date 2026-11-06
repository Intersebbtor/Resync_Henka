package io.github.radixtrator.resync.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Sync's drop-down sheet: slides from the top edge over a scrim. Used for sort and More actions,
 * which Sync anchors to the toolbar rather than the bottom of the screen.
 */
@Composable
fun TopSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Swallow taps so only the scrim dismisses.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    // Sync's top sheets put their title 24dp below the status bar.
                    Column(Modifier.statusBarsPadding().padding(top = 8.dp, bottom = 12.dp), content = content)
                }
            }
        }
    }
}

/**
 * The centered title Sync puts at the top of every sheet ("Subreddit sort", "Post options"...). Bottom
 * sheets pass [topPadding] 0: there it sits right under [SheetHandle].
 */
@Composable
fun SheetTitle(title: String, modifier: Modifier = Modifier, topPadding: Dp = 16.dp) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 1.25.em),
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = topPadding, bottom = 16.dp, start = 48.dp, end = 48.dp),
    )
}

/** Sync's bottom sheet handle sits closer to the edge than Material's: 12dp above the pill, 8dp below. */
@Composable
fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 32.dp, height = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
    )
}

/**
 * A 48dp sheet row: icon (or a radio button when [radio] is set), label, optional trailing slot.
 * [selected] draws Sync's rounded highlight.
 */
@Composable
fun OptionRow(
    label: String,
    icon: ImageVector?,
    selected: Boolean = false,
    onClick: () -> Unit,
    radio: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radio != null) {
            RadioButton(selected = radio, onClick = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(24.dp))
        } else if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(24.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** One button in Sync's More actions grid. [active] marks a toggle that's on (e.g. Dark mode). */
class QuickAction(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/** Four columns of circular icon buttons with labels underneath. */
@Composable
fun QuickActionGrid(actions: List<QuickAction>, onPicked: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { action ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = {
                                onPicked()
                                action.onClick()
                            })
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (action.active) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(action.icon, null, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            action.label,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            minLines = 1,
                            maxLines = 2,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
