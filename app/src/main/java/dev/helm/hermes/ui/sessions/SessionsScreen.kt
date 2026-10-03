package dev.helm.hermes.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.helm.hermes.HelmViewModel
import dev.helm.hermes.Link
import dev.helm.hermes.Session
import dev.helm.hermes.ui.Format
import dev.helm.hermes.ui.components.EmptyState
import dev.helm.hermes.ui.components.HelmBar
import dev.helm.hermes.ui.components.Lamp
import dev.helm.hermes.ui.components.PanelTap
import dev.helm.hermes.ui.components.ReadoutRow
import dev.helm.hermes.ui.components.Rule
import dev.helm.hermes.ui.components.SolidAction
import dev.helm.hermes.ui.theme.HelmShape
import dev.helm.hermes.ui.theme.LocalHelm
import dev.helm.hermes.ui.theme.Telemetry

/**
 * The list of conversations on the gateway.
 *
 * This screen is a directory, not a feed: it answers "which piece of work is
 * this, and is it still moving". So each row leads with the name, carries the
 * last line as a subtitle, and ends with a single line of measurements. The
 * left rail marks state — lit for pinned, dotted for a run in progress, faint
 * for everything else — which is the only colour on the screen.
 */
@Composable
fun SessionsScreen(
    vm: HelmViewModel,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val c = LocalHelm.current
    val sessions = vm.visibleSessions
    var sheetFor by remember { mutableStateOf<Session?>(null) }

    Column(Modifier.fillMaxSize().background(c.ground)) {
        HelmBar(
            title = "Helm",
            link = vm.link,
            onSettings = onSettings,
            trailing = {
                PanelTap(onClick = { vm.refreshSessions() }) {
                    Icon2(Icons.Default.Add, "New session") {
                        vm.startNewSession()
                        onOpen("")
                    }
                }
            },
        )

        when (val link = vm.link) {
            is Link.Unreachable -> OfflineNotice(link.error.guidance()) { onSettings() }
            else -> Unit
        }

        if (sessions.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    mark = true,
                    headline = if (vm.sessionsBusy) "Reading the gateway" else "No sessions yet",
                    body = if (vm.sessionsBusy) {
                        "Asking the gateway what it knows about."
                    } else {
                        "Start a run and the agent will open one. Everything you send it is filed here, on the gateway, not on this phone."
                    },
                    action = {
                        SolidAction("Start a run") {
                            vm.startNewSession()
                            onOpen("")
                        }
                    },
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        active = session.id == vm.openSessionId,
                        onOpen = { onOpen(session.id) },
                        onMenu = { sheetFor = session },
                    )
                    Rule()
                }
                item {
                    val archived = vm.archivedSessions
                    Spacer(Modifier.height(8.dp))
                    ArchivedNote(archived) { newest ->
                        vm.setArchived(newest.id, false)
                    }
                }
            }
        }

        NewRunBar {
            vm.startNewSession()
            onOpen("")
        }
    }

    sheetFor?.let { session ->
        SessionActions(
            session = session,
            onDismiss = { sheetFor = null },
            onRename = { title ->
                vm.rename(session.id, title)
                sheetFor = null
            },
            onPin = {
                vm.setPinned(session.id, !session.pinned)
                sheetFor = null
            },
            onArchive = {
                vm.setArchived(session.id, !session.archived)
                sheetFor = null
            },
            onDelete = {
                vm.deleteSession(session.id)
                sheetFor = null
            },
        )
    }
}

@Composable
private fun SessionRow(
    session: Session,
    active: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = LocalHelm.current
    val rail = when {
        session.pinned -> c.signal
        active -> c.data
        else -> c.ruleFaint
    }
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .width(3.dp)
                .height(74.dp)
                .background(rail),
        )
        PanelTap(onOpen, Modifier.weight(1f)) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.displayName(),
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        color = if (active) c.text else c.text.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (session.pinned) {
                        Spacer(Modifier.width(7.dp))
                        Text("PINNED", style = Telemetry.lamp, color = c.signalDim)
                    }
                    if (session.archived) {
                        Spacer(Modifier.width(7.dp))
                        Text("ARCHIVED", style = Telemetry.lamp, color = c.textFaint)
                    }
                }
                val preview = Format.preview(session.preview)
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        preview,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(7.dp))
                ReadoutRow(
                    parts = buildList {
                        if (session.messageCount > 0) add("${session.messageCount} msgs" to null)
                        if (session.toolCallCount > 0) add("${session.toolCallCount} tools" to null)
                        val tok = session.totalTokens
                        if (tok > 0) add(Format.tokens(tok) + " tok" to null)
                        Format.cost(session.costUsd).takeIf { it.isNotEmpty() }
                            ?.let { add(it to c.data) }
                        add(Format.ago(session.activityAt()) to null)
                    },
                )
            }
        }
        PanelTap(onMenu, Modifier.size(44.dp)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon2(Icons.Default.MoreVert, "Session actions") { onMenu() }
            }
        }
    }
}

@Composable
private fun NewRunBar(onClick: () -> Unit) {
    val c = LocalHelm.current
    Column {
        Rule(strong = true)
        PanelTap(onClick, Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(c.ground)
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "New run",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = c.signal,
                )
            }
        }
    }
}

@Composable
private fun OfflineNotice(detail: String, onFix: () -> Unit) {
    val c = LocalHelm.current
    Column(Modifier.fillMaxWidth().background(c.signalWash.copy(alpha = 0.35f))) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Lamp("GATEWAY DOWN", c.alarm, lit = true)
            Spacer(Modifier.width(10.dp))
            Text(
                detail,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            PanelTap(onFix) {
                Text(
                    "Fix",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = c.signal,
                )
            }
        }
        Rule()
    }
}

/** Archived work is one tap from being back in the list, never a hidden menu. */
@Composable
private fun ArchivedNote(archived: List<Session>, onRestoreNewest: (Session) -> Unit) {
    if (archived.isEmpty()) return
    val count = archived.size
    val c = LocalHelm.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count == 1) "1 archived" else "$count archived",
            style = Telemetry.readoutSmall,
            color = c.textFaint,
        )
        Spacer(Modifier.weight(1f))
        PanelTap({ archived.maxByOrNull { it.activityAt() }?.let(onRestoreNewest) }) {
            Text(
                "Restore newest",
                style = Telemetry.readoutSmall,
                color = c.data,
            )
        }
    }
}

/** A square tap target wrapping an icon, sized for a thumb. */
@Composable
fun Icon2(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    PanelTap(onClick, Modifier.size(44.dp)) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = description,
                tint = LocalHelm.current.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A text field that matches the panel, used for renaming. */
@Composable
fun QuietField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val c = LocalHelm.current
    Box(
        modifier
            .background(c.panel, HelmShape.notch)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = c.textFaint,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = LocalTextStyle.current.merge(
                androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            ).copy(color = c.text),
            cursorBrush = SolidColor(c.signal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
