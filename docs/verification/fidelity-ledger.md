# Experience fidelity ledger

This ledger records current implemented surfaces. Concept images remain design references only; removed or unavailable capabilities are not counted as verification evidence.

## Desktop and website

| Check | Current evidence | Result |
| --- | --- | --- |
| Device-first frame | `desktop-overview.png` | The neutral disconnected state shows no fictional manufacturer, model, battery, or specifications. |
| Module center | `module-center.png` | The built-in catalog exposes only modules with a working lifecycle and runtime instead of decorative ideas. |
| Runtime-backed workbench | `device-inspection-workbench.png` | Browser E2E follows install → enable → open and keeps execution disabled without an authorized phone. |
| Compact desktop fit | `module-center-1280x800.png` | Playwright verifies no document-level horizontal overflow at 1280 × 800. |
| Responsive website | `site-desktop.png`, `site-mobile.png` | The site passes at 1440 × 900 and 390 × 844 without horizontal overflow or a false public-download claim. |

The old layout editor was intentionally removed because it was not part of the real device → module center → workbench path. Its concept and screenshots are not acceptance evidence for the current application.

## Truthful copy and capability boundaries

- Live device identity comes only from the selected Android device. Missing values remain unknown.
- The desktop hides `AI 节点` and other catalog ideas until they have a working runtime; the separately installed Android companion does not make a desktop module runnable automatically.
- `远程网关`, public Internet access, Root Broker, online module installation, Creator, and script sandbox remain unimplemented unless a version-bound verification report says otherwise.
- Community plugins remain declarative and cannot inject arbitrary native UI or shell commands.
- Website download controls remain disabled until a verified public release exists.

## Android node

- The phone companion uses a compact Material control surface with explicit missing, queued, downloading, ready, serving, stopped, busy, and failed states.
- Model download is user-initiated, HTTPS-only, size- and SHA-256-verified, resumable, and stored under the application's no-backup directory.
- LAN is off by default and requires a fresh phone confirmation. Client keys are shown only in a one-time surface and are stored as fingerprints afterward.
- Public gateway, Root Broker, online module directory, Creator, and Script sandbox remain unavailable and are not presented as installable features.
- Node/Chat/Status share optional live performance readings; hiding them never disables the separate resource guard. Presets and custom fields change actual bounded inference settings, not CPU clocks or system protection. Unknown CPU frequency/temperature data is never fabricated.
- Current compact light-theme evidence: `android-performance-node.png` and `android-performance-settings.png` were inspected on the installed CDL-AN50 app. All six CPU-frequency readings were available on this phone; other-device availability is not implied. Four combined native/UI tests, actual hidden-panel chat and visible start/stop passed for the APK in the phone report.
- Source/build evidence and real-device evidence are kept separate in `mvp-verification.md` and `android-node-cdl-an50.md`; an APK hash is never carried forward to a later build.
