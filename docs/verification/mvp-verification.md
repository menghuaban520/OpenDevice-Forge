# OpenDevice Forge MVP verification

## Verdict

The local MVP is runnable on this Apple-silicon Mac. The plugin microkernel, device assessment/report domain, browser demo, read-only ADB bridge, desktop interface, responsive product site, and macOS packaging route are implemented. The generated macOS app launched successfully and exited normally.

This is not a public release or a phone-compatibility certification.

## Verification environment

- macOS 26.5 (25F71), arm64
- Node.js 24.18.1
- pnpm 11.5.2
- Rust/Cargo 1.95.0
- System Google Chrome through Playwright

## Evidence matrix

| Area | Local evidence | Status |
| --- | --- | --- |
| Plugin manifest, lifecycle, rollback, safe mode, layouts | Core unit tests | Passed |
| Device sources, readiness, JSON/Markdown reports and redaction | Core unit tests | Passed |
| Fixed read-only ADB operations, parsing, timeout/error behavior | Rust tests and Clippy | Passed |
| Overview, market, lifecycle controls, profiles, layout draft/save | Desktop unit and real-browser tests | Passed |
| Website platform detection, release honesty and responsive layout | Site unit and real-browser tests | Passed |
| macOS native application | Tauri release build and process launch | Passed on this Mac |
| DMG container | `hdiutil verify` | Passed |
| Apple distribution signature/notarization | No Developer ID credentials were provided | Not available |
| HUAWEI nova 7 SE 5G 乐活版 | No real phone was connected during verification | Not available |
| Windows runtime | Workflow is configured; no Windows hardware/run was available locally | Not available |
| Local model and remote API | Only declarative plugin/readiness surfaces exist | Deliberately not implemented |

## Packaging result

- Application: `apps/desktop/src-tauri/target/release/bundle/macos/OpenDevice Forge.app`
- Disk image: `apps/desktop/src-tauri/target/release/bundle/dmg/OpenDevice Forge_0.1.0_aarch64.dmg`
- Architecture: arm64
- Disk image size: 5,021,524 bytes
- SHA-256: `204b9c7092e45669b94ef0d7239facd93c57441f3cf185ed4f1183280fbcde6c`
- Launch proof: the packaged application process appeared at its bundle executable path after `open -na`, then accepted a normal quit request.
- Integrity: the DMG passed its embedded CRC verification. A separate SHA-256 checksum is included with the handoff copy.
- Signing boundary: the local binary only has an ad-hoc linker signature and is not Developer ID signed or notarized. Gatekeeper distribution acceptance is therefore not claimed.

## Safety review

- Tauri exposes only `probe_adb`, `list_devices`, and `inspect_device`.
- ADB commands are constructed from fixed enum variants; there is no user-provided shell command path.
- No bootloader unlock, Root acquisition, flash, APK install, identifier modification, lock bypass, personal-file extraction, or retained serial number was added.
- GitHub/community plugin content is declarative-only in the MVP; native package execution is rejected.
- No remote repository, tag, release, deployment, account, token, or signing credential was created.

## Remaining real-world gates

1. Connect one chosen phone, enable developer options and USB debugging, authorize this computer, and compare the live snapshot with the public/demo snapshot.
2. Run the configured workflow on Windows and inspect the actual installer and native window before calling Windows supported.
3. Add platform signing/notarization only after the repository/owner and distribution policy are chosen.
4. Implement a local model node and remote gateway as separate reviewed plugins; their current presence in the UI is architectural, not runtime proof.
