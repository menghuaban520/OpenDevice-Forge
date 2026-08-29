# Module package trust boundary

Status: cryptographic package verification and private versioned storage are implemented in the shared contract, Android and the native Tauri desktop boundary. The desktop trust store is empty unless a valid official key fingerprint is explicitly supplied at build time, so it fails closed today. Android atomic directory/pointer publication is verified on API 29; desktop atomic version publication and append-only last-valid pointer recovery are verified on macOS. Network acquisition and registry activation are still pending.

## Trust boundaries and assets

Untrusted catalog metadata, canonical manifest bytes, module payload bytes, publisher public keys and detached signatures cross from HTTPS into the desktop or Android host. HTTPS protects transport but is not the publisher trust root.

Assets to protect:

- the module identity, publisher identity, permissions, runtime entry and compatibility range;
- the currently installed and previously working package;
- host data reachable through declared permissions;
- kernel availability and the user's explicit enable/disable choices.

The signed message is the domain-separated byte sequence `OpenDevice Module Package v1\\0` followed by the exact canonical UTF-8 unsigned manifest. The unsigned manifest contains the payload SHA-256 and publisher-key SHA-256; only its self-referential `publisherSignature` field is null. The detached signature is injected only after verification.

P-256 ECDSA with SHA-256 is fixed for package v1. Android documents `SHA256withECDSA` from API 11 while platform Ed25519 starts at API 33, so package v1 remains verifiable by the Android 28+ host without bundling another cryptographic provider: <https://developer.android.com/reference/java/security/Signature>.

## STRIDE and fail-closed controls

| Threat | Abuse case | Required control |
| --- | --- | --- |
| Spoofing | An attacker substitutes their key while keeping the publisher name. | SHA-256 fingerprint must match the signed manifest and an explicit trust-store entry. Publisher display names never grant trust. |
| Tampering | Permissions, runtime entry, source, version or payload change after signing. | The complete canonical manifest is signed; the manifest binds the payload hash. Any byte change fails. |
| Repudiation | A module update later denies requesting a permission or version. | Audit the module ID, key fingerprint, version transition, added permissions and outcome; never log payload contents or secrets. |
| Information disclosure | A package silently adds network, microphone, location or model access. | Install and update always land disabled. Added permissions and runtime changes require review before enablement. |
| Denial of service | Huge manifests/payloads exhaust memory or disk; broken updates replace the working version. | Verify configured byte limits before parsing, stage before activation, retain one verified rollback version and clean failed staging files. |
| Elevation of privilege | A downloaded package declares itself builtin/protected or requests a runtime the host cannot isolate. | Package v1 rejects protected modules and permits only explicitly allowlisted host runtimes; current default is declarative only. |

Additional abuse cases:

- non-canonical JSON, duplicate keys, malformed UTF-8 and unknown schema fields fail before installation;
- remote source metadata must include a pinned revision and an HTTPS repository;
- update identity must match, version must increase, and `rollbackVersion` must equal the installed version;
- local import and community publisher trust require their own explicit confirmation flow and are not silently promoted to official trust.

## Installation transaction

The intended installation transaction is:

1. download into a bounded staging location;
2. verify canonical manifest, source/runtime policy, payload hash, publisher key, trust and signature;
3. validate host compatibility and compute the permission/runtime review;
4. sync/close staging files, atomically rename them to a versioned package directory, then update registry metadata;
5. keep the previous verified version for rollback and leave the new version disabled;
6. on any failure, preserve the current active version and remove only the exact staging directory.

Both hosts now implement the storage portion of steps 4–6: only a `VerifiedModulePackage` can enter a store, new installs and material permission/runtime/publisher/risk changes fail closed until an explicit review flag is supplied, and one prior version is retained for rollback. Android package directories and pointers use same-filesystem atomic moves and passed JVM plus API-29 smoke tests. Desktop package directories use same-filesystem rename; pointer changes append a synced immutable record, and readers choose the newest bounded valid record so a partial record leaves the prior pointer effective. Desktop behavior passed macOS Rust tests. Neither host yet downloads packages, re-verifies a selected stored version at registry activation, or wires the package pointer to its live registry. Directory-metadata durability across sudden power loss has not been proven. All of step 1 remains pending. Until those paths exist, the UI must continue to describe bundled reinstall actions as APK-owned and must not claim online installation.

## Capability contract

- Trigger: the user selects an online or imported module package.
- Provider: the shared `@opendevice/module-contract` verifier, with equivalent Android and native Tauri verifiers.
- Side effects: none during verification; each native store writes only bounded artifacts under its application-private versioned staging/package/state directories. Registry mutation remains a separate, not-yet-wired step.
- Verifier: one fixed Node-signed fixture accepted by Node, Android/JVM, API-29 device crypto and Rust/Tauri tests; adversarial tamper/size/schema/trust/compatibility tests; installed-package provenance and post-restart lifecycle checks.
- Rollback: reject before mutation on verification/review failure; each store can select the retained prior package, which must be re-verified before a future registry activation and remain disabled pending review.
