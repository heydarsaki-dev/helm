package dev.helm.hermes

import org.json.JSONArray
import org.json.JSONObject

/*
 * Wire types for the Hermes API server (gateway `api_server` platform).
 *
 * Field names here are the gateway's, not ours: sessions live in its SessionDB
 * and are shared with every other Hermes frontend, so we read them rather than
 * inventing a private shape.
 */

/** A persisted conversation, as `GET /api/sessions` returns it. */
data class Session(
    val id: String,
    val title: String? = null,
    val preview: String? = null,
    val source: String? = null,
    val model: String? = null,
    val startedAt: Double? = null,
    val endedAt: Double? = null,
    val endReason: String? = null,
    val messageCount: Int = 0,
    val toolCallCount: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val estimatedCostUsd: Double? = null,
    val actualCostUsd: Double? = null,
    val apiCallCount: Int = 0,
    val parentSessionId: String? = null,
    val lastActive: Double? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val hidden: Boolean = false,
) {
    val totalTokens: Int get() = inputTokens + outputTokens
    val costUsd: Double? get() = actualCostUsd ?: estimatedCostUsd

    /** Falls back through the three things a session can be called, in order. */
    fun displayName(): String =
        title?.takeIf { it.isNotBlank() }
            ?: preview?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull()?.take(72)
            ?: id

    fun activityAt(): Double = lastActive ?: endedAt ?: startedAt ?: 0.0

    companion object {
        fun from(o: JSONObject) = Session(
            id = o.optString("id"),
            title = o.optStringOrNull("title"),
            preview = o.optStringOrNull("preview"),
            source = o.optStringOrNull("source"),
            model = o.optStringOrNull("model"),
            startedAt = o.optDoubleOrNull("started_at"),
            endedAt = o.optDoubleOrNull("ended_at"),
            endReason = o.optStringOrNull("end_reason"),
            messageCount = o.optInt("message_count"),
            toolCallCount = o.optInt("tool_call_count"),
            inputTokens = o.optInt("input_tokens"),
            outputTokens = o.optInt("output_tokens"),
            reasoningTokens = o.optInt("reasoning_tokens"),
            estimatedCostUsd = o.optDoubleOrNull("estimated_cost_usd"),
            actualCostUsd = o.optDoubleOrNull("actual_cost_usd"),
            apiCallCount = o.optInt("api_call_count"),
            parentSessionId = o.optStringOrNull("parent_session_id"),
            lastActive = o.optDoubleOrNull("last_active"),
            pinned = o.optBoolean("pinned"),
            archived = o.optBoolean("archived"),
            hidden = o.optBoolean("hidden"),
        )
    }
}

/** One row of session history. `displayKind == "hidden"` is compaction scaffolding. */
data class Message(
    val id: String? = null,
    val role: String = "user",
    val content: String = "",
    val toolName: String? = null,
    val timestamp: Double? = null,
    val tokenCount: Int = 0,
    val reasoning: String? = null,
    val hidden: Boolean = false,
) {
    companion object {
        fun from(o: JSONObject) = Message(
            id = o.optStringOrNull("id"),
            role = o.optString("role", "user"),
            content = o.optString("content"),
            toolName = o.optStringOrNull("tool_name"),
            timestamp = o.optDoubleOrNull("timestamp"),
            tokenCount = o.optInt("token_count"),
            reasoning = o.optStringOrNull("reasoning") ?: o.optStringOrNull("reasoning_content"),
            hidden = o.optString("display_kind") == "hidden",
        )
    }
}

data class Usage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
) {
    companion object {
        fun from(o: JSONObject?) = Usage(
            inputTokens = o?.optInt("input_tokens") ?: 0,
            outputTokens = o?.optInt("output_tokens") ?: 0,
            totalTokens = o?.optInt("total_tokens")
                ?: ((o?.optInt("input_tokens") ?: 0) + (o?.optInt("output_tokens") ?: 0)),
        )
    }
}

/** Pollable run state from `GET /v1/runs/{run_id}`. */
data class RunRecord(
    val runId: String,
    val status: String,
    val sessionId: String? = null,
    val model: String? = null,
    val output: String? = null,
    val error: String? = null,
    val usage: Usage? = null,
    val lastEvent: String? = null,
    val createdAt: Double? = null,
    val updatedAt: Double? = null,
) {
    companion object {
        fun from(o: JSONObject) = RunRecord(
            runId = o.optString("run_id"),
            status = o.optString("status"),
            sessionId = o.optStringOrNull("session_id"),
            model = o.optStringOrNull("model"),
            output = o.optStringOrNull("output"),
            error = o.optStringOrNull("error"),
            usage = if (o.has("usage")) Usage.from(o.optJSONObject("usage")) else null,
            lastEvent = o.optStringOrNull("last_event"),
            createdAt = o.optDoubleOrNull("created_at"),
            updatedAt = o.optDoubleOrNull("updated_at"),
        )
    }
}

/**
 * What the person is looking at right now. Derived, never stored — the run's
 * lifecycle is the gateway's to own, and mirroring it would mean guessing when
 * to invalidate the copy.
 */
enum class RunPhase {
    /** Nothing in flight. */
    Idle,

    /** POST /v1/runs accepted, SSE not yet producing. */
    Starting,

    /** The agent is thinking, streaming, or calling tools. */
    Working,

    /** A tool asked a human a question. Everything else is blocked on this. */
    Held,

    /** /stop accepted; the agent is unwinding. */
    Stopping,

    /** Finished cleanly this session; the transcript is on disk. */
    Settled,

    /** Finished badly — provider error, auth, transport. */
    Faulted,
}

enum class ApprovalChoice(val wire: String, val verb: String) {
    Once("once", "Allow"),
    Session("session", "Allow for this run"),
    Always("always", "Always allow"),
    Deny("deny", "Deny");

    companion object {
        fun fromWire(s: String) = entries.firstOrNull { it.wire == s }
    }
}

/**
 * One SSE frame from `GET /v1/runs/{run_id}/events`.
 *
 * The gateway emits a flat `event` discriminant with no per-event envelope, so
 * [Other] exists so a Hermes that grows a new event degrades to a quiet line in
 * the activity log instead of a crash.
 */
sealed interface RunEvent {
    val at: Double

    data class Delta(val delta: String, override val at: Double) : RunEvent
    data class Thinking(val text: String, override val at: Double) : RunEvent
    data class ToolStarted(val tool: String?, val preview: String?, override val at: Double) : RunEvent
    data class ToolFinished(
        val tool: String?,
        val seconds: Double,
        val failed: Boolean,
        override val at: Double,
    ) : RunEvent
    data class Subagent(
        val id: String?,
        val goal: String?,
        val summary: String?,
        val done: Boolean,
        val seconds: Double?,
        override val at: Double,
    ) : RunEvent
    data class ApprovalAsked(
        val command: String?,
        val choices: List<ApprovalChoice>,
        override val at: Double,
    ) : RunEvent
    data class ApprovalAnswered(val choice: String, override val at: Double) : RunEvent
    data class Steered(override val at: Double) : RunEvent
    data class Finished(val output: String, val usage: Usage?, override val at: Double) : RunEvent
    data class Failed(val error: String, override val at: Double) : RunEvent
    data class Cancelled(override val at: Double) : RunEvent
    data class Other(val name: String, override val at: Double) : RunEvent

    companion object {
        fun parse(o: JSONObject): RunEvent {
            val at = o.optDouble("timestamp")
            return when (val e = o.optString("event")) {
                "message.delta" -> Delta(o.optString("delta"), at)
                "reasoning.available" -> Thinking(o.optString("text"), at)
                "tool.started" -> ToolStarted(o.optStringOrNull("tool"), o.optStringOrNull("preview"), at)
                "tool.completed" -> ToolFinished(
                    tool = o.optStringOrNull("tool"),
                    seconds = o.optDouble("duration"),
                    failed = o.optBoolean("error"),
                    at = at,
                )
                "subagent.start" -> Subagent(
                    id = o.optStringOrNull("subagent_id") ?: o.optStringOrNull("child_session_id"),
                    goal = o.optStringOrNull("goal"),
                    summary = null,
                    done = false,
                    seconds = null,
                    at = at,
                )
                "subagent.complete" -> Subagent(
                    id = o.optStringOrNull("subagent_id") ?: o.optStringOrNull("child_session_id"),
                    goal = o.optStringOrNull("goal"),
                    summary = o.optStringOrNull("summary"),
                    done = true,
                    seconds = o.optDoubleOrNull("duration_seconds"),
                    at = at,
                )
                "approval.request" -> ApprovalAsked(
                    command = o.optStringOrNull("command"),
                    choices = o.optJSONArray("choices").toStringList()
                        .mapNotNull(ApprovalChoice::fromWire)
                        .ifEmpty { listOf(ApprovalChoice.Once, ApprovalChoice.Deny) },
                    at = at,
                )
                "approval.responded" -> ApprovalAnswered(o.optString("choice"), at)
                "run.steered" -> Steered(at)
                "run.completed" -> Finished(
                    output = o.optString("output"),
                    usage = if (o.has("usage")) Usage.from(o.optJSONObject("usage")) else null,
                    at = at,
                )
                "run.failed" -> Failed(o.optString("error", "The run failed."), at)
                "run.cancelled" -> Cancelled(at)
                else -> Other(e, at)
            }
        }
    }
}

/* ---- org.json helpers, kept out of the models ---- */

internal fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key)
    return v.ifBlank { null }
}

internal fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key).takeIf { !it.isNaN() }
}

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
