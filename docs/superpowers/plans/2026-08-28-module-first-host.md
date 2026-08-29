# Module-first host: first verifiable slice

## Goal

OpenDevice Forge is a desktop and Android module platform. Each host installs and runs compatible modules; local phone LLM and remote inference are one module, not the shell. This follows the user's 2026-08-28 clarification and the approved direction in [the original spec](../specs/2026-08-25-opendevice-node-module-console-design.md).

## Architecture

- Keep the existing Android registry, shared manifest and native runtime. Do not add a second module system.
- Open the Android host on Modules. Model setup belongs only to the AI module. Primary navigation belongs to the host; chat and AI connections belong inside AI.
- Show the trusted bundled catalog through generic lifecycle actions. Add a read-only device-info module using existing actual device facts.
- Installation does not enable a module. Uninstall requires a disabled module, retains a tombstone across restart, and does not delete user model data. Bundled runtime bytes remain in the APK; describe this explicitly.
- AI is not protected. Kernel recovery remains available independently; repeated AI crashes also disable AI.
- Fail closed on unsupported runtime entries. Do not treat arbitrary manifests as downloadable executable code.

## Tech stack

Existing Kotlin, Compose Material 3, DataStore, Kotlin serialization, shared JSON schema and Vitest. No new dependencies or privileges.

## Global constraints

Preserve existing inference/safety work and user data. Do not root, change CPU clocks, disable hardware protection, enable LAN, download models, publish externally or create signing keys automatically. Desktop and Android use host-specific runtimes; a shared product direction does not imply cross-platform binary compatibility.

## Tasks / verification

1. Add regression tests for ordinary AI crash handling, uninstalled records surviving restart, and a module-first initial route; observe expected failures.
2. Implement persisted uninstall/reinstall and installed-state guards, including safe-mode migration and disabled recovery. Test active/protected/unknown rejection, reinstall disabled and persistence failures.
3. Add the device-info manifest to the existing schema/Android fixture validation. Wire generic actions, source/permissions/compatibility display, explicit uninstall confirmation and module-local AI routes.
4. Run Android unit tests/lint/build plus shared contract/web checks. Update Compose flows for missing-model independent navigation, module lifecycle and existing AI performance/chat/connection behavior.
5. Verify the newly built APK on the attached Android device, preserve existing model data/settings, and record exact artifact hashes and evidence boundaries.

## Later release gates (not completed by this slice)

Current status: the module-first host slice and its follow-up package trust boundary are verified. The last phone-installed debug artifacts match their local hashes; 137 Android JVM tests, 91 workspace Vitest tests, 18 Rust/Tauri tests, lint/build, cross-host P-256 signatures, Android API-29 and desktop macOS private package storage, UI/native acceptance and retained state all pass. The final Android rebuild still needs phone installation and readback after reconnection. See [candidate evidence](../../verification/android-module-host.md). Network acquisition, review UI, registry activation and public release remain separate gates.

Online module acquisition still needs bounded HTTPS/catalog download, an explicit review UI and registry activation. Both hosts now verify publisher trust, the complete manifest, hashes, source/runtime/platform policy and permissions before private versioned storage; both require review again for material updates and permission-restoring rollback. A bundled catalog is not an online store. GitHub publication and the OpenAI support application require separate final confirmation and truthful evidence.

## UI references

Reuse existing project components; keep unknown device facts unknown. Navigation state is a small saveable destination, not copied module data: https://developer.android.com/develop/ui/compose/state-saving and https://developer.android.com/develop/ui/compose/components/navigation-bar.
