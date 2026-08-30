# Codex for Open Source application draft

Status: **not submitted**. Source is now public for review; keep the application unsubmitted until the candidate pull request and release evidence have been reviewed and the maintainer approves the final form.

Official program page: <https://developers.openai.com/community/codex-for-oss>

## Project

- Project name: OpenDevice Forge
- Public repository: <https://github.com/menghuaban520/OpenDevice-Forge>
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

The official program page checked on 2026-08-29 says core maintainers or maintainers of widely used public projects should apply, and explicitly invites ecosystem-relevant projects that do not otherwise fit those criteria to explain their importance. It does not guarantee acceptance, credits or Codex Security access.

## Fields the maintainer must provide at submission

- First and last name
- Email associated with the ChatGPT account
- Public GitHub username and repository URL
- Primary-maintainer or core-maintainer role
- Any real user, star, download or contributor metrics available at that time
- Whether to request Codex Security and/or API credits
- OpenAI Organization ID if requesting API credits
- Acceptance of the current Codex for Open Source program terms

## Form-ready copy

### Why does this repository qualify? (maximum 500 characters)

OpenDevice Forge is a new MIT-licensed device-first module platform for desktop and Android. It targets older and resource-constrained phones without requiring Root, thermal bypasses, or opaque binaries. Its shared signed-module contract is implemented across TypeScript, Kotlin, and Rust, with explicit permissions, rollback, safety limits, and real Android API 29 evidence. It has no public adoption metrics yet; its case is ecosystem relevance and security-conscious local AI infrastructure.

### How will you use API credits? (maximum 500 characters)

API credits would support a bounded GitHub Actions workflow for pull-request review and release-evidence summarization. Secrets would remain in repository secrets, untrusted contributor code would not run with elevated credentials, and generated findings would require maintainer review before any change. Credits would not fund user-facing inference or telemetry.

### Anything else we should know? (maximum 500 characters)

Conditional Codex Security access would be used to review the signed package parser, JNI boundary, local authenticated HTTP service, and fixed-argument ADB bridge. The repository is pre-release and currently has no stars, downloads, or external contributors; the application will not claim otherwise.
