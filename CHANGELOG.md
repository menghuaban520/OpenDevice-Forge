# Changelog

## 0.1.0-preview — unreleased

### Added

- Desktop macOS/Windows device workbench with a fixed, read-only ADB bridge and recoverable plugin lifecycle.
- Android module-first host with independent device-information and local-AI modules.
- Pinned arm64 `llama.cpp` inference, local chat and an authenticated OpenAI-compatible local HTTP endpoint.
- Customizable app-level inference limits: 2–4 threads, 1–512 output tokens, 38–43 °C battery ceiling and 15–120 second deadline, with optional metric display.
- Cross-host package-v1 verification using bounded canonical manifests, SHA-256, P-256 signatures and explicit publisher trust.
- Private versioned package storage, material-change review and rollback protection on Android and desktop.

### Safety boundaries

- No Root, bootloader unlock, lock bypass, CPU clock control, thermal/battery-protection bypass, silent APK install or arbitrary shell execution.
- LAN service access is off by default and requires a fresh on-device warning.
- Online catalog acquisition, live registry activation and production signing are not part of this preview.
