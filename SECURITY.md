# Security policy

## Supported versions

Only the latest local MVP line is maintained while the project remains pre-release. There is no signed or publicly supported installer yet.

## Reporting a vulnerability

Please use GitHub's private **Report a vulnerability** flow when the repository enables private security advisories. Do not include a working exploit, device identifiers, account data, tokens, or personal files in a public issue.

Include the affected version, operating system, the smallest safe reproduction, expected impact, and whether the problem requires a connected phone. Allow maintainers time to reproduce and prepare a fix before public disclosure.

## Product boundary

The MVP accepts only fixed read-only ADB operations. Rooting, bootloader unlocking, lock bypass, identifier modification, personal-file extraction, APK installation, and arbitrary command execution are outside the supported boundary. A report that depends on adding one of these capabilities should first explain why a safer declarative or read-only design cannot meet the need.
