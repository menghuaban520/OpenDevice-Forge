# OpenDevice Forge — Device-first Design Contract

> Status: v2 direction accepted for implementation after the first prototype exposed two product defects: a hard-coded reference phone and configuration that was not backed by plugin capabilities.

## Product surface

- Cross-platform Windows/macOS desktop device utility, not a browser dashboard or marketing site.
- Primary user task: connect or select an Android phone, confirm that the app identified the correct device, understand its real condition, then choose an inspection or plugin action.
- No phone model is a runtime default. The shell, breadcrumb, report, and compatibility view all derive their identity from the selected device. A missing field remains unknown and never falls back to another manufacturer's reference device.
- Android compatibility is property-driven rather than model-list-driven: use standard manufacturer, model, product, Android version, ABI, memory, storage, and battery facts. Manufacturer-specific extensions may add facts later, but cannot block basic identification.
- Multiple connected devices are first-class. Selection uses an ephemeral session identifier that is never displayed, exported, or persisted.
- Apple devices require a separate device provider. Until that provider exists, the UI must explicitly say Android instead of implying that ADB can identify an iPhone.

## Product architecture: microkernel plus removable plugins

OpenDevice Forge is plugin-first, but not literally plugin-only. A small non-removable kernel prevents a disabled or broken plugin from making the application unrecoverable.

The kernel owns only:

- device discovery and connection selection;
- plugin install, enable, disable, update, uninstall, rollback, and safe mode;
- permission prompts, signature/source verification, task audit, and crash isolation;
- recovery and plugin-market entry points;
- versioned contribution APIs and the UI slots plugins may fill.

Everything else is a plugin, including device inspection, report extensions, AI node deployment, remote gateway, vision bridge, network tools, Root Lab, and future community workflows.

### Plugin contributions

A plugin may declare one or more explicit contributions:

- `navigation`: a reorderable sidebar destination;
- `overviewAction`: a device-overview shortcut;
- `overviewSection`: a bounded device-overview information region;
- `deviceProbe`: allowlisted read-only facts and compatibility checks;
- `configuration`: schema-driven settings rendered by the shell;
- `workflow`: a multi-step operation with progress, cancellation, and rollback;
- `service`: a local or remote API with port, authentication, health, and logs;
- `companion`: an optional phone-side package or model asset;
- `reportSection`: additional facts in exported reports.

Third-party plugins cannot arbitrarily replace the shell or inject unbounded native UI. Community plugins start with declarative contributions and allowlisted operations; reviewed trusted plugins may request stronger host/device capabilities with an explicit warning.

### Lifecycle and scope

- `install` adds a plugin to the desktop workbench.
- `enable/disable` controls whether it can run without deleting configuration.
- `deploy/remove from device` manages the optional phone-side component separately.
- `uninstall` removes the desktop plugin after showing affected devices and services.
- Configuration exists only when a plugin declares a real `configuration` contribution with a schema implemented by the host. The shell never invents ports, API keys, startup switches, deployment, or device scope for a plugin.
- Sidebar placement and ordering remain customizable through an advanced market action, not a permanent control in the device rail.
- A failed plugin launch opens the app in safe mode with third-party plugins disabled; the market and recovery settings always remain reachable.

### Market sources

- Official catalog: signed and reviewed first-party plugins.
- Community catalog: signed metadata, transparent source, declared permissions, compatibility, and rollback.
- GitHub source: install only from a pinned release or commit after manifest and checksum review.
- Local package: advanced mode, explicit trust confirmation, never auto-updated from an unknown source.

## Reference adoption map

| Source | Adopt | Adapt | Reject | Evidence |
| --- | --- | --- | --- | --- |
| iMazing 3 device overview | A strong physical device identity rail; native toolbar; one device-context screen; open information regions for device facts, storage, battery, and quick actions | Replace Apple data management with Android inspection, plugins, AI node, and remote services; keep the information hierarchy but use original branding and icons | Apple assets, exact component geometry, proprietary icons, backup/data-transfer claims | `work/references/imazing-device-overview.webp`; https://imazing.com/guides/learn-imazing-interface |
| VS Code Extensions | Separate plugin list, selected-plugin details, permissions, source, version, and configuration | Make compatibility checks and rollback more prominent than popularity; use the product's native light visual language | Dark editor skin, ratings/download counts, marketplace social proof, pixel-for-pixel split layout | `work/references/vscode-extensions.png`; https://code.visualstudio.com/docs/configure/extensions/extension-marketplace |
| Docker Desktop services | Service status, local port, remote reachability, logs, and contextual actions | Translate containers into phone-provided services and explain local/remote access in plain Chinese | Developer-first terminal surface, resource charts as decoration, unsafe public defaults | https://docs.docker.com/desktop/use-desktop/ |
| HUAWEI official support asset | Accurate nova 7 SE family silhouette and front/back visual anchor | Use only as a device identity reference inside the desktop UI | Huawei branding as product branding; treating reference specifications as connected-device facts | `work/references/nova7-se-lehuo-official.png`; https://consumer.huawei.com/cn/support/phones/nova7-se-5g-lehuoban/ |

## Active structural rules

1. The phone is the visual anchor. The persistent rail shows the selected device's measured identity and connection state; before connection it shows a neutral Android device with no fictional brand, model, battery, or specifications.
2. The main workspace is an open native desktop surface, not a grid of equal rounded cards.
3. The overview follows this reading order: identity/connection -> essential facts -> the next useful action -> concise compatibility findings.
4. The stable sidebar contains only `设备` and `插件`. Task audit, recovery, sources, and layout customization are contextual or advanced actions inside the plugin surface.
5. Enabled plugins do not automatically become sidebar destinations. A plugin may contribute navigation only after the user pins it; the default rail stays quiet.
6. Use disclosure (“显示全部”, “更多”) for deep facts instead of showing eight KPI tiles.
7. Warnings use one small symbol plus plain text. Avoid status pills, colored backgrounds, and repeated red/amber/green labels.
8. Tables are reserved for true collections: plugin list, service list, task history, and reports. Device facts use aligned definition rows.
9. One primary action per screen. Secondary actions use quiet text or toolbar controls.
10. Disconnected, ADB-missing, unauthorized, offline, single-device, and multi-device states are designed states, not variations of a demo phone.
11. Show settings on demand and in context. No setting is displayed before the corresponding capability exists and can be applied or rolled back.

## Visual rules to validate in the next concept

- True white/light neutral workspace; dark graphite device rail is allowed.
- System UI font stack; compact desktop control typography, not oversized headings.
- Borders are hairlines; radius is restrained; shadows only separate major app layers.
- Blue is used for selection and the one primary action. Amber/red/green appear only for factual state.
- One outline icon family with consistent optical size and stroke.
- The phone render must be recognizable and materially larger than plugin/service icons.

## Rejected patterns

- Rejected v1: oversized beginner landing page with too little real device information.
- Rejected v2 direction: symmetrical admin dashboard, equal bordered cards, KPI strip, badge-heavy state language, and every feature visible at once. It was clearer but still looked AI-generated and did not carry iMazing's native device-manager character.
- Rejected prototype behavior: using the nova 7 reference snapshot as the base of a live inspection. A partially read Pixel, Samsung, Xiaomi, OPPO, vivo, Huawei, Honor, or other Android device must never inherit Huawei facts.
- Rejected prototype behavior: the same model preset, port, access range, startup, API-key, and deploy controls on every plugin. Those controls implied implementation that did not exist.
- Do not fix these problems by changing color, gradients, shadows, or radius alone; the hierarchy and container model must change.

## Verified project rule — device facts before plugin configuration

- Status: verified.
- Signal: the prototype displayed a Huawei reference device when ADB was missing and rendered identical service settings for unrelated plugins.
- Method: start from an unknown snapshot, populate only facts reported by the selected Android device, discard late results from a previously selected device, and render configuration only for an implemented `configuration` contribution.
- Evidence: desktop unit tests cover ADB-missing, unauthorized, Google, partial Samsung, multiple devices, and rapid selection; Rust tests cover standard ADB lookup paths; Playwright and native-app screenshots cover the disconnected overview, plugin detail, and layout editor.
- Applies to: Android device identity, reports, compatibility assessment, device selection, plugin details, and advanced layout previews.
- Does not apply to: iPhone/iPad discovery, vendor-only diagnostic protocols, automatic Platform-Tools installation, or claims that a specific model supports Root or a local model.
- Revalidate when: adding an Apple provider, bundling or replacing ADB, introducing a plugin configuration schema, or displaying manufacturer-specific facts.
- Verified at: 2026-08-16.

## Android node control surface

The Android companion is a compact, touch-first operations console for the phone owner. Its primary flow is `enable module -> verify model -> start visible service -> create a client key or chat locally`. It is not a miniature desktop dashboard and does not imply that the future public gateway, Root Broker, marketplace, creator, or script sandbox already exists.

### Android reference adoption map

| Source | Adopt | Adapt | Reject | Evidence |
| --- | --- | --- | --- | --- |
| Android Material 3 for Compose | Native buttons, text fields, cards, tonal surfaces, dialogs, progress and semantic colors | Use a restrained device-utility hierarchy, factual Chinese copy, and one primary action in the current task area | Expressive decoration that competes with live device and safety state; equal card grids | https://developer.android.com/develop/ui/compose/designsystems/material3 |
| Android compact navigation bar | Four persistent destinations of equal importance on a phone | Node, Modules, Connections, and Status remain stable and keep their state while switching | More than five destinations, text glyphs pretending to be finished icons, hidden marketplace routes | https://developer.android.com/develop/ui/compose/components/navigation-bar |
| Android Compose dialog guidance | Modal confirmation for LAN exposure and one-time token disclosure | LAN confirmation uses the full compact window so the HTTP warning cannot be missed; the token dialog is dismissible and never reconstructs the raw token | Persisted confirmation, auto-opened settings, or a small warning buried under other controls | https://developer.android.com/develop/ui/compose/components/dialog |
| Android notification permission guidance | Ask in the visible start-node action on API 33+ | A denial keeps the service stopped and explains why in the Node screen | Asking on first launch, bypassing denial, or opening system settings automatically | https://developer.android.com/develop/ui/compose/notifications/notification-permission |

### Android structural and visual rules

1. Reading order on Node is prerequisites, model action, service action, then local chat. The first unsatisfied prerequisite owns the primary action and blocking explanation.
2. Use the existing four-destination bottom navigation on compact phones. Each destination uses one consistent Material line-icon family and a text label; no single Chinese character acts as a placeholder icon.
3. Page padding is 20 dp horizontally, major vertical gaps are 16 dp, and related rows use 8–12 dp. Cards use 16 dp radius and tonal separation without decorative shadows.
4. Headings use the system Material type scale. Endpoints, USB commands, fingerprints, and the one-time token use selectable monospace text; long values wrap instead of shrinking.
5. Blue/primary is reserved for the current primary action and selected navigation. Error colors mean an actual blocking or destructive state; amber language is expressed with plain warning copy, not repeated status pills.
6. Loading, missing, ready, busy, paused, blocked, failed, empty, and safe-mode states are explicit. Buttons are disabled during incompatible actions; no fake throughput, capacity, IP, or model readiness is rendered.
7. LAN is off by default. Enabling it always requires a fresh full-screen phone confirmation with the exact cleartext-HTTP warning. Disabling it discards that confirmation.
8. Raw client tokens exist only in the one-time creation surface. After dismissal, lists show label, fingerprint, creation/revocation state, and Revoke only.
9. Public gateway, Root Broker, Marketplace, Creator, and Script sandbox stay visible only as `尚未实现` in Status. Modules explicitly says `尚未提供在线模块市场`; there is no dead search/install control.
10. This release targets compact Android phones. Wider windows center content at a readable maximum width instead of stretching definition rows; adaptive rail/tablet behavior remains a later verified enhancement.
