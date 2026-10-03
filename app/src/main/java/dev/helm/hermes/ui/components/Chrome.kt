package dev.helm.hermes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.helm.hermes.Link
import dev.helm.hermes.ui.theme.HelmShape
import dev.helm.hermes.ui.theme.LocalHelm
import dev.helm.hermes.ui.theme.Telemetry

/**
 * The three marks from the launcher icon, in a row.
 *
 * They are the app's whole identity: one lit, two falling away. Used at 14dp
 * in the bar and 28dp on the empty state, never in between.
 */
@Composable
fun HelmMark(size: androidx.compose.ui.unit.Dp = 14.dp, modifier: Modifier = Modifier) {
    val c = LocalHelm.current
    val unit = size / 6f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(unit).height(size).background(c.signal, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(unit))
        listOf(1f, 0.7f, 0.4f).forEachIndexed { i, w ->
            Box(
                Modifier
                    .width(unit * 3 * w)
                    .height(unit * 1.4f)
                    .background(
                        when (i) {
                            0 -> c.text
                            1 -> c.textMuted
                            else -> c.textFaint
                        },
                        RoundedCornerShape(1.dp),
                    ),
            )
            if (i < 2) Spacer(Modifier.width(unit))
        }
    }
}

/**
 * The status lamp for the gateway link.
 *
 * "Offline" is the only word here that would ever be an excuse. Everything
 * else names what is true: checking, connected with the model it will use, or
 * not reachable.
 */
@Composable
fun LinkLamp(link: Link, modifier: Modifier = Modifier) {
    val c = LocalHelm.current
    val (label, tone, lit) = when (link) {
        Link.Unknown -> "UNKNOWN" to c.textFaint
        Link.Checking -> "CHECKING" to c.textFaint
        is Link.Reachable -> "ONLINE" to c.moss
        is Link.Unreachable -> "OFFLINE" to c.alarm
    }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Dot(if (link is Link.Checking) c.held else tone, 6.dp)
        Text(label, style = Telemetry.lamp, color = tone)
    }
}

/**
 * The one bar every screen shares: identity and state on the left, one control
 * on the right. Height is fixed and the hairline under it is the same rule used
 * everywhere else, so the screen never re-invents its own header.
 */
@Composable
fun HelmBar(
    title: String,
    modifier: Modifier = Modifier,
    link: Link? = null,
    onSettings: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalHelm.current
    Column(modifier.background(c.ground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            } else {
                HelmMark()
                Spacer(Modifier.width(10.dp))
            }
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            if (link != null) LinkLamp(link)
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
            if (onSettings != null) {
                Spacer(Modifier.width(4.dp))
                PanelTap(onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = c.textMuted,
                        modifier = Modifier.size(20.dp).padding(1.dp),
                    )
                }
            }
        }
        Rule()
    }
}

/** A 48dp square icon button sized for a thumb, drawn without a chip. */
@Composable
fun IconTap(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalHelm.current.textMuted,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .then(if (enabled) Modifier else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PanelTap(onClick, enabled = enabled) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The empty state's shape: left-aligned, on the same margin as everything else,
 * with the mark above it. A screen with nothing on it should read as an
 * instruction, not as an apology.
 */
@Composable
fun EmptyState(
    mark: Boolean,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = LocalHelm.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        if (mark) {
            HelmMark(28.dp)
            Spacer(Modifier.height(18.dp))
        }
        Text(
            headline,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = c.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Small solid action used at the bottom of empty states. */
@Composable
fun SolidAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Color = LocalHelm.current.signal,
) {
    val c = LocalHelm.current
    PanelTap(onClick, enabled = enabled, modifier = modifier) {
        Box(
            Modifier
                .background(if (enabled) tone else c.rule, HelmShape.panel)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                label,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = if (enabled) c.ground else c.textMuted,
            )
        }
    }
}
