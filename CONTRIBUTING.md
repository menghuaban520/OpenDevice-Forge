# Contributing to OpenDevice Forge

OpenDevice Forge is a pre-release, device-first module platform. Contributions are welcome when they preserve the project's fail-closed safety boundary and make a concrete host or module capability verifiable.

## Before opening a change

- Use Node.js 24+, pnpm 11, Rust stable and—when touching Android—JDK 17 with the pinned SDK/NDK/CMake versions in the README.
- Keep desktop ADB operations fixed, argument-vector based and read-only. Do not add a general shell command surface.
- Do not add Root, lock bypass, identifier modification, silent APK installation, CPU clock controls or thermal/battery-protection bypasses.
- Treat third-party bytes as untrusted. Keep size bounds, canonical manifest verification, publisher trust, permission review, disabled-by-default installation and rollback checks intact.
- Never commit device serials, API tokens, model files, signing keys or user data.

## Checks

Run the smallest relevant checks while iterating, then the complete workspace checks before a pull request:

```sh
pnpm check
cargo test --manifest-path apps/desktop/src-tauri/Cargo.toml --locked
cargo clippy --manifest-path apps/desktop/src-tauri/Cargo.toml --locked --all-targets -- -D warnings
```

For Android changes, also run:

```sh
apps/android-node/gradlew -p apps/android-node \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Explain which evidence layer your change verifies: source/tests, build artifact, installed device behavior, or public release. A local pass is not evidence of deployment or real-device compatibility.

Report security issues through the private process in [SECURITY.md](SECURITY.md), not a public issue.
