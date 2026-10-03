package dev.helm.hermes.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.helm.hermes.ui.theme.HelmShape
import dev.helm.hermes.ui.theme.LocalHelm
import dev.helm.hermes.ui.theme.Telemetry

/**
 * A small markdown reader, sized for what an agent actually writes.
 *
 * A full CommonMark implementation is not wanted here and would be worse: the
 * interesting cases are fenced code, inline code, emphasis, headings, bullets
 * and blockquotes. Anything else renders as the literal text the model sent,
 * which is the honest behaviour for a console.
 */
@Composable
fun AgentText(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    val blocks = remember(text) { splitBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Code -> CodeBlock(block.language, block.body)
                is Block.Prose -> ProseBlock(block.body)
            }
        }
        if (streaming && text.isNotEmpty()) StreamingCaret()
    }
}

private sealed interface Block {
    data class Prose(val body: String) : Block
    data class Code(val language: String, val body: String) : Block
}

/** Split on ``` fences. An unterminated fence still renders as code — models do that. */
private fun splitBlocks(text: String): List<Block> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<Block>()
    var i = 0
    while (i < text.length) {
        val open = text.indexOf("```", i)
        if (open < 0) {
            val tail = text.substring(i)
            if (tail.isNotBlank()) out += Block.Prose(tail.trimEnd())
            break
        }
        val before = text.substring(i, open)
        if (before.isNotBlank()) out += Block.Prose(before.trimEnd())

        val nl = text.indexOf('\n', open)
        if (nl < 0) {
            out += Block.Code("", "")
            break
        }
        val language = text.substring(open + 3, nl).trim().substringBefore(' ')
        val close = text.indexOf("```", nl)
        val body = if (close < 0) text.substring(nl + 1) else text.substring(nl + 1, close)
        out += Block.Code(language, body.trimEnd())
        i = if (close < 0) text.length else close + 3
    }
    return out
}

/**
 * Prose, line by line.
 *
 * Lines are classified rather than reflowed, because an agent's output is
 * already wrapped the way it wants to be read — re-wrapping it to the phone's
 * measure would throw away the model's own line breaks inside lists and tables.
 */
@Composable
private fun ProseBlock(body: String) {
    val c = LocalHelm.current
    val lines = remember(body) { body.lines() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> Unit

                line.trimStart().startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length.coerceIn(1, 4)
                    Spacer(Modifier.width(0.dp))
                    Text(
                        inline(line.trimStart().trimStart('#').trim()),
                        style = when (level) {
                            1 -> MaterialTheme.typography.titleMedium
                            2 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.labelLarge
                        },
                        color = c.text,
                    )
                }

                line.trimStart().startsWith("> ") -> QuoteLine(inline(line.trimStart().removePrefix("> ").trim()))

                isBullet(line) -> BulletLine(inline(line.trimStart().substring(1).trim()))

                else -> {
                    // Gather the run of plain lines into one paragraph.
                    val buf = StringBuilder()
                    while (i < lines.size && lines[i].isNotBlank() &&
                        !isBullet(lines[i]) && !lines[i].trimStart().startsWith("#") &&
                        !lines[i].trimStart().startsWith("> ")
                    ) {
                        if (buf.isNotEmpty()) buf.append('\n')
                        buf.append(lines[i])
                        i++
                    }
                    Text(
                        inline(buf.toString()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.text,
                    )
                    continue
                }
            }
            i++
        }
    }
}

private fun isBullet(line: String) =
    line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") || it.startsWith("+ ") }

@Composable
private fun BulletLine(content: AnnotatedString) {
    val c = LocalHelm.current
    Row {
        Box(Modifier.width(16.dp).padding(top = 9.dp)) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(4.dp)
                    .background(c.textFaint, RoundedCornerShape(1.dp)),
            )
        }
        Text(
            content,
            style = MaterialTheme.typography.bodyLarge,
            color = c.text,
        )
    }
}

@Composable
private fun QuoteLine(content: AnnotatedString) {
    val c = LocalHelm.current
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(c.dataDim),
        )
        Spacer(Modifier.width(10.dp))
        Text(content, style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
    }
}

/**
 * Code. The one surface on the screen with its own background, because code is
 * the one thing that must not be mistaken for prose — and it scrolls sideways
 * rather than wrapping, because a wrapped shell command is a wrong command.
 */
@Composable
private fun CodeBlock(language: String, body: String) {
    val c = LocalHelm.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.panel, HelmShape.notchShape),
    ) {
        if (language.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(c.ruleFaint)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    language,
                    style = Telemetry.lamp,
                    color = c.textFaint,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                body,
                style = Telemetry.code,
                color = c.text,
                softWrap = false,
            )
        }
    }
}

/** The cursor on a streaming answer. It blinks on the working pulse, nothing else does. */
@Composable
private fun StreamingCaret() {
    val c = LocalHelm.current
    val pulse = dev.helm.hermes.ui.theme.Motion.working()
    Box(
        Modifier
            .width(7.dp)
            .height(15.dp)
            .background(c.signal.copy(alpha = 0.35f + 0.65f * pulse.value)),
    )
}

/**
 * Inline spans: `code`, **bold**, *italic*, and links kept as their label so a
 * URL is still readable rather than being hidden behind a shortened anchor.
 */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withSpan(SpanStyle(fontWeight = FontWeight.SemiBold), text.substring(i + 2, end))
                    i = end + 2
                } else {
                    append('*'); i++
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withSpan(SpanStyle(fontFamily = Telemetry.code.fontFamily), text.substring(i + 1, end))
                    i = end + 1
                } else {
                    append('`'); i++
                }
            }

            (text[i] == '*' || text[i] == '_') && i + 1 < text.length &&
                text[i + 1] != text[i] -> {
                val marker = text[i]
                val end = text.indexOf(marker.toString(), i + 1)
                if (end > i + 1) {
                    withSpan(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), text.substring(i + 1, end))
                    i = end + 1
                } else {
                    append(marker); i++
                }
            }

            else -> {
                append(text[i]); i++
            }
        }
    }
}

private fun AnnotatedString.Builder.withSpan(style: SpanStyle, body: String) {
    val start = length
    append(body)
    addStyle(style, start, length)
}
