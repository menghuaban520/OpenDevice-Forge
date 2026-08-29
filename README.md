# OpenDevice Forge

OpenDevice Forge is a device-first module platform for desktop and Android. Each host manages modules compatible with its own runtime. Phone-local LLM inference and remote API access are one optional module, not the product shell.

The current build includes the desktop module workbench and an Android module host with read-only device information and a bounded local AI module. The phone opens on Modules without requiring a model download. Package v1 now has complete-manifest P-256 verification, explicit publisher trust, permission review planning, and private versioned stores on both hosts. Android uses atomic directory/pointer moves verified on API 29; desktop uses atomic version publication plus an append-only, last-valid pointer journal verified on macOS. Network acquisition and registry activation remain release gates; bundled Android runtime code still ships inside the APK.

See [module-host candidate verification](docs/verification/android-module-host.md) for local tests/build, installed APK provenance, cross-host package signatures, prior version-bound phone evidence, native inference, module persistence and AI service start/stop. The latest UI build must receive a new phone hash and regression run before those earlier device results can be carried forward. Online acquisition and public release remain separate gates.

## Safety boundary

The desktop/module kernel does **not** unlock bootloaders, obtain Root, silently install APKs, modify identifiers, bypass locks, extract personal files, or execute arbitrary shell commands. The Android companion is a separately installed, visible application; community packages are parsed as declarative manifests only.

## Workspace

- `packages/module-contract`: the shared, versioned module manifest and integrity rules used by the host and Android companion.
- `packages/core`: plugin contracts, lifecycle, device facts, readiness, reports, and desktop runtime definitions.
- `apps/desktop`: React/Vite interface and Tauri read-only ADB bridge.
- `apps/site`: cross-platform download and product information page.
- `apps/android-node`: Kotlin/Compose module host with a persistent module kernel, device-info module, and optional pinned `llama.cpp`/authenticated local API module.

## Local development

Use Node.js 24 or newer and pnpm 11. The web demo remains usable when ADB and a physical phone are unavailable.

```sh
pnpm install
pnpm check
```

Run the desktop interface or product site in a browser:

```sh
pnpm --filter @opendevice/desktop dev
pnpm --filter @opendevice/site dev
```

Build a native package for the current operating system:

```sh
pnpm --filter @opendevice/desktop tauri build
```

Build and check the Android companion with JDK 17, Android SDK 36, NDK r29, and CMake 3.31.6 installed:

```sh
JAVA_HOME=/path/to/jdk-17 ANDROID_HOME=/path/to/android-sdk \
  apps/android-node/gradlew -p apps/android-node \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

The native build is unsigned unless platform signing credentials are supplied deliberately. Pushing a version-matched `v*` tag after the reviewed source exists on the remote triggers macOS, Windows and Android arm64 draft artifacts plus SHA-256 files; the Android artifact uses debug signing. The workflow does not publish the product site.

No source commit has been pushed to the current public repository. No public deployment, signed package, or current-build real-device compatibility claim is included by default.

See [the 0.1.0 preview notes](docs/release/0.1.0-preview.md), [contribution guide](CONTRIBUTING.md), and [security policy](SECURITY.md) before publishing or distributing artifacts.

## Android performance controls

The Android host is not tied to one phone model. On Android 9+ arm64 devices it builds a capability profile from the live SDK, ABI, memory, allocatable storage and logical processor count, then labels the workload as light, balanced or performance. Manufacturer and model names are display facts, never compatibility allowlists. This is a multi-device policy, not a claim that every phone has completed real-device verification.

On the phone, Modules → 本地 AI 节点 → 打开 → 负载与保护 offers workload presets and custom 2–4 threads, 1–512 output tokens, a 38–43°C battery ceiling, and a 15–120-second load/generation deadline. Temperature and CPU readings are hidden by default and can be shown without changing protection. These are app-level limits, not CPU clock controls or a hardware safety guarantee; system thermal and charging protections remain intact.

See the [version-bound CDL-AN50 verification](docs/verification/android-node-cdl-an50.md) for measured short-run performance, installed APK hashes, test results and unverified background/long-duration boundaries.
