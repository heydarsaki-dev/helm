package dev.helm.hermes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.helm.hermes.data.ThemeMode
import dev.helm.hermes.ui.components.PanelTap
import dev.helm.hermes.ui.components.Rule
import dev.helm.hermes.ui.run.RunScreen
import dev.helm.hermes.ui.sessions.SessionsScreen
import dev.helm.hermes.ui.settings.SettingsScreen
import dev.helm.hermes.ui.theme.HelmShape
import dev.helm.hermes.ui.theme.HelmTheme
import dev.helm.hermes.ui.theme.LocalHelm
import dev.helm.hermes.ui.theme.Motion
import dev.helm.hermes.ui.theme.Telemetry

/**
 * Three destinations, held as state rather than a navigation graph.
 *
 * Helm has one job and one back stack. A nav library would add a dependency and
 * a lifecycle to express "list → conversation → settings" and nothing else.
 */
private sealed interface Route {
    data object Sessions : Route
    data object Run : Route
    data object Settings : Route
}

@Composable
fun HelmApp(vm: HelmViewModel) {
    val dark = when (vm.themeMode) {
        ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.Night -> true
        ThemeMode.Day -> false
    }
    HelmTheme(dark = dark) {
        val c = LocalHelm.current
        var route by remember { mutableStateOf<Route>(Route.Sessions) }

        // A run in progress owns the screen: the phone should not time out
        // while a shell command nobody can see is running.
        val keepAwake = vm.keepScreenOn && vm.live?.busy == true
        val view = androidx.compose.ui.platform.LocalView.current
        val window = remember(view) { view.context as? android.app.Activity }?.window
        androidx.compose.runtime.SideEffect {
            val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (keepAwake) window?.addFlags(flag) else window?.clearFlags(flag)
        }

        Box(Modifier.fillMaxSize().background(c.ground)) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val forward = targetState != Route.Sessions
                    val width = if (forward) 1 else -1
                    (slideInHorizontally(tween(Motion.EnterMs)) { width * it / 5 } + fadeIn(tween(Motion.EnterMs)))
                        .togetherWith(
                            slideOutHorizontally(tween(Motion.EnterMs)) { -width * it / 6 } +
                                fadeOut(tween(Motion.EnterMs)),
                        )
                },
                label = "route",
            ) { target ->
                when (target) {
                    Route.Sessions -> SessionsScreen(
                        vm = vm,
                        onOpen = { id ->
                            if (id.isNotEmpty()) vm.openSession(id)
                            route = Route.Run
                        },
                        onSettings = { route = Route.Settings },
                    )

                    Route.Run -> RunScreen(vm = vm, onBack = {
                        route = Route.Sessions
                        vm.refreshSessions()
                    })

                    Route.Settings -> SettingsScreen(vm = vm, onBack = { route = Route.Sessions })
                }
            }

            Banner(vm)
        }

        BackHandler(enabled = route != Route.Sessions) {
            route = Route.Sessions
            if (vm.live?.busy != true) vm.refreshSessions()
        }
    }
}

/**
 * Transient errors, pinned under the bar.
 *
 * Only faults and holds get one. Success is never announced — a green toast
 * telling you something worked is noise, and the state change that the success
 * caused is already on screen.
 */
@Composable
private fun Banner(vm: HelmViewModel) {
    val banner = vm.banner ?: return
    val c = LocalHelm.current
    val tone = when (banner.tone) {
        Banner.Tone.Info -> c.data
        Banner.Tone.Fault -> c.alarm
        Banner.Tone.Held -> c.held
    }
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .background(tone),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    banner.text,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.text,
                )
                banner.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
            Spacer(Modifier.width(8.dp))
            PanelTap(vm::dismissBanner) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = c.textFaint,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        Rule()
    }
}
