# Helm

A native Android client for a [Hermes Agent](https://github.com/NousResearch) gateway
running on the same phone, usually inside Termux + proot.

Hermes already speaks HTTP. `hermes gateway run` exposes an OpenAI-compatible API
plus a Hermes-native run surface on `127.0.0.1:8642`. Helm is a client for that
run surface — the one that streams tool calls, asks for approval, accepts
steering and can be stopped. Pointing a generic chat UI at the port would give
you the answers and none of the supervision.

---

## What it does

| Screen | What it answers |
|---|---|
| **Sessions** | Which piece of work is this, and is it still moving? |
| **Run** | What is the agent doing *right now*, and what does it need from me? |
| **Settings** | Can Helm reach the gateway, and how do I start it? |

- **Live streaming.** `POST /v1/runs` hands back a run id immediately;
  `GET /v1/runs/{id}/events` is an SSE stream of `message.delta`,
  `tool.started`/`tool.completed`, `reasoning.available`, subagent lifecycle and
  terminal events. Helm renders all of it as it arrives.
- **Approvals.** When a tool stops to ask, the gateway emits `approval.request`
  with the exact command and the choices it will actually accept. Helm shows
  those buttons — not a fixed yes/no — and resolves them with
  `POST /v1/runs/{id}/approval`.
- **Steering.** The composer changes job when the agent is working: the same box
  sends a turn when idle and steers the running turn when live.
- **Stopping.** `POST /v1/runs/{id}/stop`, with the button in the bar for as long
  as the run lasts.
- **Sessions.** `GET /api/sessions` for the list, `…/messages` for the
  transcript, `PATCH` for title/pin/archive. Every conversation lives in the
  gateway's SessionDB and is shared with any other Hermes frontend — Helm keeps
  no copy of anything.

Nothing is written to the phone except the gateway address, the key, and two
display preferences.

---

## Getting it running

### 1. Turn on the gateway's API server

In Termux:

```sh
hermes gateway run
```

The `api_server` platform is **off by default**, and the gateway refuses to start
it without a key of at least 16 characters — on loopback as much as anywhere
else. Turn it on in `~/.hermes/config.yaml`:

```yaml
platforms:
  api_server:
    enabled: true
    extra:
      key: choose-a-long-random-secret
```

Override the bind with `API_SERVER_HOST` / `API_SERVER_PORT` if you need to, or
with `platforms.api_server.extra.{host,port}`. `API_SERVER_KEY` works as an
environment variable instead of the config key.

A stock Hermes install that has only ever run on Telegram or the CLI has no
`api_server` block at all, so this is the step people miss.

### 2. Point Helm at it

Open **Settings**, enter `http://127.0.0.1:8642` and the same key, and press
**Save and test**. The app probes `/v1/capabilities`, which is the one route
that proves both that something is listening and that the key is accepted — so
it can tell "gateway is down" apart from "key is wrong".

### The other surface, if you outgrow this

`hermes serve` (or `hermes dashboard`) brings up a richer JSON-RPC WebSocket at
`ws://127.0.0.1:9119/api/ws` — around 140 methods, session forking, branching,
plans, PTY, images. That is what the shipped desktop app speaks, and it is the
right target for a client that wants everything.

Helm does not use it, on purpose. That surface needs a WebSocket client, a
session-token or password dance (`GET /api/status` tells you which mode is
active), and care with the `Host` header — the server rejects requests whose
`Host` does not match what it is bound to, which is a DNS-rebinding guard that
turns a typo into an opaque HTTP 400. `/v1/runs` over HTTP and SSE gives Helm
every capability this app actually uses — streaming, tool progress, approvals,
steering, stopping, session history — with no extra machinery and nothing to go
stale behind a version negotiation.

---

## Design

**Subject.** A control desk. You are watching a process work through a small
window, which argues for a cool low-chroma ground so the only thing that ever
looks *lit* is the agent actually doing something.

**Palette.** A blue-shifted graphite ramp that never reaches pure black
(`#0B0F14` ground) and exactly one saturated colour: a sodium-lamp amber
(`#FF8A3D`) reserved for live state. Four supporting hues carry meaning and
nothing else — cyan for settled measurements, moss for a clean tool, amber for
held work, red for failure. Day mode is the same instrument read in daylight:
cool paper, and a deeper burnt orange so the lamp survives on white.

**Type.** Three voices with distinct jobs. [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk)
labels and titles — it is a signage face. [IBM Plex Sans](https://fonts.google.com/specimen/IBM+Plex+Sans)
carries prose, the agent's answers and what you type. [IBM Plex Mono](https://fonts.google.com/specimen/IBM+Plex+Mono)
carries *measurements only*: counts, durations, costs, timestamps, status
lamps, tool names, paths. It is never decoration and never sits above a heading.
The variable axes are shipped as two files, not nine.

**The one bold thing.** The live trace rail: a 3dp spine down the gutter of the
transcript whose colour is the run's current phase — faint for history, amber
while the agent works, held-yellow while it is blocked on you, green when the
turn lands. Tool calls write 2dp ticks into the same gutter, one per call,
coloured by outcome. It is the app's launcher mark, which is the same three
marks: one lit, two falling away.

Everything else stays quiet. The transcript is a document, not a chat — the
agent's answer runs the full measure of the screen, because a two-thousand-word
answer about a patch is not a message. Reasoning and tool activity collapse to
one line each. The only card in the app is the approval prompt, because it is
the only object with a decision in it.

**Motion.** One unprompted animation: the working pulse, and it runs only where
"what is it doing right now?" is genuinely open — the rail and the caret on a
streaming answer. Everything else animates because a person did something. The
system "remove animations" setting is honoured.

**Copy.** Plain verbs, sentence case, no filler. Buttons say what happens:
**Save and test**, **Restore newest**, **Allow for this run**. Errors say what
went wrong and what to do about it, never that they are sorry.

---

## Building

```sh
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Requirements: JDK 17+,
Android SDK with platform 35. No third-party runtime libraries — the transport
is `HttpURLConnection` and the JSON is `org.json`, both from the platform.

---

## Layout

```
dev.helm.hermes
├── MainActivity.kt          edge-to-edge host
├── HelmApp.kt               three routes held as state, no nav library
├── HelmViewModel.kt         session list, transcript, live run, settings
├── Model.kt                 wire types, parsed from the gateway's own shapes
├── net/Gateway.kt           HTTP + SSE over HttpURLConnection
├── data/Store.kt            the six preferences Helm keeps
└── ui/
    ├── theme/               colour, type, shape, motion
    ├── components/          rail, lamps, rules, bar, empty states
    ├── sessions/            list, row, actions sheet
    ├── run/                 transcript, live block, approval, composer, markdown
    ├── settings/            connection, model, behaviour, start guide
    └── Format.kt            every measurement Helm prints
```

### The transport, briefly

`HttpURLConnection` on `Dispatchers.IO` is a deliberate trade against OkHttp:
the gateway is on loopback, so the connection cost that library would save is
not being paid anyway, and it keeps the APK free of a dependency stack that has
to keep agreeing with itself. The SSE reader treats `: keepalive` as a liveness
comment, `: stream closed` as the gateway ending the run, and a read timeout
past the keepalive interval as "the run genuinely went quiet" — three different
things, handled three different ways.

`tool.completed` carries no call id, so an open call is matched by name from the
back of the list. That is how a sequential agent behaves, and how a parallel one
still lands on the right row often enough that a mis-closure is a grey tick
rather than a wrong claim.
