package dev.helm.hermes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.helm.hermes.HelmViewModel
import dev.helm.hermes.Link
import dev.helm.hermes.data.ThemeMode
import dev.helm.hermes.ui.components.Dot
import dev.helm.hermes.ui.components.HelmBar
import dev.helm.hermes.ui.components.Lamp
import dev.helm.hermes.ui.components.PanelTap
import dev.helm.hermes.ui.components.Rule
import dev.helm.hermes.ui.theme.HelmShape
import dev.helm.hermes.ui.theme.LocalHelm
import dev.helm.hermes.ui.theme.Telemetry

/**
 * Settings, in the order you need them: where the gateway is, what it will
 * run, then how this app behaves, then how to start the gateway if you haven't.
 *
 * Connection state is shown by actually making the request — a switch that
 * says "connected" without having asked is a decoration, not a fact.
 */
@Composable
fun SettingsScreen(
    vm: HelmViewModel,
    onBack: () -> Unit,
) {
    val c = LocalHelm.current
    val store = vm.prefs

    var url by remember { mutableStateOf(store.baseUrl) }
    var key by remember { mutableStateOf(store.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(store.model) }

    LaunchedEffect(Unit) { vm.loadModelChoices() }

    Column(Modifier.fillMaxSize().background(c.ground)) {
        HelmBar(
            title = "Settings",
            leading = {
                PanelTap(onBack) {
                    Row(Modifier.size(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Back", style = Telemetry.readout, color = c.textMuted)
                    }
                }
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {

            SectionLabel("Gateway")
            Field(
                label = "Address",
                value = url,
                onChange = { url = it },
                placeholder = "http://127.0.0.1:8642",
                mono = true,
            )
            Field(
                label = "API server key",
                value = key,
                onChange = { key = it },
                placeholder = "API_SERVER_KEY",
                mono = true,
                secret = !showKey,
                trailing = {
                    PanelTap({ showKey = !showKey }) {
                        Text(
                            if (showKey) "Hide" else "Show",
                            style = Telemetry.readoutSmall,
                            color = c.data,
                        )
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Save and test") {
                    vm.saveConnection(url, key)
                }
                SecondaryButton("Test only") { vm.probe() }
            }
            Spacer(Modifier.height(14.dp))
            LinkReport(vm.link)
            Spacer(Modifier.height(22.dp))

            SectionLabel("Model")
            ModelPicker(
                choices = vm.models,
                selected = model,
                onSelect = {
                    model = it
                    vm.saveModel(it)
                },
            )
            Spacer(Modifier.height(22.dp))

            SectionLabel("While it works")
            ToggleRow(
                title = "Hold the screen on",
                detail = "A long tool call is not worth watching on a screen that keeps sleeping.",
                checked = vm.keepScreenOn,
                onChange = vm::setKeepScreenOn,
            )
            ToggleRow(
                title = "Show the agent's reasoning",
                detail = "Off hides it behind a disclosure instead of removing it.",
                checked = vm.showThinking,
                onChange = vm::setShowThinking,
            )
            ToggleRow(
                title = "Reopen the last session",
                detail = "Opening Helm drops you back into whatever you were working on.",
                checked = store.resumeLast,
                onChange = vm::setResumeLast,
            )
            Spacer(Modifier.height(22.dp))

            SectionLabel("Appearance")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Segmented(
                        label = when (mode) {
                            ThemeMode.System -> "System"
                            ThemeMode.Night -> "Night"
                            ThemeMode.Day -> "Day"
                        },
                        selected = vm.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            SectionLabel("Starting the gateway")
            StartGuide()
            Spacer(Modifier.height(26.dp))
        }
    }
}

/* ------------------------------------------------------------------- pieces */

@Composable
private fun SectionLabel(text: String) {
    val c = LocalHelm.current
    Column {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = c.textMuted,
            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 10.dp),
        )
        Rule()
    }
}

/**
 * A field with its label sitting above it rather than floating inside it.
 *
 * The floating-label pattern costs a line of vertical space and reads as a
 * form; this is a settings list, and the labels are already sectioned.
 */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    mono: Boolean = false,
    secret: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalHelm.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Telemetry.readoutSmall, color = c.textFaint)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.panel, HelmShape.notch)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = Telemetry.readout,
                        color = c.rule,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = if (mono) {
                        Telemetry.code.copy(color = c.text)
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(color = c.text)
                    },
                    cursorBrush = SolidColor(c.signal),
                    visualTransformation = if (secret) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** What the last probe actually found, in the gateway's own terms. */
@Composable
private fun LinkReport(link: Link) {
    val c = LocalHelm.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(c.panel, HelmShape.notch)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (link) {
            Link.Unknown -> Dot(c.textFaint)
            Link.Checking -> Dot(c.held)
            is Link.Reachable -> Dot(c.moss)
            is Link.Unreachable -> Dot(c.alarm)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (link) {
                    Link.Unknown -> "Not checked yet"
                    Link.Checking -> "Asking the gateway…"
                    is Link.Reachable -> "Connected"
                    is Link.Unreachable -> "Not reachable"
                },
                style = MaterialTheme.typography.labelLarge,
                color = c.text,
            )
            val detail = when (link) {
                is Link.Reachable -> "${link.version} · default model ${link.model}"
                is Link.Unreachable -> link.error.guidance()
                else -> ""
            }
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun ModelPicker(
    choices: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = LocalHelm.current
    val options = listOf("") + choices
    Column {
        options.forEach { option ->
            PanelTap({ onSelect(option) }) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (option.isEmpty()) "Gateway default" else option,
                        style = Telemetry.readout,
                        color = if (option == selected) c.text else c.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    if (option == selected) {
                        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = c.signal,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
            Rule()
        }
        if (choices.isEmpty()) {
            Text(
                "No models listed yet — the gateway answers this once it is running.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textFaint,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = LocalHelm.current
    PanelTap({ onChange(!checked) }) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textFaint)
            }
            Spacer(Modifier.width(14.dp))
            // A switch drawn as a slot and a knob, so it belongs to this panel.
            Box(
                Modifier
                    .width(40.dp)
                    .height(22.dp)
                    .background(
                        if (checked) c.signal.copy(alpha = 0.22f) else c.raised,
                        HelmShape.rule,
                    ),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(16.dp)
                        .background(if (checked) c.signal else c.textFaint, HelmShape.rule),
                )
            }
        }
    }
    Rule()
}

/** Segmented with an underline, not a filled pill — the bar already owns fills. */
@Composable
private fun Segmented(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalHelm.current
    PanelTap(onClick, Modifier.weight(1f)) {
        Column(
            Modifier
                .padding(top = 10.dp)
                .then(if (selected) Modifier else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) c.text else c.textFaint,
            )
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .width(if (selected) 18.dp else 0.dp)
                    .height(2.dp)
                    .background(c.signal),
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val c = LocalHelm.current
    PanelTap(onClick) {
        Box(
            Modifier
                .background(c.signal, HelmShape.notch)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.ground)
        }
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    val c = LocalHelm.current
    PanelTap(onClick) {
        Box(
            Modifier
                .background(c.raised, HelmShape.notch)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        }
    }
}

/**
 * The setup block, written as instructions rather than as a marketing panel.
 *
 * Someone installing this has a Hermes running in Termux and needs to turn on
 * its HTTP surface; that is three commands and a config key, and hiding any of
 * it behind "contact support" would be the single most annoying thing this app
 * could do.
 */
@Composable
private fun StartGuide() {
    val c = LocalHelm.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            "Hermes serves an HTTP control surface when its api_server platform is " +
                "enabled. In Termux:",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        CodeLine("hermes gateway run")
        Spacer(Modifier.height(12.dp))
        Text(
            "It listens on 127.0.0.1:8642 by default and refuses to start without " +
                "a key of at least 16 characters. Add one to ~/.hermes/config.yaml:",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        CodeLine(
            "platforms:\n  api_server:\n    enabled: true\n    extra:\n      key: choose-a-long-secret",
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Paste the same key into Helm. Point Address at the host and port if you " +
                "changed them with API_SERVER_HOST or API_SERVER_PORT.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textFaint,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lamp("SESSION FILES", c.textFaint)
            Spacer(Modifier.width(8.dp))
            Text(
                "Everything Helm shows is read from the gateway at request time.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textFaint,
            )
        }
    }
}

@Composable
private fun CodeLine(text: String) {
    val c = LocalHelm.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.panel, HelmShape.notch)
            .padding(12.dp),
    ) {
        Text(text, style = Telemetry.code, color = c.text, softWrap = true)
    }
}
