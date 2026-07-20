# LLMApp (PrivateAI)

Android chat client for a self-hosted llama-server, with a ComfyUI instance on the
same host for image/audio/video generation. Kotlin + Jetpack Compose, MVVM
(`ViewModel` + `StateFlow`), Room persistence, manual service locator on
`LlamaChatApp` (no Hilt). See `README.md` for the feature tour.

## Architecture & layout

- **Vertical slices.** A feature = a Compose screen in `ui/<feature>/`, its
  `ViewModel` in `vm/`, and a repository/service under `data/`. Start from
  `ui/Navigation.kt` (the `Routes` + `NavHost`) to trace any screen to its VM.
- **Service locator.** `LlamaChatApp` (the `Application`) lazily builds every
  singleton — clients, repositories, stores, controllers — and hands them to
  ViewModels through their `factory(app, …)` functions.
- **Service + Controller pattern.** Long-running network work runs in a foreground
  `Service` (so it survives leaving the screen); each pairs with an app-scoped
  `Controller` that exposes live progress for screens to observe:
  `GenerationService`/`generation` (chat reply & continue),
  `SummarizationService`/`summarization`, `SceneImageService`/`sceneImages`,
  `ComfyGenerationService`/`comfyJobs`.
- **Where things live:** `data/model` domain types (`Models.kt`, `Catalog.kt`,
  `ModelCapabilities.kt`); `data/net` HTTP (`LlamaClient`, `ComfyClient`, DTOs,
  `ServerHealthMonitor`); `data/db` Room; `data/gen` request building
  (`ChatRequestBuilder`) + generation services + `Think.kt` (reasoning-tag
  parse/strip); `data/comfy` workflow packages + job running; `data/attach`
  attachment files + WAV recorder; `data/backup` backup format; `data/gallery`
  generated-media files.

## Build & verification

- Verify with `./gradlew assembleDebug`. There is no emulator on this machine —
  runtime testing is a manual install on a physical device.
- Toolchain is pinned: Gradle 8.2 / AGP 8.2.2 / Kotlin 1.9.24 / Compose BOM
  2024.06.00 / lifecycle 2.8.4. Only add libraries compatible with these
  (e.g. Coil **2.x**, not 3.x, which needs newer Kotlin/Compose).

## Compatibility rules (data formats)

- `ChatMessage`, `Attachment`, `SceneImageMeta` (data/model/Models.kt) and
  `SamplingOverrides` (data/model/Catalog.kt) are serialized into BOTH the Room blob
  (`ConversationEntity.messagesJson`) and the backup file format
  (data/backup/ConversationBackup.kt, `BACKUP_VERSION`).
  Add fields only with defaults; never rename/remove/retype. A breaking change
  needs a DB migration and/or backup version bump — stop and ask first.
- Backups are full: attachment metadata is kept and the bytes (app-private files
  under `filesDir/attachments/<convId>/`, owned by `AttachmentStore`) are inlined
  as base64 in `BackupConversation.attachmentBytes`, keyed by fileName. So a
  backup file is self-contained but no longer text-only, and can be large.

## Server conventions

- llama-server speaks the OpenAI-compatible API; text-only messages must keep
  encoding `content` as a plain JSON string (see `apiText` in data/net/Dto.kt).
- Health: llama-server `GET /health`, ComfyUI `GET /system_stats` — polled by
  `ServerHealthMonitor`, which also owns fetching `/v1/models`.
- Model input modality is inferred from the model NAME (`ModelCapabilities`):
  `-VL-` = vision, `-AL-` = audio, `-VAL-` = both. Voice notes are recorded as
  PCM16 WAV (llama-server only accepts wav/mp3).
