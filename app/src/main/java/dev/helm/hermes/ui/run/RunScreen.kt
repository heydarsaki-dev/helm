package dev.helm.hermes.ui.run

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.helm.hermes.Entry
import dev.helm.hermes.HelmViewModel
import dev.helm.hermes.Live
import dev.helm.hermes.RunPhase
import dev.helm.hermes.ApprovalChoice
import dev.helm.hermes.ui.Format
import dev.helm.hermes.ui.components.EmptyState
import dev.helm.hermes.ui.components.HelmBar
import dev.helm.hermes.ui.components.Lamp
import dev.helm.hermes.ui.components.PanelTap
import dev.helm.hermes.ui.components.LiveRail
import dev.helm.hermes.ui.components.RailPhase
import dev.helm.hermes.ui.components.RailTick
import dev.helm.hermes.ui.components.TickOutcome
import dev.helm.hermes.ui.components.ReadoutRow
import dev.helm.hermes.ui.components.Rule
import dev.helm.hermes.ui.theme.HelmShape
import dev.helm.hermes.ui.theme.LocalHelm
import dev.helm.hermes.ui.theme.Telemetry

/**
 * One conversation, with the run that is producing it.
 *
 * The transcript is a document, not a chat: the agent's answers run the full
 * measure of the screen and the reader's turns are set off by a rule rather
 * than a bubble, because a two-thousand-word answer about a patch is not a
 * message. Everything the agent *did* — thinking, tool calls, subagents —
 * collapses to one line each, expandable, so the answer stays the loudest thing
 * on screen.
 */
@Composable
fun RunScreen(
    vm: HelmViewModel,
    onBack: () -> Unit,
) {
    val c = LocalHelm.current
    val live = vm.live
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    // Follow the tail while the agent is producing. Only when the reader is
    // already at the bottom, so scrolling back to re-read something is not
    // fought by the stream.
    LaunchedEffect(live?.streamed, entriesSignature(vm), live?.calls?.size) {
        if (live?.busy == true) {
            val atBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?.let { it >= listState.layoutInfo.totalItemsCount - 2 } ?: true
            if (atBottom) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        }
    }

    Column(Modifier.fillMaxSize().background(c.ground)) {
        HelmBar(
            title = sessionTitle(vm),
            link = null,
            onSettings = null,
            leading = {
                PanelTap(onBack) {
                    Row(
                        Modifier.size(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconGlyph(Icons.Default.ArrowBack, "Back to sessions")
                    }
                }
            },
            trailing = {
                if (live?.busy == true) {
                    PanelTap(vm::stopRun) {
                        Row(
                            Modifier
                                .background(c.alarm.copy(alpha = 0.14f), HelmShape.notch)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("STOP", style = Telemetry.lamp, color = c.alarm)
                        }
                    }
                }
            },
        )

        StatusStrip(vm)

        Box(Modifier.weight(1f)) {
            if (vm.entries.isEmpty() && live == null) {
                EmptyState(
                    mark = false,
                    headline = "Nothing here yet",
                    body = "Tell the agent what to do. It runs on this phone, in Termux, with whatever tools its gateway has loaded.",
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 16.dp, top = 14.dp, bottom = 18.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(vm.entries) { _, entry ->
                        EntryRow(entry = entry)
                    }
                    if (live != null) {
                        item(key = "live") { LiveRunBlock(live) }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = live?.approval != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            live?.approval?.let { prompt ->
                ApprovalCard(prompt.command, prompt.choices, onAnswer = vm::answer)
            }
        }

        Composer(
            value = draft,
            onChange = { draft = it },
            steering = live?.busy == true,
            enabled = live?.approval == null,
            hint = if (live?.busy == true) {
                "Steer the agent…"
            } else {
                "Give the agent a task…"
            },
            onSend = {
                val text = draft
                if (live?.busy == true) {
                    vm.steer(text)
                } else {
                    vm.send(text)
                }
                draft = ""
            },
        )
    }
}

/* ------------------------------------------------------------------- status */

/**
 * One line of truth about the run, always visible under the title.
 *
 * It replaces a spinner with information: phase, elapsed, how many tools have
 * fired, and what it has cost so far. A phone held over a long shell command
 * needs an answer to "is it stuck?", and this is it.
 */
@Composable
private fun StatusStrip(vm: HelmViewModel) {
    val c = LocalHelm.current
    val live = vm.live
    val session = vm.sessions.firstOrNull { it.id == vm.openSessionId }

    val lamp = when (live?.phase) {
        RunPhase.Working, RunPhase.Starting -> "RUNNING" to c.signal
        RunPhase.Held -> "NEEDS YOU" to c.held
        RunPhase.Stopping -> "STOPPING" to c.alarm
        RunPhase.Faulted -> "FAILED" to c.alarm
        RunPhase.Settled -> "DONE" to c.moss
        RunPhase.Idle, null -> "IDLE" to c.textFaint
    }
    val lit = live?.busy == true || live?.phase == RunPhase.Held

    Column(Modifier.fillMaxWidth().background(c.ground)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Lamp(lamp.first, lamp.second, lit = lit)
            Text(
                if (live != null) Format.elapsed(vm.now - live.startedAt) else Format.ago(
                    session?.activityAt() ?: 0.0,
                    vm.now,
                ).ifEmpty { "—" },
                style = Telemetry.readoutSmall,
                color = c.textMuted,
            )
            Spacer(Modifier.weight(1f))
            ReadoutRow(
                parts = buildList {
                    val calls = live?.closedCalls()?.size ?: session?.toolCallCount ?: 0
                    if (calls > 0) add("$calls tools" to null)
                    val tokens = live?.usage?.totalTokens ?: session?.totalTokens ?: 0
                    if (tokens > 0) add(Format.tokens(tokens) + " tok" to null)
                    // Usage carries no cost; the session row is where the
                    // gateway reports what the work actually cost.
                    Format.cost(session?.costUsd)
                        .takeIf { it.isNotEmpty() }
                        ?.let { add(it to c.data) }
                },
            )
        }
        Rule()
    }
}

/* ---------------------------------------------------------------- transcript */

private fun entriesSignature(vm: HelmViewModel) = vm.entries.size to vm.entries.hashCode()

@Composable
private fun EntryRow(entry: Entry) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Settled history is a quiet spine. Only the run in progress lights it.
        LiveRail(RailPhase.Dormant, Modifier.fillMaxHeight(), thickness = 3.dp)
        Spacer(Modifier.width(13.dp))
        Box(Modifier.weight(1f)) {
            when (entry) {
                is Entry.Say -> SayBlock(entry)
                is Entry.Think -> ThinkBlock(entry.text)
                is Entry.Work -> WorkBand(entry.calls)
            }
        }
    }
}

@Composable
private fun SayBlock(entry: Entry.Say) {
    val c = LocalHelm.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (entry.from) {
                    dev.helm.hermes.Who.You -> "YOU"
                    dev.helm.hermes.Who.Agent -> "HERMES"
                    dev.helm.hermes.Who.Tool -> "TOOL"
                },
                style = Telemetry.lamp,
                color = when (entry.from) {
                    dev.helm.hermes.Who.You -> c.textFaint
                    dev.helm.hermes.Who.Tool -> c.textFaint
                    else -> c.data
                },
            )
            if (entry.at > 0) {
                Spacer(Modifier.width(8.dp))
                Text(Format.clock(entry.at), style = Telemetry.readoutSmall, color = c.rule)
            }
        }
        Spacer(Modifier.height(7.dp))
        if (entry.from == dev.helm.hermes.Who.You) {
            Text(
                entry.text,
                style = MaterialTheme.typography.bodyLarge,
                color = c.text.copy(alpha = 0.82f),
            )
        } else {
            AgentText(entry.text)
        }
    }
}

@Composable
private fun ThinkBlock(text: String) {
    val c = LocalHelm.current
    var open by remember(text) { mutableStateOf(false) }
    Column {
        PanelTap({ open = !open }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (open) "▾" else "▸",
                    style = Telemetry.readoutSmall,
                    color = c.textFaint,
                )
                Spacer(Modifier.width(7.dp))
                Text("THINKING", style = Telemetry.lamp, color = c.textFaint)
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = c.textFaint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------ live run */

/**
 * The run in progress.
 *
 * Tool calls appear above the answer as they happen and each one closes with
 * its own duration, so the watcher can see the agent moving even before it has
 * anything to say. The answer streams underneath with a caret.
 */
@Composable
private fun LiveRunBlock(live: Live) {
    val c = LocalHelm.current
    val rail = when (live.phase) {
        RunPhase.Working, RunPhase.Starting -> RailPhase.Working
        RunPhase.Held -> RailPhase.Held
        RunPhase.Faulted -> RailPhase.Bad
        else -> RailPhase.Dormant
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        LiveRail(
            phase = rail,
            modifier = Modifier.fillMaxHeight(),
            thickness = 3.dp,
            lit = live.busy,
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            val calls = live.closedCalls()
            if (calls.isNotEmpty()) {
                Text("WORKING", style = Telemetry.lamp, color = c.signal)
                Spacer(Modifier.height(7.dp))
                WorkBand(calls)
            }
            if (live.subagents.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SubagentBand(live)
            }
            if (live.thought.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                ThinkBlock(live.thought)
            }
            if (live.streamed.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text("HERMES", style = Telemetry.lamp, color = c.data)
                Spacer(Modifier.height(7.dp))
                AgentText(live.streamed, streaming = live.busy)
            }
            if (live.error != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(c.alarm.copy(alpha = 0.10f))) {
                    Box(Modifier.width(2.dp).heightIn(min = 40.dp).background(c.alarm))
                    Text(
                        live.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.text,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * The tool band.
 *
 * Collapsed it is one line — how many tools, how long in total, whether any of
 * them failed — because the interesting question is "is it making progress",
 * not "what file did it read". Expanded it is the receipt.
 */
@Composable
fun WorkBand(calls: List<dev.helm.hermes.ToolCall>) {
    val c = LocalHelm.current
    var open by remember(calls) { mutableStateOf(calls.any { it.failed }) }
    val failed = calls.count { it.failed }
    val total = calls.sumOf { it.seconds ?: 0.0 }
    val running = calls.count { it.seconds == null }

    Column {
        PanelTap({ open = !open }) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (open) "▾" else "▸",
                    style = Telemetry.readoutSmall,
                    color = c.textFaint,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "${calls.size} ${if (calls.size == 1) "tool" else "tools"}",
                    style = Telemetry.readout,
                    color = c.textMuted,
                )
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(Format.toolSeconds(total), style = Telemetry.readoutSmall, color = c.textFaint)
                }
                if (running > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("$running running", style = Telemetry.readoutSmall, color = c.signal)
                }
                if (failed > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("$failed failed", style = Telemetry.readoutSmall, color = c.alarm)
                }
                Spacer(Modifier.weight(1f))
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(top = 6.dp)) {
                calls.forEach { call ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        // One tick per tool, written into the rail gutter.
                        RailTick(
                            outcome = when {
                                call.seconds == null -> TickOutcome.Running
                                call.failed -> TickOutcome.Bad
                                else -> TickOutcome.Good
                            },
                            modifier = Modifier.padding(end = 2.dp),
                        )
                        Text(
                            call.name,
                            style = Telemetry.code,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Format.toolSeconds(call.seconds).ifEmpty { "…" },
                            style = Telemetry.readoutSmall,
                            color = if (call.failed) c.alarm else c.textFaint,
                        )
                    }
                    if (!call.preview.isNullOrBlank()) {
                        Text(
                            call.preview,
                            style = Telemetry.readoutSmall,
                            color = c.textFaint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Subagents get one line each: the goal, and how long it took. */
@Composable
private fun SubagentBand(live: Live) {
    val c = LocalHelm.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("SUBAGENTS", style = Telemetry.lamp, color = c.textFaint)
        live.subagents.forEach { sub ->
            Row(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .width(10.dp)
                        .height(2.dp)
                        .background(if (sub.done) c.moss else c.signal)
                        .padding(top = 7.dp),
                )
                Spacer(Modifier.width(2.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sub.goal ?: sub.summary ?: "Delegated task",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sub.done && sub.summary != null) {
                        Text(
                            sub.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textFaint,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                sub.seconds?.let {
                    Text(
                        Format.toolSeconds(it),
                        style = Telemetry.readoutSmall,
                        color = c.textFaint,
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- approval */

/**
 * The one card in the app.
 *
 * It is a card because it is an object with a decision in it, not because the
 * layout likes rounded rectangles. It shows the exact command the gateway wants
 * to run, and offers the choices the gateway actually advertised rather than a
 * fixed yes/no — a denied tool may not even offer "always".
 */
@Composable
private fun ApprovalCard(
    command: String?,
    choices: List<ApprovalChoice>,
    onAnswer: (ApprovalChoice) -> Unit,
) {
    val c = LocalHelm.current
    Column(Modifier.fillMaxWidth().background(c.ground)) {
        Rule(strong = true)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lamp("APPROVAL REQUESTED", c.held, lit = true)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "The agent wants to run something and is waiting for you.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
            )
            if (!command.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(c.panel, HelmShape.notch)
                        .padding(12.dp),
                ) {
                    Text(command, style = Telemetry.code, color = c.text, softWrap = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    val tone = if (choice == ApprovalChoice.Deny) c.alarm else c.signal
                    PanelTap({ onAnswer(choice) }) {
                        Box(
                            Modifier
                                .background(tone.copy(alpha = 0.14f), HelmShape.notch)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(choice.verb, style = Telemetry.readout, color = tone)
                        }
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- composer */

/**
 * The composer changes its job when the agent is working.
 *
 * Idle it sends a turn. Working it steers — the same box, relabelled, because
 * "tell the agent to do something different right now" is the second-most-used
 * control in this app and deserves the place the send button already is.
 */
@Composable
private fun Composer(
    value: String,
    onChange: (String) -> Unit,
    steering: Boolean,
    enabled: Boolean,
    hint: String,
    onSend: () -> Unit,
) {
    val c = LocalHelm.current
    val canSend = value.isNotBlank() && enabled
    Column(Modifier.fillMaxWidth().background(c.ground)) {
        Rule()
        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .background(c.panel, HelmShape.panel)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textFaint,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                    cursorBrush = SolidColor(if (steering) c.signal else c.data),
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .background(
                        when {
                            !enabled -> c.ruleFaint
                            canSend && steering -> c.signal
                            canSend -> c.data
                            else -> c.rule
                        },
                        HelmShape.panel,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PanelTap({ if (canSend) onSend() }, enabled = canSend) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        IconGlyph(
                            Icons.Default.Send,
                            if (steering) "Steer the agent" else "Send",
                            tint = when {
                                !canSend -> c.textFaint
                                steering -> c.ground
                                else -> c.ground
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Icons in Helm are drawn at one weight and one size, everywhere. */
@Composable
internal fun IconGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color = LocalHelm.current.textMuted,
) {
    androidx.compose.material3.Icon(
        icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(18.dp),
    )
}

private fun sessionTitle(vm: HelmViewModel): String {
    val id = vm.openSessionId ?: return "New run"
    val session = vm.sessions.firstOrNull { it.id == id }
    return session?.displayName()?.take(34) ?: "Session"
}
