package dev.helm.hermes.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

/**
 * Two curves and one loop.
 *
 * [enter] is for things that arrive because the person did something — a
 * composer opening, an approval appearing because a tool asked. [settle] is
 * for a value that has stopped moving. [working] is the only animation that
 * runs unprompted, and it runs exactly where the question "what is it doing
 * right now?" is genuinely open: the live rail, and the cursor on a
 * streaming answer.
 */
object Motion {
    private val enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val settle = CubicBezierEasing(0.3f, 0f, 0f, 1f)

    const val EnterMs = 200
    const val SettleMs = 360
    const val WorkingMs = 1500

    val Enter get() = enter
    val Settle get() = settle

    /** Honours the system "remove animations" setting, including the battery saver. */
    val reduced: Boolean get() = !ValueAnimator.areAnimatorsEnabled()

    /** 0f..1f, slow and shallow. Only the rail and the cursor should read it. */
    @Composable
    fun working(): State<Float> {
        if (reduced) return remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
        val transition = rememberInfiniteTransition(label = "working")
        return transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(WorkingMs, easing = settle),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "working-pulse",
        )
    }
}
