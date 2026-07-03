# LLMApp (llama chat)

Android chat client for a self-hosted llama-server, with a ComfyUI instance on the
same host for (future) image/audio/video generation. Kotlin + Jetpack Compose,
manual service locator on `LlamaChatApp` (no Hilt).

## Build & verification

- Verify with `./gradlew assembleDebug`. There is no emulator on this machine —
  runtime testing is a manual install on a physical device.
- Toolchain is pinned: Gradle 8.2 / AGP 8.2.2 / Kotlin 1.9.24 / Compose BOM
  2024.06.00 / lifecycle 2.8.4. Only add libraries compatible with these
  (e.g. Coil **2.x**, not 3.x, which needs newer Kotlin/Compose).

## Compatibility rules (data formats)

- `ChatMessage`, `Attachment`, `SamplingOverrides` (data/model/Models.kt) are
  serialized into BOTH the Room blob (`ConversationEntity.messagesJson`) and the
  backup file format (data/backup/ConversationBackup.kt, `BACKUP_VERSION`).
  Add fields only with defaults; never rename/remove/retype. A breaking change
  needs a DB migration and/or backup version bump — stop and ask first.
- Backups are text-only: attachment metadata is stripped on export and the bytes
  (app-private files under `filesDir/attachments/<convId>/`, owned by
  `AttachmentStore`) never leave the device.

## Server conventions

- llama-server speaks the OpenAI-compatible API; text-only messages must keep
  encoding `content` as a plain JSON string (see `apiText` in data/net/Dto.kt).
- Health: llama-server `GET /health`, ComfyUI `GET /system_stats` — polled by
  `ServerHealthMonitor`, which also owns fetching `/v1/models`.
- Model input modality is inferred from the model NAME (`ModelCapabilities`):
  `-VL-` = vision, `-AL-` = audio, `-VAL-` = both. Voice notes are recorded as
  PCM16 WAV (llama-server only accepts wav/mp3).
