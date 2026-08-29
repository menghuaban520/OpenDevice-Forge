# Codex for Open Source application draft

Status: **not submitted**. Complete only after the repository is public and the maintainer has reviewed the final form.

Official program page: <https://developers.openai.com/community/codex-for-oss>

## Project

- Project name: OpenDevice Forge
- Public repository: `[add GitHub URL after publication]`
- Maintainer role: Core maintainer with write access
- License: MIT
- Current stage: Pre-release 0.1.0 preview; no public adoption metrics yet

## Short description

OpenDevice Forge is an open-source, device-first module platform for desktop and Android. It keeps phone-local LLM inference and remote API access as optional modules while preserving a small recoverable host. The project focuses on older and resource-constrained Android devices, explicit safety limits, read-only desktop inspection and signed declarative module packages instead of unrestricted scripts.

## Why the project matters

Many device and local-AI tools assume recent hardware, Root access or opaque one-off binaries. OpenDevice Forge explores a reusable, inspectable path for older Android phones: measured device facts, bounded on-device inference, explicit service authentication, publisher signatures, permission review and rollback. The same open module contract is verified by TypeScript, Android/Kotlin and desktop/Rust implementations.

## Current maintainer evidence

- MIT-licensed monorepo with desktop, Android, shared contract and product-site packages.
- Automated TypeScript, Rust/Tauri and Android checks.
- Cross-host P-256 package fixture and tamper/trust/compatibility tests.
- Real Android API-29 evidence for UI flows, package storage, local inference and service start/stop.
- Published security policy and explicit non-goals: no Root, lock bypass, CPU clock changes, thermal-protection bypass or arbitrary shell surface.

## How Codex support would be used

Six months of ChatGPT Pro with Codex would support cross-platform maintenance, issue triage, review, release preparation and compatibility work. API credits, if awarded, would be used only after a public, bounded maintainer workflow is implemented—for example pull-request review or release-evidence summarization—with secrets isolated in GitHub Actions and no contributor code executed with elevated credentials. Codex Security access would be most valuable for the signed package parser, JNI boundary, local HTTP service and fixed ADB bridge.

## Honest eligibility note

The repository is new and does not yet have public usage or contributor metrics. The application should not claim otherwise. The strongest current case is ecosystem relevance and security-conscious open-source infrastructure for older Android devices; adoption evidence can be added only after it exists.

## Fields the maintainer must provide at submission

- Name and account email
- Public GitHub repository URL
- GitHub profile and proof of write access
- Any real user, star, download or contributor metrics available at that time
- Acceptance of the current Codex for Open Source program terms
