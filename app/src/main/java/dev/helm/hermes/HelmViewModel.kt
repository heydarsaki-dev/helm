package dev.helm.hermes

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.compose.foundation.layout.size
import androidx.lifecycle.viewModelScope
import dev.helm.hermes.data.Store
import dev.helm.hermes.data.ThemeMode
import dev.helm.hermes.net.Gateway
import dev.helm.hermes.net.GatewayError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ view state */

/** How Helm is doing at reaching the gateway, without guessing. */
sealed interface Link {
    data object Unknown : Link
    data object Checking : Link
    data class Reachable(val version: String, val model: String) : Link
    data class Unreachable(val error: GatewayError) : Link
}

/** One row of the transcript, already decided what kind of thing it is. */
sealed interface Entry {
    val at: Double

    /** Prose somebody said — or the agent said back. */
    data class Say(val text: String, val from: Who, override val at: Double) : Entry

    /** The agent's reasoning. Held behind a disclosure because it is not the answer. */
    data class Think(val text: String, override val at: Double) : Entry

    /** A contiguous stretch of tool calls, collapsed to one expandable band. */
    data class Work(val calls: List<ToolCall>, override val at: Double) : Entry
}

enum class Who { You, Agent, Tool }

data class ToolCall(
    val name: String,
    val preview: String? = null,
    val seconds: Double? = null,
    val failed: Boolean = false,
)

data class SubagentRun(
    val id: String?,
    val goal: String?,
    val summary: String?,
    val done: Boolean,
    val seconds: Double?,
)

data class ApprovalPrompt(
    val command: String?,
    val choices: List<ApprovalChoice>,
)

/** Everything about the run in flight. Null when nothing is running. */
data class Live(
    val runId: String,
    val phase: RunPhase = RunPhase.Starting,
    val startedAt: Long = System.currentTimeMillis(),
    val streamed: String = "",
    val thought: String = "",
    val calls: List<ToolCall> = mutableListOf(),
    val subagents: List<SubagentRun> = emptyList(),
    val approval: ApprovalPrompt? = null,
    val usage: Usage? = null,
    val error: String? = null,
    val answer: String? = null,
) {
    val busy: Boolean
        get() = phase == RunPhase.Starting || phase == RunPhase.Working ||
            phase == RunPhase.Held || phase == RunPhase.Stopping

    fun closedCalls(): List<ToolCall> = calls.filter { it.name.isNotBlank() }

}

/** A transient line at the top of the screen: an error with a way out of it. */
data class Banner(val text: String, val detail: String? = null, val tone: Tone) {
    enum class Tone { Info, Fault, Held }
}

class HelmViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    var link: Link by mutableStateOf(Link.Unknown)
        private set

    var sessions: List<Session> by mutableStateOf(emptyList())
        private set

    var sessionsBusy: Boolean by mutableStateOf(false)
        private set

    var openSessionId: String? by mutableStateOf(store.lastSessionId)
        private set

    var entries: List<Entry> by mutableStateOf(emptyList())
        private set

    var transcriptBusy: Boolean by mutableStateOf(false)
        private set

    var live: Live? by mutableStateOf(null)
        private set

    var models: List<String> by mutableStateOf(emptyList())
        private set

    var banner: Banner? by mutableStateOf(null)
        private set

    var showThinking: Boolean by mutableStateOf(store.showThinking)
        private set

    /** Drives the elapsed readout while a run is live. */
    var now: Long by mutableStateOf(System.currentTimeMillis())
        private set

    /** The few preferences Settings needs to read back into its fields. */
    val prefs: Store get() = store

    val themeMode: ThemeMode get() = store.themeMode
    val keepScreenOn: Boolean get() = store.keepScreenOn
    val configured: Boolean get() = store.configured

    private var gateway: Gateway = Gateway(store.baseUrl, store.apiKey)
    private var runJob: Job? = null
    private var clockJob: Job? = null
    private var openJob: Job? = null

    /* ------------------------------------------------------------- lifecycle */

    init {
        viewModelScope.launch {
            probe(announce = false)
            if (store.configured && store.resumeLast) {
                store.lastSessionId?.let { openSession(it) }
            }
        }
    }

    fun gateway(): Gateway = gateway

    private fun rebind() {
        gateway = Gateway(store.baseUrl, store.apiKey)
    }

    /* ------------------------------------------------------------ reachability */

    /**
     * Ask `/v1/capabilities` rather than `/health`: it is the one route that
     * proves both that something is listening *and* that the key is accepted,
     * so a single check can tell "gateway is down" apart from "key is wrong".
     */
    fun probe(announce: Boolean = true) {
        viewModelScope.launch {
            link = Link.Checking
            try {
                val caps = gateway.capabilities()
                val model = caps.optString("model", "hermes-agent")
                link = Link.Reachable(model, caps.optString("platform", "hermes-agent"))
                banner = null
                if (announce) refreshSessions()
                gateway.models().let { if (it.isNotEmpty()) models = it }
            } catch (e: GatewayError) {
                link = Link.Unreachable(e)
                if (announce) banner = Banner("Can't reach the gateway", e.guidance(), Banner.Tone.Fault)
            }
        }
    }

    fun dismissBanner() {
        banner = null
    }

    /* ---------------------------------------------------------------- sessions */

    fun refreshSessions() {
        viewModelScope.launch {
            sessionsBusy = true
            try {
                sessions = gateway.sessions()
            } catch (e: GatewayError) {
                banner = Banner("Couldn't load sessions", e.guidance(), Banner.Tone.Fault)
            } finally {
                sessionsBusy = false
            }
        }
    }

    /** Visible conversations only — archiving is the gateway's flag, not ours. */
    val visibleSessions: List<Session>
        get() = sessions.filter { !it.archived && !it.hidden }

    val archivedSessions: List<Session>
        get() = sessions.filter { it.archived && !it.hidden }

    fun openSession(id: String) {
        if (openSessionId == id && entries.isNotEmpty()) return
        openSessionId = id
        store.lastSessionId = id
        openJob?.cancel()
        openJob = viewModelScope.launch {
            transcriptBusy = true
            try {
                entries = gateway.messages(id)
                    .filterNot { it.hidden }
                    .map { it.toEntry() }
                markRead(id)
            } catch (e: GatewayError) {
                entries = emptyList()
                banner = Banner("Couldn't open that session", e.guidance(), Banner.Tone.Fault)
            } finally {
                transcriptBusy = false
            }
        }
    }

    private fun Message.toEntry(): Entry = when {
        role == "user" -> Entry.Say(content, Who.You, timestamp ?: 0.0)
        role == "tool" -> Entry.Say(content, Who.Tool, timestamp ?: 0.0)
        !reasoning.isNullOrBlank() && content.isBlank() ->
            Entry.Think(reasoning, timestamp ?: 0.0)

        else -> Entry.Say(content, Who.Agent, timestamp ?: 0.0)
    }

    /** Clearing the unread dot is a courtesy, never a blocking round trip. */
    private fun markRead(id: String) {
        viewModelScope.launch {
            runCatching { gateway.updateSession(id, unread = false) }
        }
    }

    fun startNewSession() {
        openJob?.cancel()
        openSessionId = null
        entries = emptyList()
        live = null
        store.lastSessionId = null
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch {
            try {
                gateway.updateSession(id, title = title)
                refreshSessions()
            } catch (e: GatewayError) {
                banner = Banner("Couldn't rename", e.guidance(), Banner.Tone.Fault)
            }
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            try {
                gateway.updateSession(id, pinned = pinned)
                refreshSessions()
            } catch (e: GatewayError) {
                banner = Banner("Couldn't update that pin", e.guidance(), Banner.Tone.Fault)
            }
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            try {
                gateway.updateSession(id, archived = archived)
                refreshSessions()
            } catch (e: GatewayError) {
                banner = Banner("Couldn't archive that", e.guidance(), Banner.Tone.Fault)
            }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            try {
                gateway.deleteSession(id)
                if (openSessionId == id) startNewSession()
                refreshSessions()
            } catch (e: GatewayError) {
                banner = Banner("Couldn't delete that", e.guidance(), Banner.Tone.Fault)
            }
        }
    }

    /* -------------------------------------------------------------------- runs */

    /**
     * Send a turn.
     *
     * The session row is created first and its id handed to the run, so the
     * gateway files the transcript under a row we already know about — that is
     * what lets the session list survive the app being closed mid-run.
     */
    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || live?.busy == true) return

        viewModelScope.launch {
            var sessionId = openSessionId
            if (sessionId == null) {
                try {
                    val created = gateway.createSession(model = store.model.takeIf { it.isNotBlank() })
                    sessionId = created.id
                    openSessionId = sessionId
                    store.lastSessionId = sessionId
                    sessions = listOf(created) + sessions
                } catch (e: GatewayError) {
                    banner = Banner("Couldn't open a session", e.guidance(), Banner.Tone.Fault)
                    return@launch
                }
            }

            // Show the turn immediately. The gateway's transcript is the record
            // of truth; this is the phone's own echo of what you just sent.
            entries = entries + Entry.Say(clean, Who.You, now / 1000.0)
            startRun(clean, sessionId)
        }
    }

    private fun startRun(input: String, sessionId: String) {
        viewModelScope.launch {
            val runId = try {
                gateway.startRun(
                    input = input,
                    sessionId = sessionId,
                    model = store.model.takeIf { it.isNotBlank() },
                )
            } catch (e: GatewayError) {
                banner = Banner("The agent didn't start", e.guidance(), Banner.Tone.Fault)
                return@launch
            }
            live = Live(runId = runId, phase = RunPhase.Working)
            startClock()
            follow(runId)
        }
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (live?.busy == true) {
                now = System.currentTimeMillis()
                delay(250)
            }
            now = System.currentTimeMillis()
        }
    }

    /**
     * Follow one run's event stream until the gateway closes it.
     *
     * Every branch re-checks the run id before touching state. Cancelling one
     * collector to start the next is not synchronised — the old job's `finally`
     * runs after the new one has already installed itself, and without the
     * guard the abandoned run would clear the live block of the run that
     * replaced it.
     */
    private fun follow(runId: String) {
        runJob?.cancel()
        runJob = viewModelScope.launch {
            try {
                gateway.events(runId).collect { event -> if (live?.runId == runId) apply(event) }
                // A clean end-of-stream is the gateway closing after a terminal
                // event. Anything else left the phase untouched.
                if (live?.runId == runId && live?.busy == true) {
                    live = live?.copy(phase = RunPhase.Settled)
                }
            } catch (e: GatewayError) {
                if (live?.runId != runId) return@launch
                val stillLive = live?.busy == true
                live = live?.copy(
                    phase = if (stillLive) RunPhase.Working else RunPhase.Faulted,
                    error = e.guidance(),
                )
                if (!stillLive) banner = Banner("Lost the run", e.guidance(), Banner.Tone.Fault)
            } finally {
                if (live?.runId == runId) settle()
            }
        }
    }

    /**
     * Fold one SSE frame into the live run.
     *
     * `tool.completed` carries no call id, so an open call is matched by name
     * from the back of the list — which is how a sequential agent actually
     * behaves, and how a parallel one still lands on the right row often
     * enough that a mis-closure is a grey tick rather than a wrong claim.
     */
    private fun apply(event: RunEvent) {
        val current = live ?: return
        val next = when (event) {
            is RunEvent.Delta -> current.copy(
                streamed = current.streamed + event.delta,
                phase = RunPhase.Working,
            )

            is RunEvent.Thinking -> current.copy(
                thought = (current.thought + "\n" + event.text).trim(),
            )

            is RunEvent.ToolStarted -> current.copy(
                calls = current.calls + ToolCall(event.tool ?: "tool", event.preview),
            )

            is RunEvent.ToolFinished -> current.closeCall(event)

            is RunEvent.Subagent -> current.copy(
                subagents = current.subagents + SubagentRun(
                    id = event.id,
                    goal = event.goal,
                    summary = event.summary,
                    done = event.done,
                    seconds = event.seconds,
                ),
            )

            is RunEvent.ApprovalAsked -> current.copy(
                phase = RunPhase.Held,
                approval = ApprovalPrompt(event.command, event.choices),
            )

            is RunEvent.ApprovalAnswered -> current.copy(
                approval = null,
                phase = RunPhase.Working,
                answer = event.choice,
            )

            is RunEvent.Steered -> current.copy(phase = RunPhase.Working)

            is RunEvent.Finished -> current.copy(
                phase = RunPhase.Settled,
                usage = event.usage,
                approval = null,
                // The streamed text and the final output are the same answer;
                // the final one is authoritative when the two disagree.
                streamed = event.output.ifBlank { current.streamed },
            )

            is RunEvent.Failed -> current.copy(
                phase = RunPhase.Faulted,
                error = event.error,
                approval = null,
            )

            is RunEvent.Cancelled -> current.copy(
                phase = RunPhase.Settled,
                approval = null,
            )

            is RunEvent.Other -> current
        }
        live = next
    }

    private fun Live.closeCall(event: RunEvent.ToolFinished): Live {
        val calls = calls.toMutableList()
        val index = calls.indexOfLast { it.seconds == null && it.name == event.tool }
            .takeIf { it >= 0 }
            ?: calls.indexOfLast { it.seconds == null }.takeIf { it >= 0 }
            ?: -1
        if (index >= 0) {
            calls[index] = calls[index].copy(seconds = event.seconds, failed = event.failed)
        } else {
            calls.add(ToolCall(event.tool ?: "tool", seconds = event.seconds, failed = event.failed))
        }
        return copy(calls = calls)
    }

    /**
     * The run is over. The gateway's transcript is now the record of truth, so
     * reload it — but a reload that comes back short (the agent was stopped
     * mid-write, or the gateway is already gone) must not be allowed to delete
     * an answer the reader just watched stream in. Anything the reload does not
     * account for is kept.
     */
    private fun settle() {
        clockJob?.cancel()
        val sessionId = openSessionId
        val finished = live
        val phase = finished?.phase
        if (phase == RunPhase.Faulted || phase == RunPhase.Working || phase == RunPhase.Held) {
            // Left hanging by a broken stream — say so rather than pretending.
            live = finished?.copy(phase = RunPhase.Faulted)
        }
        live = null

        if (sessionId == null) return
        val hadEntries = entries.size
        val answer = finished?.streamed?.trim().orEmpty()
        viewModelScope.launch {
            val reloaded = runCatching {
                gateway.messages(sessionId).filterNot { it.hidden }.map { it.toEntry() }
            }.getOrNull()

            val merged = when {
                reloaded == null -> entries
                // The optimistic echo of what you just typed is still the only
                // copy if the gateway has not written it yet.
                reloaded.size < hadEntries ->
                    entries.dropLast((hadEntries - reloaded.size).coerceAtMost(1))
                else -> reloaded
            }
            entries = if (!answer.isEmpty() && !merged.any { it.isAgentAnswer(answer) }) {
                merged + Entry.Say(answer, Who.Agent, System.currentTimeMillis() / 1000.0)
            } else {
                merged
            }
            runCatching { sessions = gateway.sessions() }
        }
    }

    private fun Entry.isAgentAnswer(text: String): Boolean =
        this is Entry.Say && from == Who.Agent && this.text.trim() == text

    fun stopRun() {
        val runId = live?.runId ?: return
        viewModelScope.launch {
            runCatching { gateway.stop(runId) }
            live = live?.copy(phase = RunPhase.Stopping)
        }
    }

    fun answer(choice: ApprovalChoice) {
        val runId = live?.runId ?: return
        viewModelScope.launch {
            try {
                gateway.approve(runId, choice.wire)
                live = live?.copy(approval = null, phase = RunPhase.Working, answer = choice.wire)
            } catch (e: GatewayError) {
                banner = Banner("Couldn't answer the approval", e.guidance(), Banner.Tone.Fault)
            }
        }
    }

    /** Send guidance into a run that is already going, without ending its turn. */
    fun steer(text: String) {
        val runId = live?.runId ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                gateway.steer(runId, clean)
                banner = Banner("Sent", "Steering: $clean", Banner.Tone.Info)
            } catch (e: GatewayError) {
                banner = Banner("Couldn't steer", e.guidance(), Banner.Tone.Fault)
            }
        }
    }

    /* ---------------------------------------------------------------- settings */

    fun saveConnection(url: String, key: String) {
        store.baseUrl = url
        store.apiKey = key
        store.configured = true
        rebind()
        probe()
    }

    fun saveModel(model: String) {
        store.model = model
    }

    fun setThemeMode(mode: ThemeMode) {
        store.themeMode = mode
    }

    fun setKeepScreenOn(value: Boolean) {
        store.keepScreenOn = value
    }

    fun setShowThinking(value: Boolean) {
        store.showThinking = value
    }

    fun setResumeLast(value: Boolean) {
        store.resumeLast = value
    }

    fun loadModelChoices() {
        viewModelScope.launch {
            runCatching { gateway.models() }.onSuccess { if (it.isNotEmpty()) models = it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runJob?.cancel()
        clockJob?.cancel()
    }
}
