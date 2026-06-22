# llama chat — Android app

A native Android chat client for a self-hosted [llama-server](https://github.com/ggml-org/llama.cpp/tree/master/tools/server),
built from the Claude Design handoff in `project/LlamaChat.dc.html`. Jetpack Compose + Material 3,
with real OpenAI-compatible SSE streaming.

## Stack

| | |
|---|---|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose (BOM 2024.06), Material 3 |
| Build | AGP 8.2.2, Gradle 8.2, JDK 17 |
| SDK | compileSdk/targetSdk 34, minSdk 26 |
| Persistence | Room 2.6.1 (conversations) + DataStore (server + model settings) |
| Networking | OkHttp 4.12 + okhttp-sse, kotlinx.serialization |

## Build

```bash
# Android SDK is expected at the path in local.properties (sdk.dir).
./gradlew :app:assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug            # install to a connected device/emulator
```

## How it maps to the prototype

The prototype faked the LLM with canned replies; this app talks to a real server.

- **Connect** screen → `GET /v1/models` both verifies the server is reachable and
  populates the model picker. Cleartext HTTP to a LAN address is enabled
  (`usesCleartextTraffic="true"`) because llama-server is typically plain HTTP on the local network.
- **Chat** streaming → `POST /v1/chat/completions` with `stream:true`; tokens arrive as
  SSE `delta.content`. The character supplies the `system` message; the preset supplies
  `temperature`/`top_p`/`top_k`/`repeat_penalty` (the last two are llama.cpp extensions).
- **Stop** cancels the SSE call and keeps the partial text. **Regenerate** adds a new
  variant to the last assistant message (navigable with ‹ / ›). **Edit**, **Clear**,
  **Delete**, **Continue**, and per-message editing all mirror the prototype.

## Architecture

```
data/            models, Room DB, DataStore settings, repository, llama-server client
vm/              ConnectViewModel, HomeViewModel, NewConversationViewModel, ChatViewModel
ui/              Navigation + theme + components + one package per screen
```

`LlamaChatApp` is a tiny manual service locator (no DI framework). Conversations are
persisted as JSON blobs in a single Room table; the connection itself is in-memory, so
each launch starts on the Connect screen.

## Known caveat — "Continue"

"Continue" re-sends the conversation with the partial assistant turn as the last message
and streams more tokens onto it. Whether the server truly continues that turn (vs. starting
a fresh one) depends on the model's chat template; it works on common llama.cpp setups but
is not guaranteed across every template.
