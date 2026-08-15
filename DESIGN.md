# OpenDevice Forge — Starter Design Contract

> Status: visual direction is under review. This file records the active reference boundaries and anti-patterns only; implementation tokens will be locked after the device overview is accepted.

## Product surface

- Cross-platform Windows/macOS desktop device utility, not a browser dashboard or marketing site.
- Primary user task: select one Android phone, understand its real condition, then choose an inspection, plugin, or remote-service action.
- Current reference device: HUAWEI nova 7 SE 5G 乐活版 (`CDL-AN50`). All runtime facts must still come from the connected device.

## Product architecture: microkernel plus removable plugins

OpenDevice Forge is plugin-first, but not literally plugin-only. A small non-removable kernel prevents a disabled or broken plugin from making the application unrecoverable.

The kernel owns only:

- device discovery and connection selection;
- plugin install, enable, disable, update, uninstall, rollback, and safe mode;
- permission prompts, signature/source verification, task audit, and crash isolation;
- stable settings, recovery, and plugin-market entry points;
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
- Settings may apply to `this device`, `all devices`, or a named profile.
- Sidebar placement, overview visibility, shortcut visibility, and ordering are user-configurable.
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

1. The phone is the visual anchor. A device render, exact connected name, model code, connection state, battery, and navigation occupy one persistent device rail.
2. The main workspace is an open native desktop surface, not a grid of equal rounded cards.
3. The overview follows this reading order: quick actions -> device information -> storage/battery -> suitability and problems -> recent operations.
4. The stable sidebar contains only kernel destinations. Enabled plugins contribute reorderable destinations under an “已启用插件” group; users can pin, hide, reorder, enable, or disable them.
5. Plugins and remote services have their own navigation destinations. The overview shows only contextual shortcuts and concise status, never a second full dashboard inside the dashboard.
6. Use disclosure (“显示全部”, “更多”) for deep facts instead of showing eight KPI tiles.
7. Warnings use one small symbol plus plain text. Avoid status pills, colored backgrounds, and repeated red/amber/green labels.
8. Tables are reserved for true collections: plugin list, service list, task history, and reports. Device facts use aligned definition rows.
9. One primary action per screen. Secondary actions use quiet text or toolbar controls.

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
- Do not fix these problems by changing color, gradients, shadows, or radius alone; the hierarchy and container model must change.
