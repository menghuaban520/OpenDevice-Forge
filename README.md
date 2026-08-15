# OpenDevice Forge

OpenDevice Forge is a plugin-first desktop workbench for understanding and safely repurposing Android devices. The MVP focuses on a small, recoverable kernel, declarative plugins, configurable placement, and read-only inspection.

## Safety boundary

The first milestone does **not** unlock bootloaders, obtain Root, install APKs, modify identifiers, bypass locks, extract personal files, or execute arbitrary shell commands. Community packages are parsed as declarative manifests only.

## Workspace

- `packages/core`: versioned plugin contracts, lifecycle, layout profiles, device facts, readiness, and reports.
- `apps/desktop`: React/Vite interface and Tauri read-only ADB bridge.
- `apps/site`: cross-platform download and product information page.

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

The native build is unsigned unless platform signing credentials are supplied deliberately. Pushing a `v*` tag after a remote repository exists triggers macOS and Windows draft builds; it does not publish the product site.

No remote repository, public deployment, signed package, or real-device compatibility claim is included by default.
