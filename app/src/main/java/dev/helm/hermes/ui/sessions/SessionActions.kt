package dev.helm.hermes.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.helm.hermes.Session
import dev.helm.hermes.ui.Format
import dev.helm.hermes.ui.components.Rule
import dev.helm.hermes.ui.theme.HelmShape
import dev.helm.hermes.ui.theme.LocalHelm
import dev.helm.hermes.ui.theme.Telemetry

/**
 * What you can do to a conversation.
 *
 * Rename is inline and immediate — the field is the action, not a dialog
 * leading to another dialog. Deleting is the only thing that asks twice, since
 * it is the only one the gateway cannot undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionActions(
    session: Session,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalHelm.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(session.id) { mutableStateOf(session.title.orEmpty()) }
    var confirmingDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        contentColor = c.text,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 28.dp, height = 3.dp)
                        .background(c.rule, HelmShape.rule),
                )
            }
        },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SESSION",
                    style = Telemetry.lamp,
                    color = c.textFaint,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    session.id.take(12),
                    style = Telemetry.readoutSmall,
                    color = c.textFaint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 20.dp)) {
                QuietField(
                    value = title,
                    onChange = { title = it },
                    placeholder = "Name this conversation",
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Fact("Started", Format.clock(session.startedAt ?: 0.0))
                Fact("Turns", session.messageCount.toString())
                Fact("Cost", Format.cost(session.costUsd).ifEmpty { "—" })
            }
            Spacer(Modifier.height(18.dp))
            Rule()
            Spacer(Modifier.height(4.dp))

            SheetAction(
                label = if (session.pinned) "Unpin" else "Pin to top",
                detail = if (session.pinned) "Take it out of the pinned group" else "Keep it above everything else",
                tone = if (session.pinned) c.signal else c.text,
                onClick = onPin,
            )
            SheetAction(
                label = if (session.archived) "Move back to the list" else "Archive",
                detail = if (session.archived) "It stays on the gateway either way" else "Hide it without deleting anything",
                tone = c.text,
                onClick = onArchive,
            )

            Spacer(Modifier.height(8.dp))
            Rule()
            Spacer(Modifier.height(4.dp))

            if (confirmingDelete) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "This removes the conversation from the gateway for good.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Keep", c.rule, c.text) { confirmingDelete = false }
                        SmallButton("Delete", c.alarm, c.ground) { onDelete() }
                    }
                }
            } else {
                SheetAction(
                    label = "Delete",
                    detail = "Removes the transcript from the gateway",
                    tone = c.alarm,
                    onClick = { confirmingDelete = true },
                )
            }
            Spacer(Modifier.height(6.dp))
            SheetAction(
                label = "Save name",
                detail = "Writes the title to the gateway",
                tone = if (title.trim().isEmpty()) c.ruleFaint else c.signal,
                enabled = title.trim() != session.title.orEmpty() && title.isNotBlank(),
                onClick = { onRename(title.trim()) },
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val c = LocalHelm.current
    Column {
        Text(label, style = Telemetry.readoutSmall, color = c.textFaint)
        Text(value, style = Telemetry.readout, color = c.text)
    }
}

@Composable
private fun SheetAction(
    label: String,
    detail: String,
    tone: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val c = LocalHelm.current
    dev.helm.hermes.ui.components.PanelTap(onClick, enabled = enabled) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp, vertical = 11.dp),
        ) {
            Text(
                label,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = if (enabled) tone else c.rule,
            )
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = c.textFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SmallButton(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    dev.helm.hermes.ui.components.PanelTap(onClick) {
        Box(
            Modifier
                .background(background, HelmShape.notch)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = ink)
        }
    }
}
