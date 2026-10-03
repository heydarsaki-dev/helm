package dev.helm.hermes.net

import dev.helm.hermes.Message
import dev.helm.hermes.RunEvent
import dev.helm.hermes.RunRecord
import dev.helm.hermes.Session
import dev.helm.hermes.optStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * A failure the interface can say something useful about.
 *
 * The gateway wraps every error as `{"error":{"message","type","code"}}`. We
 * keep the code because it is the part that tells a person what to *do*:
 * `gateway_auth_failed` means fix the key, `run_not_found` means the gateway
 * restarted, a refused connection means the gateway is not running.
 */
class GatewayError(
    val status: Int,
    val code: String?,
    override val message: String,
    val transport: Transport = Transport.Http,
) : IOException(message) {

    enum class Transport { Http, Connect, Read }

    /** One line of plain direction, written for someone holding the phone. */
    fun guidance(): String = when {
        transport == Transport.Connect ->
            "Nothing is listening. In Termux, enable the api_server platform in " +
                "~/.hermes/config.yaml (enabled: true, extra.key of 16+ characters), " +
                "then run `hermes gateway run`."
        status == 401 || status == 403 ->
            "The gateway rejected this key. Copy API_SERVER_KEY from the gateway config into Helm."
        status == 404 && code?.contains("run") == true ->
            "That run is gone. The gateway restarts forget in-flight runs — reload the session."
        status == 409 ->
            "The agent is no longer waiting for that."
        status == 503 ->
            "The gateway is up but its session store is not ready yet."
        else -> message
    }

    companion object {
        fun connect(detail: String) = GatewayError(0, null, detail, Transport.Connect)
        fun read(detail: String) = GatewayError(0, null, detail, Transport.Read)
    }
}

/**
 * The Hermes API server, spoken over HTTP.
 *
 * `hermes gateway run` exposes an OpenAI-compatible surface plus Hermes-native
 * run control. We use the run surface rather than `/v1/chat/completions`
 * because it is the only one that exposes tool progress, approvals, steering
 * and interruption — which is the whole reason to have a phone app instead of
 * pointing Open WebUI at the port.
 *
 * Everything here is blocking `HttpURLConnection` on an IO dispatcher. That is
 * a deliberate trade: it keeps the APK free of OkHttp/Retrofit and the build
 * free of version alignment, and the gateway is on loopback, so the connection
 * cost that library would save is not being paid anyway.
 */
class Gateway(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private fun url(path: String) = URL(baseUrl.trimEnd('/') + path)

    private fun open(path: String, method: String, accept: String): HttpURLConnection {
        val conn = url(path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String =
        (conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() }

    private fun gatewayError(status: Int, raw: String): GatewayError {
        val parsed = runCatching { JSONObject(raw).optJSONObject("error") }.getOrNull()
        return GatewayError(
            status = status,
            code = parsed?.optStringOrNull("code"),
            message = parsed?.optStringOrNull("message")
                ?: raw.take(400).ifBlank { "The gateway returned HTTP $status." },
        )
    }

    private fun call(path: String, method: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = open(path, method, "application/json")
            try {
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = conn.responseCode
                val raw = if (status in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    readBody(conn)
                }
                if (status !in 200..299) throw gatewayError(status, raw)
                if (raw.isBlank()) JSONObject() else JSONObject(raw)
            } catch (e: GatewayError) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw GatewayError.read("The gateway did not answer in time.")
            } catch (e: IOException) {
                throw GatewayError.connect(e.message ?: "Could not reach the gateway.")
            } finally {
                conn.disconnect()
            }
        }

    /* ---------------------------------------------------------------- health */

    /** `GET /v1/capabilities` — the honest reachability probe: it enforces auth. */
    suspend fun capabilities(): JSONObject = call("/v1/capabilities", "GET")

    /** `GET /v1/models` — the virtual `hermes-agent` alias plus configured routes. */
    suspend fun models(): List<String> {
        val o = call("/v1/models", "GET")
        val arr = o.optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optStringOrNull("id")
        }.distinct()
    }

    /* -------------------------------------------------------------- sessions */

    /** `GET /api/sessions` — newest activity first, the order a person wants. */
    suspend fun sessions(limit: Int = 60, offset: Int = 0): List<Session> {
        val o = call("/api/sessions?limit=$limit&offset=$offset", "GET")
        val arr = o.optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let(Session::from)
        }
    }

    /** `POST /api/sessions` — create the row up front so the run lands in history. */
    suspend fun createSession(title: String? = null, model: String? = null): Session {
        // "helm" is not in the gateway's allowed source set and would be
        // normalised to this anyway; ask for the truth instead of a nicer lie.
        val body = JSONObject().apply {
            put("source", "api_server")
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
        }
        val o = call("/api/sessions", "POST", body)
        return o.optJSONObject("session")?.let(Session::from)
            ?: throw GatewayError(0, null, "The gateway created a session but returned no record of it.")
    }

    /** `GET /api/sessions/{id}/messages?order=oldest` — the full transcript, in order. */
    suspend fun messages(sessionId: String, limit: Int = 500): List<Message> {
        val o = call(
            "/api/sessions/${enc(sessionId)}/messages?order=oldest&limit=$limit",
            "GET",
        )
        val arr = o.optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let(Message::from)
        }
    }

    /** `PATCH /api/sessions/{id}` — title, and the three flags the sidebar owns. */
    suspend fun updateSession(
        sessionId: String,
        title: String? = null,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        unread: Boolean? = null,
    ): Session {
        val body = JSONObject().apply {
            title?.let { put("title", it) }
            pinned?.let { put("pinned", it) }
            archived?.let { put("archived", it) }
            unread?.let { put("unread", it) }
        }
        if (body.length() == 0) return session(sessionId)
        val o = call("/api/sessions/${enc(sessionId)}", "PATCH", body)
        return o.optJSONObject("session")?.let(Session::from)
            ?: throw GatewayError(0, null, "The gateway accepted the update but returned no session.")
    }

    suspend fun session(sessionId: String): Session =
        call("/api/sessions/${enc(sessionId)}", "GET")
            .optJSONObject("session")?.let(Session::from)
            ?: throw GatewayError(404, "session_not_found", "That session is not on the gateway.")

    suspend fun deleteSession(sessionId: String) {
        call("/api/sessions/${enc(sessionId)}", "DELETE")
    }

    /* ------------------------------------------------------------------ runs */

    /**
     * `POST /v1/runs` — hand the agent a turn and get a handle back immediately.
     *
     * The session id is passed explicitly rather than left to the gateway: a
     * session created up front is the one whose transcript we can read back
     * afterwards, which is the whole reason the list stays useful.
     */
    suspend fun startRun(
        input: String,
        sessionId: String,
        model: String? = null,
        provider: String? = null,
    ): String {
        val body = JSONObject().apply {
            put("input", input)
            put("session_id", sessionId)
            model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
            provider?.takeIf { it.isNotBlank() }?.let { put("provider", it) }
        }
        val o = call("/v1/runs", "POST", body)
        return o.optStringOrNull("run_id")
            ?: throw GatewayError(0, null, "The gateway accepted the run but returned no run id.")
    }

    suspend fun run(runId: String): RunRecord =
        call("/v1/runs/${enc(runId)}", "GET").let(RunRecord::from)

    /** `POST /v1/runs/{id}/approval` — answer a tool that stopped to ask. */
    suspend fun approve(runId: String, choice: String, resolveAll: Boolean = false) {
        val body = JSONObject().apply {
            put("choice", choice)
            if (resolveAll) put("all", true)
        }
        call("/v1/runs/${enc(runId)}/approval", "POST", body)
    }

    /** `POST /v1/runs/{id}/steer` — change the agent's mind without a new turn. */
    suspend fun steer(runId: String, text: String) {
        call(
            "/v1/runs/${enc(runId)}/steer", "POST",
            JSONObject().put("input", text),
        )
    }

    /** `POST /v1/runs/{id}/stop` — ask the agent to unwind. */
    suspend fun stop(runId: String) {
        call("/v1/runs/${enc(runId)}/stop", "POST", JSONObject())
    }

    /**
     * `GET /v1/runs/{id}/events` — the agent's whole life as it happens.
     *
     * The gateway writes `data: {json}` frames with the event name inside the
     * JSON, and `: keepalive` comments every 30s. The read timeout is set past
     * the keepalive interval on purpose: a timeout means the run genuinely
     * went quiet, which is a different thing from the stream ending, and the
     * caller treats them differently.
     *
     * One reader per run, and Helm is it. The gateway backs a run with a single
     * asyncio queue, so a second consumer would silently starve this one — which
     * is why nothing in the app opens a second stream for the same run, and why
     * a reconnect resumes on status polling rather than by reopening the feed.
     */
    fun events(runId: String, readTimeoutSeconds: Int = 95): Flow<RunEvent> =
        runEvents(runId, readTimeoutSeconds)

    private fun runEvents(runId: String, readTimeoutSeconds: Int): Flow<RunEvent> = flow {
        val conn = open("/v1/runs/${enc(runId)}/events", "GET", "text/event-stream")
        try {
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.readTimeout = readTimeoutSeconds * 1000
            val status = conn.responseCode
            if (status !in 200..299) throw gatewayError(status, readBody(conn))

            BufferedReader(conn.inputStream.reader(Charsets.UTF_8)).use { reader ->
                val data = StringBuilder()
                while (true) {
                    val line = try {
                        reader.readLine()
                    } catch (e: SocketTimeoutException) {
                        throw GatewayError.read("The gateway stopped sending events.")
                    } ?: break

                    when {
                        // A comment frame. `: keepalive` holds the socket open;
                        // `: stream closed` is the gateway saying the run ended.
                        line.startsWith(":") ->
                            if (line.contains("stream closed")) return@use

                        // Blank line ends a frame.
                        line.isEmpty() ->
                            if (data.isNotEmpty()) {
                                emit(RunEvent.parse(JSONObject(data.toString())))
                                data.setLength(0)
                            }

                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trimStart())
                        }

                        // `event:`, `id:` and `retry:` carry nothing Helm needs —
                        // the discriminant lives inside the JSON payload.
                        else -> Unit
                    }
                }
                if (data.isNotEmpty()) emit(RunEvent.parse(JSONObject(data.toString())))
            }
        } catch (e: GatewayError) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw GatewayError.read("The event stream went quiet.")
        } catch (e: IOException) {
            throw GatewayError.connect(e.message ?: "Lost the event stream.")
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
    }
}
