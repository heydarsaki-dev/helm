package dev.helm.hermes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.helm.hermes.ui.theme.HelmShape
import dev.helm.hermes.ui.theme.LocalHelm
import dev.helm.hermes.ui.theme.Motion
import dev.helm.hermes.ui.theme.Telemetry

/** A hairline that divides two kinds of thing. Never rounded, never a shadow. */
@Composable
fun Rule(modifier: Modifier = Modifier, strong: Boolean = false) {
    val c = LocalHelm.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(if (strong) c.rule else c.ruleFaint),
    )
}

/**
 * A status lamp: the state name set on a small coloured plate.
 *
 * It is a label on a panel, not a badge on a message, so it sits flush in the
 * type scale at 10sp with wide tracking and takes its colour from the phase it
 * reports. There are four of these on any screen at once, at most.
 */
@Composable
fun Lamp(
    label: String,
    tone: Color,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
) {
    Box(
        modifier
            .background(tone.copy(alpha = if (lit) 0.18f else 0.10f), HelmShape.notchShape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = Telemetry.lamp,
            color = if (lit) tone else tone.copy(alpha = 0.85f),
            maxLines = 1,
        )
    }
}

/** A small square of colour — the legend swatch next to a lamp. */
@Composable
fun Dot(color: Color, size: androidx.compose.ui.unit.Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, RoundedCornerShape(1.dp)))
}

/**
 * Tap target with no ripple paint and no pressed alpha — the instrument reads
 * as a panel, so a press is a thin underline that appears, not a wash.
 */
@Composable
fun PanelTap(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) { content() }
}

/**
 * The one bold thing in the interface: the live trace rail.
 *
 * A 3dp spine running down the gutter of the transcript. Its colour is the
 * run's current phase — dim when nothing is happening, amber while the agent
 * is working, held-yellow while it is blocked on a person, green the instant a
 * turn lands. Tool calls write 2dp ticks into the gutter beside them, one per
 * call, coloured by outcome, so the rail is a legible history of the run rather
 * than a flourish beside it.
 */
@Composable
fun LiveRail(
    phase: RailPhase,
    modifier: Modifier = Modifier,
    thickness: androidx.compose.ui.unit.Dp = 3.dp,
    lit: Boolean = false,
) {
    val c = LocalHelm.current
    val target = when (phase) {
        RailPhase.Dormant -> c.ruleFaint
        RailPhase.Working -> c.signal
        RailPhase.Held -> c.held
        RailPhase.Good -> c.moss
        RailPhase.Bad -> c.alarm
    }
    val color by animateColorAsState(target, tween(Motion.SettleMs), label = "rail")
    val pulseState = if (lit && !Motion.reduced) {
        Motion.working()
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val pulse by pulseState
    Box(
        modifier
            .width(thickness)
            .background(color.copy(alpha = if (lit) 0.55f + 0.45f * pulse else 1f)),
    )
}

enum class RailPhase { Dormant, Working, Held, Good, Bad }

/** One tool tick written into the rail gutter. */
@Composable
fun RailTick(outcome: TickOutcome, modifier: Modifier = Modifier) {
    val c = LocalHelm.current
    val target = when (outcome) {
        TickOutcome.Running -> c.signal
        TickOutcome.Good -> c.moss
        TickOutcome.Bad -> c.alarm
    }
    val tick by animateColorAsState(target, tween(Motion.SettleMs), label = "tick")
    Box(
        modifier
            .width(10.dp)
            .height(2.dp)
            .background(tick),
    )
}

enum class TickOutcome { Running, Good, Bad }

/**
 * A row of readouts. Measurements only — this is why the type is mono and the
 * separators are spaced pipes rather than middots: the row is a single line of
 * telemetry a reader scans, not a list of labels.
 */
@Composable
fun ReadoutRow(
    parts: List<Pair<String, Color?>>,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = Telemetry.readoutSmall,
) {
    if (parts.isEmpty()) return
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        parts.forEachIndexed { index, (text, tone) ->
            if (index > 0) {
                Text("|", style = style, color = LocalHelm.current.rule)
            }
            Text(
                text = text,
                style = style,
                color = tone ?: LocalHelm.current.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
