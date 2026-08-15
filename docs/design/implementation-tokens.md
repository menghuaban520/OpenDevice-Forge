# Accepted v3 implementation tokens

The accepted v3 concepts are the implementation specification. The desktop UI uses native device-management grammar: broad white work surfaces, one dark device rail, thin neutral dividers, compact controls, and a single mineral-blue action color.

## Visual tokens

- Window reference: `1600 × 1000`; minimum supported content frame: `1080 × 720`.
- Title bar: `48px`; device rail: `318px`; page toolbar: `72px`; bottom status: `48px`.
- Canvas: `#f5f6f8`; work surface: `#ffffff`; device rail: `#22323d`; rail secondary: `#2b3c47`.
- Text: `#16212b`; secondary: `#65717c`; hairline: `#dfe3e7`; selected: `#1473e6`; warning: `#d97706`; destructive: `#d14343`.
- Radius: `6px` controls, `8px` panels, `10px` large surfaces. Shadows are limited to floating menus and the phone render.
- Font: platform UI stack; base `14px`; dense metadata `12px`; device name `20px`; page title `22px`.
- Icons: Lucide line icons, `1.7px` stroke, normally `18–24px`; never use emoji as interface icons.
- Motion: `120–180ms` opacity/color/transform transitions; disabled under `prefers-reduced-motion`.

## Component inventory

- Native-style title bar and three window marks.
- Device rail with original unbranded phone render, connection state, fixed kernel navigation, enabled-plugin section, and plugin-source action.
- Overview toolbar with breadcrumb, search, refresh, task history, and help.
- Device overview with five large task actions, fact table, action list, capacity/battery panels, findings, and plugin/service state.
- Plugin Market with tabs, searchable catalog list, manifest/source/risk detail, reversible lifecycle controls, contribution placement toggles, readiness facts, and a scoped configuration rail.
- Layout Editor with device/profile selectors, live rail preview, per-slot visibility/order controls, selected-plugin properties, reset, cancel, and save actions.
- Bottom safety strip that always exposes read-only/safe-mode status and never hides write boundaries.

## Copy rules

- “演示视图 · 未连接真机” appears anywhere reference/demo values are shown.
- Public specifications say “公开参考”; live probes say “真机读取”; unknown values say “待读取/无法确认”.
- Root is information-only and must never use an install/unlock/flash call to action.
- Remote gateway copy says planning/configuration until a local service is actually running.

