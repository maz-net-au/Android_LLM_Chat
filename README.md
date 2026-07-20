# LLMApp (PrivateAI)

An Android chat client for a **self-hosted [llama-server](https://github.com/ggml-org/llama.cpp)**,
with an optional **[ComfyUI](https://github.com/comfyanonymous/ComfyUI)** instance on the same host
for image / audio / video generation. Everything runs against your own machine — no third-party API,
no cloud accounts.

Built with **Kotlin + Jetpack Compose**. State is MVVM (`ViewModel` + `StateFlow`), persistence is
**Room**, and dependencies are wired by hand through a small service locator on the `Application`
object — no Hilt/Dagger.

---

## Features

**Chat**
- Streaming replies from llama-server (OpenAI-compatible `/v1/chat/completions`).
- **Characters** — persona (system prompt) + optional greeting, with `{{char}}`/`{{user}}`
  placeholders (text-generation-webui style). Built-ins plus full user CRUD, YAML import/export,
  and an **AI character generator**. A character can use "Name:" transcript framing (roleplay) or
  plain assistant framing.
- **Sampling presets** (temperature, top-p/k, penalties, mirostat, …) shared across chats, with
  **per-conversation overrides** layered on top.
- **Message actions** — edit, delete, copy, **regenerate** (kept as swipeable *variants*),
  **continue** (extend the last reply), and **impersonate** (have the model draft *your* next turn
  into the input box).
- **Reasoning blocks** — `<think>…</think>` and Gemma's thought-channel tags are shown as a
  collapsed "Thoughts" disclosure, hidden entirely when empty, and stripped from context resent to
  the server (see `data/gen/Think.kt`).
- **Multimodal input** — attach images and record **voice notes** (PCM16 WAV). Whether a model
  accepts images/audio is inferred from its name (`-VL-`, `-AL-`, `-VAL-`; see `ModelCapabilities`).
- **Long conversations** — *Summarize & continue* folds older messages into a running summary so a
  chat can outlast the context window; a live context readout shows tokens vs. the model's limit
  (via `/tokenize` + `/props`).

**Media generation (ComfyUI)**
- Five flows: **text→image, text→audio, text→video, image→image, image→video**.
- Installable **workflow packages** with form-driven inputs; submitted jobs run through a **queue**
  with live progress, results land in a **gallery** (with export to the device MediaStore and
  one-tap **regenerate**).
- **Scene images** — inside a chat, the LLM writes an image prompt from the current scene, ComfyUI
  renders it, and it appears inline on the message (this is separate from the standalone gallery).

**Shell & infrastructure**
- A **launcher** hub tiles the entry points (chat, quick "image → text", the media flows, gallery,
  queue, settings).
- **Server health** for both llama-server (`/health`) and ComfyUI (`/system_stats`) is polled in the
  background, which also keeps the `/v1/models` list fresh.
- **Full backups** — self-contained backup files that inline attachment bytes as base64, so a backup
  restores completely (but isn't text-only and can be large).

---

## Architecture at a glance

```
Compose Screen  ──>  ViewModel (StateFlow)  ──>  Repository  ──>  Room / files / HTTP
     (ui/)               (vm/)                      (data/)
```

Long-running network work (streaming a reply, summarizing, rendering a scene image, running a
ComfyUI job) runs in a **foreground Service** so it survives the screen leaving. Each service pairs
with a **Controller** — a small app-scoped holder of live progress that any screen can observe:

| Service (`data/gen`, `data/comfy`) | Controller (on `LlamaChatApp`) | What it drives |
|---|---|---|
| `GenerationService`   | `generation`   | Streaming a chat reply / continue |
| `SummarizationService`| `summarization`| *Summarize & continue* |
| `SceneImageService`   | `sceneImages`  | In-chat scene image (describe → render) |
| `ComfyGenerationService` | `comfyJobs` | Standalone media-generation jobs |

`LlamaChatApp` (the `Application`) is the **service locator**: it lazily builds the single client,
repository, store, and controller instances and hands them to ViewModels via their factories.

---

## Where to find things

| Path | What lives here |
|---|---|
| `LlamaChatApp.kt` | App entry point + manual service locator (all singletons) |
| `MainActivity.kt` | Single activity; hosts the Compose nav graph |
| `ui/Navigation.kt` | `Routes` + the `NavHost` wiring every screen to its ViewModel |
| `ui/<feature>/` | Compose screens: `chat`, `home`, `newconv`, `characters`, `gallery`, `queue`, `workflow`, `settings`, `launcher` |
| `ui/components/`, `ui/theme/` | Shared composables (AppBar, Avatar, Markdown, …) and the design tokens |
| `vm/` | One `ViewModel` per screen; screen state + intents |
| `data/model/` | Domain types: `Models.kt` (`ChatMessage`, `Conversation`, `Attachment`…), `Catalog.kt` (characters/presets), `ModelCapabilities.kt` |
| `data/net/` | HTTP: `LlamaClient`, `ComfyClient`, their DTOs, `ServerHealthMonitor` |
| `data/db/` | Room database, entities, and DAOs |
| `data/gen/` | Chat-request building (`ChatRequestBuilder`), the generation/summarize/scene services + controllers, and `Think.kt` (reasoning-tag handling) |
| `data/comfy/` | ComfyUI workflow packages, the job controller/service, and workflow patching |
| `data/attach/` | Attachment file store + the WAV voice recorder |
| `data/gallery/`, `data/GalleryRepository.kt` | Generated-media files + metadata |
| `data/backup/` | Backup/restore file format |
| `data/SettingsRepository.kt` | App settings (server address, default model/character/preset, …) |

**Rule of thumb:** a feature is a vertical slice — screen in `ui/<feature>/`, its `ViewModel` in
`vm/`, and the data it needs in a `data/` repository (plus a `data/gen` or `data/comfy` service if it
does streaming/background work). Start from `ui/Navigation.kt` to trace any screen end to end.

---

## Build & run

- **Verify:** `./gradlew assembleDebug`.
- There is **no emulator** on the dev machine — runtime testing is a manual install on a physical
  device pointed at a reachable llama-server (and optionally ComfyUI). Set the server address in
  **Settings**.
- The toolchain is **pinned** (Gradle 8.2 / AGP 8.2.2 / Kotlin 1.9.24 / Compose BOM 2024.06.00 /
  lifecycle 2.8.4). Only add libraries compatible with these — e.g. **Coil 2.x**, not 3.x.

## Data-format compatibility (important)

`ChatMessage`, `Attachment`, `SceneImageMeta`, and `SamplingOverrides` are serialized into **both**
the Room blob (`ConversationEntity.messagesJson`) and the backup file (`data/backup`, `BACKUP_VERSION`).
**Add fields only with defaults; never rename/remove/retype.** A breaking change needs a Room
migration and/or a backup-version bump — stop and ask first. See `CLAUDE.md` for the full rules and
server conventions.
