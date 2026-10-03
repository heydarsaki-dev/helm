package dev.helm.hermes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Helm's palette.
 *
 * The subject is a control desk: you are watching a process work through a
 * small window. That argues for a cool, low-chroma ground so the only thing
 * that ever looks *lit* is the agent actually doing something. So the ramp is
 * a blue-shifted graphite (never pure grey, never pure black), and exactly
 * one saturated colour exists — [signal], a sodium-lamp amber — reserved
 * for live state.
 *
 * Four supporting hues carry meaning and nothing else:
 *  - [data]   a measurement settled: resolved counts, completed output
 *  - [moss]   a tool ran clean
 *  - [alarm]  a tool failed, an approval was denied, a run died
 *  - [held]   work paused on a human decision
 */
data class HelmColors(
    val ground: Color,
    val surface: Color,
    val panel: Color,
    val raised: Color,
    val rule: Color,
    val ruleFaint: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val signal: Color,
    val signalDim: Color,
    val signalWash: Color,
    val data: Color,
    val dataDim: Color,
    val moss: Color,
    val alarm: Color,
    val held: Color,
    val scrim: Color,
)

/** Night shift: the default. Deep blue-black with a warm lamp on the rail. */
val NightColors = HelmColors(
    ground = Color(0xFF0B0F14),
    surface = Color(0xFF111820),
    panel = Color(0xFF161F28),
    raised = Color(0xFF1E2932),
    rule = Color(0xFF27333E),
    ruleFaint = Color(0xFF1A242D),
    text = Color(0xFFE8EDF2),
    textMuted = Color(0xFF8A9BA8),
    textFaint = Color(0xFF5A6B78),
    signal = Color(0xFFFF8A3D),
    signalDim = Color(0xFF8A4E24),
    signalWash = Color(0x1AFF8A3D),
    data = Color(0xFF57C7D9),
    dataDim = Color(0xFF2C5A64),
    dataWash = Color(0x1F57C7D9),
    moss = Color(0xFF8FBF7F),
    alarm = Color(0xFFE5484D),
    held = Color(0xFFE9C46A),
    scrim = Color(0xCC060A0E),
)

/**
 * Daylight: the same instrument, read in a bright room. The ground goes to a
 * cool paper rather than a warm cream, and the lamp deepens to burnt orange
 * so it survives on white without changing what it means.
 */
val DayColors = HelmColors(
    ground = Color(0xFFF2F5F8),
    surface = Color(0xFFFFFFFF),
    panel = Color(0xFFFAFCFD),
    raised = Color(0xFFEDF1F5),
    rule = Color(0xFFD7DFE7),
    ruleFaint = Color(0xFFE7ECF1),
    text = Color(0xFF0E1519),
    textMuted = Color(0xFF54646F),
    textFaint = Color(0xFF8A9BA8),
    signal = Color(0xFFC2410C),
    signalDim = Color(0xFFE8B49A),
    signalWash = Color(0x1FC2410C),
    data = Color(0xFF0E7490),
    dataDim = Color(0xFFA8CBD8),
    dataWash = Color(0x1F0E7490),
    moss = Color(0xFF3F7A33),
    alarm = Color(0xFFC62A2F),
    held = Color(0xFF9A6B12),
    scrim = Color(0x99101820),
)
