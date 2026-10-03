package dev.helm.hermes.ui

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Numbers Helm shows are measurements, so they are formatted as measurements:
 * short, monospaced, no thousands separators. A session list that says
 * "1,234 tokens" is making a small fact feel like a report.
 */
object Format {

    /** "now" / "4m" / "3h" / "2d" / "18 Mar" — compact enough for a list row. */
    fun ago(epochSeconds: Double, nowMillis: Long = System.currentTimeMillis()): String {
        if (epochSeconds <= 0.0) return ""
        val delta = (nowMillis / 1000.0) - epochSeconds
        if (delta < 0) return "now"
        val s = delta.toLong()
        return when {
            s < 45 -> "now"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3600}h"
            s < 7 * 86_400L -> "${s / 86_400}d"
            else -> {
                val then = java.util.Date(epochSeconds.toLong() * 1000)
                val cal = java.util.Calendar.getInstance().apply { time = then }
                val month = cal.getDisplayName(
                    java.util.Calendar.MONTH,
                    java.util.Calendar.SHORT,
                    Locale.getDefault(),
                )
                val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                if (s > 300 * 86_400L) "${cal.get(java.util.Calendar.YEAR)}" else "$day $month"
            }
        }
    }

    /** Elapsed wall time for a live run: "4s", "1m 04s", "2h 03m". */
    fun elapsed(millis: Long): String {
        val total = abs(millis) / 1000
        val h = TimeUnit.SECONDS.toHours(total)
        val m = TimeUnit.SECONDS.toMinutes(total) % 60
        val s = total % 60
        return when {
            h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
            m > 0 -> "${m}m ${s.toString().padStart(2, '0')}s"
            else -> "${s}s"
        }
    }

    /** A tool call's own duration, which is usually sub-second. */
    fun toolSeconds(seconds: Double?): String = when {
        seconds == null -> ""
        seconds < 1 -> "${(seconds * 1000).toInt()}ms"
        seconds < 60 -> String.format(Locale.US, "%.1fs", seconds)
        else -> "${(seconds / 60).toInt()}m ${(seconds % 60).toInt()}s"
    }

    /** Token counts stay short: 842, 1.2k, 18k, 1.4M. */
    fun tokens(n: Int): String = when {
        n < 0 -> "0"
        n < 1_000 -> n.toString()
        n < 10_000 -> String.format(Locale.US, "%.1fk", n / 1000.0)
        n < 1_000_000 -> "${n / 1000}k"
        else -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    }

    /**
     * Cost is the number people actually watch, so it never rounds to zero:
     * sub-cent runs show their real value and only grow past $1.
     */
    fun cost(usd: Double?): String = when {
        usd == null || usd <= 0.0 -> ""
        usd < 0.01 -> String.format(Locale.US, "$%.4f", usd)
        usd < 1.0 -> String.format(Locale.US, "$%.3f", usd)
        else -> String.format(Locale.US, "$%.2f", usd)
    }

    /** Wall-clock time of day for a transcript stamp. */
    fun clock(epochSeconds: Double): String {
        if (epochSeconds <= 0.0) return ""
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = (epochSeconds * 1000).toLong()
        }
        return String.format(
            Locale.US,
            "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
        )
    }

    /** First line of an agent answer, for a session row preview. */
    fun preview(text: String?, max: Int = 96): String {
        val line = text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }
}
