# Visual fidelity ledger

The accepted v3 concepts were treated as structural specifications, not as images to trace. The implementation uses original assets and a native device-utility visual grammar.

## Surface comparisons

| Check | Accepted concept | Implemented evidence | Result |
| --- | --- | --- | --- |
| Overall frame | Dark device rail, large white work surface, thin title/status bars | `desktop-overview.png` | Preserved. The rail remains the single dark anchor; panels no longer read as an AI dashboard. |
| Device overview | Task-first actions above facts, storage, battery, findings, and services | `device-overview-v3-imazing-direction.png` → `desktop-overview.png` | Preserved hierarchy with denser native controls and explicit source labels. |
| Plugin market | Catalog, manifest/risk detail, lifecycle control, permission/config rail | `plugin-market-v3-microkernel.png` → `plugin-market.png` | Preserved three-zone structure. Added functional Installed/Discover/Updates/Sources tabs. |
| Layout editor | Independent editor with preview, placement list, properties, reset/cancel/save | `plugin-layout-editor-v3.png` → `layout-editor.png` | Preserved. Draft edits remain reversible and profile-specific. |
| Compact desktop fit | No clipped controls at 1280 × 800 | `layout-editor-1280x800.png` | Passed after narrowing rail/detail columns and keeping workspace overflow local. |
| Typography and palette | Platform UI font, mineral blue selection, neutral hairlines, no gradients as decoration | All implementation screenshots | Preserved. Blue is reserved for selection/action; warnings and destructive actions remain semantic. |
| Icon system | Consistent line icons, no emoji controls | All implementation screenshots | Preserved with Lucide line icons and an original flat application mark. |
| Responsive website | Product proof and honest availability at desktop and phone widths | `site-desktop.png`, `site-mobile.png` | Passed at 1440 × 900 and 390 × 844 without horizontal overflow. |

## Copy-diff audit

- The accepted early mock showed `HUAWEI nova 9`; the implementation uses the user's corrected `HUAWEI nova 7 SE 5G 乐活版` and labels the model code/reference facts as public or demo data until a live probe replaces them.
- “AI 节点” describes a plugin manifest and readiness/configuration surface only. No local model download, inference server, remote API, or performance result is claimed.
- “远程网关” remains planning/configuration copy. No tunnel, public endpoint, or external account was created.
- Root appears only in Discover as a risk/boundary guide. There is no Root, unlock, flash, install, or arbitrary-command call to action.
- Download buttons explicitly say `等待首个已验证发布包`; the website contains no dead or misleading download URL.
- Live values say `真机读取`, public values say `公开参考`, demo values say `演示视图`, and missing values stay unknown rather than being filled with likely specifications.

## Material refinements after real-browser review

1. Fixed an input overlay that intercepted the plugin enable/disable switch.
2. Rebalanced the desktop columns so the Layout Editor remains usable at 1280 × 800.
3. Replaced the first metallic application icon direction with the same flat, restrained mark used in the desktop title bar and website.
4. Kept the website as a long-form product page with borders and editorial spacing instead of repeating dashboard cards.
5. Kept all release and device-compatibility gaps visible in the page itself, not only in developer documentation.

## Android node control surface

- The phone companion uses the same factual hierarchy: prerequisites first, then the one current action, followed by local chat and diagnosis.
- Four Material outline icons replace the earlier single-character placeholder icons.
- LAN exposure uses a full-screen, fresh confirmation with the cleartext-HTTP warning; raw client tokens exist only in the dismissible one-time surface.
- Public gateway, Root Broker, Marketplace, Creator, and Script sandbox remain labeled `尚未实现`; the Modules screen has no dead search/install control.
- Source review, JVM tests, lint, and both APK builds passed. No phone or emulator was attached, so visual fidelity and touch behavior are not marked verified yet.
