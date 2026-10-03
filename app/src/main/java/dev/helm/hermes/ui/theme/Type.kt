package dev.helm.hermes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dev.helm.hermes.R

/**
 * Two voices plus a data voice.
 *
 * Space Grotesk carries anything that labels or titles — it is a signage
 * face, wide and slightly mechanical, so it reads as panel engraving rather
 * than as prose. IBM Plex Sans carries prose (the agent's answers, the words
 * a person writes), because it holds up at 15sp/22 on a phone better than
 * anything geometric. IBM Plex Mono carries *measurements only* — counts,
 * durations, costs, timestamps, status lamps, tool names, paths, ids. It is
 * never used as decoration and never used above a heading.
 *
 * Both Plex faces ship as variable fonts, so weights are requested through
 * `FontVariation` rather than by shipping nine static files.
 */
private fun axisWeight(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

val Display = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = arrayOf(axisWeight(500))),
    Font(R.font.space_grotesk, FontWeight.SemiBold, variationSettings = arrayOf(axisWeight(600))),
    Font(R.font.space_grotesk, FontWeight.Bold, variationSettings = arrayOf(axisWeight(700))),
)

val Body = FontFamily(
    Font(R.font.plex_sans, FontWeight.Normal, variationSettings = arrayOf(axisWeight(400))),
    Font(R.font.plex_sans, FontWeight.Medium, variationSettings = arrayOf(axisWeight(500))),
    Font(R.font.plex_sans, FontWeight.SemiBold, variationSettings = arrayOf(axisWeight(600))),
)

val Mono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

/** Trim the extra leading Android adds so headings sit tight to their rule. */
private val Tight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private val DisplayFamily = Display
private val BodyFamily = Body
private val MonoFamily = Mono

/**
 * A phone-scale type ramp. Six steps, because a console has five kinds of
 * thing to say and no more: a screen name, a section, a sentence, a label, a
 * measurement, and a lamp.
 */
val HelmType = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp, lineHeight = 29.sp, letterSpacing = (-0.6).sp,
        lineHeightStyle = Tight,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp,
        lineHeightStyle = Tight,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.15).sp,
        lineHeightStyle = Tight,
    ),
    titleSmall = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
        lineHeightStyle = Tight,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.1.sp,
    ),
)

/** Measurements, and nothing else. */
object Telemetry {
    val readout = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    )
    val readoutSmall = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.sp,
    )
    /** A status lamp: a state name, set wide because it is a label on a panel. */
    val lamp = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.9.sp,
    )
    val code = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp,
    )
}
