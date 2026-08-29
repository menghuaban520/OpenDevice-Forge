# Security policy

## Supported versions

Only the latest `0.1.x` preview line is maintained while the project remains pre-release. Draft artifacts are preview builds; they are not Developer ID / Authenticode signed or notarized, and the Android artifact uses standard debug signing.

## Reporting a vulnerability

Please use GitHub's private **Report a vulnerability** flow when the repository enables private security advisories. Do not include a working exploit, device identifiers, account data, tokens, or personal files in a public issue.

Include the affected version, operating system, the smallest safe reproduction, expected impact, and whether the problem requires a connected phone. Allow maintainers time to reproduce and prepare a fix before public disclosure.

## Product boundary

The desktop bridge accepts only fixed read-only ADB operations. The Android host can run its visible, user-started local AI service and an authenticated local HTTP API, but LAN access is off by default and requires a fresh warning. Downloaded modules are limited to signed declarative packages, explicit publisher trust and permission review; arbitrary native package execution is not supported.

Rooting, bootloader unlocking, lock bypass, identifier modification, personal-file extraction, silent APK installation, CPU clock control, thermal-protection bypass and arbitrary shell execution are outside the supported boundary. A report that depends on adding one of these capabilities should first explain why a safer declarative or read-only design cannot meet the need.
