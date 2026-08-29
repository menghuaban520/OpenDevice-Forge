use std::collections::HashSet;

use base64::{Engine as _, engine::general_purpose::STANDARD as BASE64};
use p256::{
    PublicKey,
    ecdsa::{Signature, VerifyingKey, signature::Verifier},
    pkcs8::DecodePublicKey,
};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use tauri::State;

const SIGNATURE_DOMAIN: &[u8] = b"OpenDevice Module Package v1\0";
const MAX_ENVELOPE_BYTES: usize = 12 * 1024 * 1024;
const MAX_MANIFEST_BYTES: usize = 256 * 1024;
const MAX_PAYLOAD_BYTES: usize = 8 * 1024 * 1024;
const MAX_KEY_BYTES: usize = 4 * 1024;
const MAX_SIGNATURE_BYTES: usize = 512;
const MODULE_SCHEMA: &str =
    include_str!("../../../../packages/core/schema/opendevice.module.v1.schema.json");

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct ModuleHost {
    pub kernel_version: String,
    pub android_sdk: u64,
    pub abis: HashSet<String>,
    pub available_runtimes: HashSet<String>,
}

#[derive(Debug, Clone, Default)]
pub(crate) struct PublisherTrustStore {
    keys: HashSet<String>,
}

impl PublisherTrustStore {
    pub(crate) fn from_build_configuration() -> Self {
        let keys = option_env!("OPENDEVICE_OFFICIAL_PUBLISHER_SHA256")
            .filter(|value| is_sha256(value))
            .map(|value| HashSet::from([value.to_owned()]))
            .unwrap_or_default();
        Self { keys }
    }

    pub(crate) fn keys(&self) -> &HashSet<String> {
        &self.keys
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum ModulePackageStatus {
    Verified,
    PackageTooLarge,
    EnvelopeInvalid,
    EncodingInvalid,
    ManifestJsonInvalid,
    ManifestNotCanonical,
    ManifestInvalid,
    NotPackage,
    SignatureNotDetached,
    HashMismatch,
    PublisherKeyMismatch,
    PublisherKeyInvalid,
    SignatureMismatch,
    UntrustedPublisher,
    SourceForbidden,
    RuntimeForbidden,
    ProtectedPackageForbidden,
    HostIncompatible,
}

#[derive(Debug, Clone)]
pub(crate) struct VerifiedModulePackage {
    pub manifest: Value,
    pub unsigned_manifest_bytes: Vec<u8>,
    pub payload_bytes: Vec<u8>,
    pub publisher_public_key_spki: Vec<u8>,
    pub publisher_signature: Vec<u8>,
    pub publisher_key_sha256: String,
}

#[derive(Debug, Clone)]
pub(crate) struct ModulePackageVerification {
    pub status: ModulePackageStatus,
    pub verified_package: Option<VerifiedModulePackage>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct ModuleHostInput {
    kernel_version: String,
    android_sdk: u64,
    abis: Vec<String>,
    available_runtimes: Vec<String>,
}

impl ModuleHostInput {
    pub(crate) fn into_host(self) -> ModuleHost {
        ModuleHost {
            kernel_version: self.kernel_version,
            android_sdk: self.android_sdk,
            abis: self.abis.into_iter().collect(),
            available_runtimes: self.available_runtimes.into_iter().collect(),
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ModulePackageVerificationResponse {
    status: ModulePackageStatus,
    module_id: Option<String>,
    version: Option<String>,
    publisher: Option<String>,
    publisher_key_sha256: Option<String>,
    manifest_bytes: Option<usize>,
    payload_bytes: Option<usize>,
    publisher_key_bytes: Option<usize>,
    signature_bytes: Option<usize>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct ModulePackageEnvelope {
    package_version: String,
    manifest: String,
    payload_base64: String,
    publisher_public_key_spki_base64: String,
    publisher_signature_base64: String,
}

#[tauri::command]
pub(crate) fn verify_module_package(
    envelope_json: String,
    host: ModuleHostInput,
    trust_store: State<'_, PublisherTrustStore>,
) -> ModulePackageVerificationResponse {
    let host = host.into_host();
    let verification = verify_package_envelope(envelope_json.as_bytes(), &host, trust_store.keys());
    let package = verification.verified_package.as_ref();
    ModulePackageVerificationResponse {
        status: verification.status,
        module_id: package
            .and_then(|value| value.manifest.get("id"))
            .and_then(Value::as_str)
            .map(str::to_owned),
        version: package
            .and_then(|value| value.manifest.get("version"))
            .and_then(Value::as_str)
            .map(str::to_owned),
        publisher: package
            .and_then(|value| value.manifest.get("publisher"))
            .and_then(Value::as_str)
            .map(str::to_owned),
        publisher_key_sha256: package.map(|value| value.publisher_key_sha256.clone()),
        manifest_bytes: package.map(|value| value.unsigned_manifest_bytes.len()),
        payload_bytes: package.map(|value| value.payload_bytes.len()),
        publisher_key_bytes: package.map(|value| value.publisher_public_key_spki.len()),
        signature_bytes: package.map(|value| value.publisher_signature.len()),
    }
}

pub(crate) fn verify_package_envelope(
    envelope_bytes: &[u8],
    host: &ModuleHost,
    trusted_publisher_key_sha256: &HashSet<String>,
) -> ModulePackageVerification {
    if envelope_bytes.is_empty() || envelope_bytes.len() > MAX_ENVELOPE_BYTES {
        return result(ModulePackageStatus::PackageTooLarge);
    }
    let envelope_text = match std::str::from_utf8(envelope_bytes) {
        Ok(value) => value,
        Err(_) => return result(ModulePackageStatus::EncodingInvalid),
    };
    let envelope: ModulePackageEnvelope = match serde_json::from_str(envelope_text) {
        Ok(value) => value,
        Err(_) => return result(ModulePackageStatus::EnvelopeInvalid),
    };
    if envelope.package_version != "1" || envelope.manifest.is_empty() {
        return result(ModulePackageStatus::EnvelopeInvalid);
    }
    let manifest_bytes = envelope.manifest.as_bytes();
    if manifest_bytes.len() > MAX_MANIFEST_BYTES {
        return result(ModulePackageStatus::PackageTooLarge);
    }
    let payload_bytes = match decode_canonical_base64(&envelope.payload_base64, MAX_PAYLOAD_BYTES) {
        Some(value) => value,
        None => return result(ModulePackageStatus::EnvelopeInvalid),
    };
    let publisher_public_key_spki =
        match decode_canonical_base64(&envelope.publisher_public_key_spki_base64, MAX_KEY_BYTES) {
            Some(value) => value,
            None => return result(ModulePackageStatus::EnvelopeInvalid),
        };
    let publisher_signature =
        match decode_canonical_base64(&envelope.publisher_signature_base64, MAX_SIGNATURE_BYTES) {
            Some(value) => value,
            None => return result(ModulePackageStatus::EnvelopeInvalid),
        };

    let unsigned: Value = match serde_json::from_str(&envelope.manifest) {
        Ok(value) => value,
        Err(_) => return result(ModulePackageStatus::ManifestJsonInvalid),
    };
    let canonical = match canonical_json(&unsigned) {
        Some(value) => value,
        None => return result(ModulePackageStatus::ManifestJsonInvalid),
    };
    if canonical.as_bytes() != manifest_bytes {
        return result(ModulePackageStatus::ManifestNotCanonical);
    }
    let integrity = match unsigned.get("integrity").and_then(Value::as_object) {
        Some(value) => value,
        None => return result(ModulePackageStatus::ManifestInvalid),
    };
    if integrity.get("kind").and_then(Value::as_str) != Some("package") {
        return result(ModulePackageStatus::NotPackage);
    }
    if !matches!(integrity.get("publisherSignature"), Some(Value::Null)) {
        return result(ModulePackageStatus::SignatureNotDetached);
    }
    if integrity.get("sha256").and_then(Value::as_str) != Some(&sha256(&payload_bytes)) {
        return result(ModulePackageStatus::HashMismatch);
    }
    let publisher_key_sha256 = sha256(&publisher_public_key_spki);
    if integrity.get("publisherKeySha256").and_then(Value::as_str)
        != Some(publisher_key_sha256.as_str())
    {
        return result(ModulePackageStatus::PublisherKeyMismatch);
    }
    if !source_allowed(&unsigned) {
        return result(ModulePackageStatus::SourceForbidden);
    }
    let runtime = unsigned.pointer("/runtime/kind").and_then(Value::as_str);
    if runtime != Some("declarative") || !host.available_runtimes.contains("declarative") {
        return result(ModulePackageStatus::RuntimeForbidden);
    }
    if unsigned.get("protected").and_then(Value::as_bool) != Some(false) {
        return result(ModulePackageStatus::ProtectedPackageForbidden);
    }
    if !trusted_publisher_key_sha256.contains(&publisher_key_sha256) {
        return result(ModulePackageStatus::UntrustedPublisher);
    }

    let public_key = match PublicKey::from_public_key_der(&publisher_public_key_spki) {
        Ok(value) => value,
        Err(_) => return result(ModulePackageStatus::PublisherKeyInvalid),
    };
    let signature = match Signature::from_der(&publisher_signature) {
        Ok(value) => value,
        Err(_) => return result(ModulePackageStatus::SignatureMismatch),
    };
    let mut signed_message = Vec::with_capacity(SIGNATURE_DOMAIN.len() + manifest_bytes.len());
    signed_message.extend_from_slice(SIGNATURE_DOMAIN);
    signed_message.extend_from_slice(manifest_bytes);
    if VerifyingKey::from(public_key)
        .verify(&signed_message, &signature)
        .is_err()
    {
        return result(ModulePackageStatus::SignatureMismatch);
    }

    let mut manifest = unsigned.clone();
    let Some(integrity) = manifest.get_mut("integrity").and_then(Value::as_object_mut) else {
        return result(ModulePackageStatus::ManifestInvalid);
    };
    integrity.insert(
        "publisherSignature".into(),
        Value::String(envelope.publisher_signature_base64),
    );
    let schema: Value = match serde_json::from_str(MODULE_SCHEMA) {
        Ok(value) => value,
        Err(_) => return result(ModulePackageStatus::ManifestInvalid),
    };
    if !jsonschema::is_valid(&schema, &manifest) {
        return result(ModulePackageStatus::ManifestInvalid);
    }
    if !host_compatible(&manifest, host) {
        return result(ModulePackageStatus::HostIncompatible);
    }

    ModulePackageVerification {
        status: ModulePackageStatus::Verified,
        verified_package: Some(VerifiedModulePackage {
            manifest,
            unsigned_manifest_bytes: manifest_bytes.to_vec(),
            payload_bytes,
            publisher_public_key_spki,
            publisher_signature,
            publisher_key_sha256,
        }),
    }
}

fn decode_canonical_base64(value: &str, maximum_bytes: usize) -> Option<Vec<u8>> {
    if value.is_empty() || value.len() > maximum_bytes.saturating_mul(4).saturating_add(4) {
        return None;
    }
    let decoded = BASE64.decode(value).ok()?;
    (decoded.len() <= maximum_bytes && BASE64.encode(&decoded) == value).then_some(decoded)
}

fn source_allowed(manifest: &Value) -> bool {
    let Some(source) = manifest.get("source").and_then(Value::as_object) else {
        return false;
    };
    if !matches!(
        source.get("kind").and_then(Value::as_str),
        Some("official" | "community" | "github")
    ) {
        return false;
    }
    let Some(repository) = source.get("repository").and_then(Value::as_str) else {
        return false;
    };
    let Ok(url) = tauri::Url::parse(repository) else {
        return false;
    };
    let revision_length = source
        .get("revision")
        .and_then(Value::as_str)
        .map(str::len)
        .unwrap_or_default();
    url.scheme() == "https" && url.host_str().is_some() && (7..=128).contains(&revision_length)
}

fn host_compatible(manifest: &Value, host: &ModuleHost) -> bool {
    let Some(minimum) = manifest.pointer("/kernel/min").and_then(Value::as_str) else {
        return false;
    };
    let Some(maximum) = manifest
        .pointer("/kernel/maxExclusive")
        .and_then(Value::as_str)
    else {
        return false;
    };
    if compare_versions(&host.kernel_version, minimum).is_none_or(|value| value < 0)
        || compare_versions(&host.kernel_version, maximum).is_none_or(|value| value >= 0)
    {
        return false;
    }
    if manifest
        .pointer("/platform/android/minSdk")
        .and_then(Value::as_u64)
        .is_none_or(|minimum_sdk| host.android_sdk < minimum_sdk)
    {
        return false;
    }
    let Some(abis) = manifest
        .pointer("/platform/android/abis")
        .and_then(Value::as_array)
    else {
        return false;
    };
    if !abis
        .iter()
        .filter_map(Value::as_str)
        .all(|abi| host.abis.contains(abi))
        || abis.iter().any(|abi| !abi.is_string())
    {
        return false;
    }
    if manifest.get("risk").and_then(Value::as_str) == Some("high")
        && manifest.get("audience").and_then(Value::as_str) != Some("advanced")
    {
        return false;
    }
    let Some(contributions) = manifest.get("contributes").and_then(Value::as_array) else {
        return false;
    };
    contributions.iter().all(|contribution| {
        let kind = contribution.get("type").and_then(Value::as_str);
        let placement = contribution.get("defaultPlacement").and_then(Value::as_str);
        matches!(
            (kind, placement),
            (Some("navigation"), Some("sidebar"))
                | (Some("overviewSection"), Some("overview"))
                | (Some("overviewAction"), Some("overview" | "shortcut"))
                | (Some("configuration"), Some("context"))
                | (Some("workflow"), Some("shortcut" | "context"))
                | (Some("service"), Some("service"))
                | (Some("screen"), Some("sidebar" | "fullscreen"))
                | (Some("deviceControl"), Some("context" | "fullscreen"))
                | (Some("companionApp"), Some("service"))
                | (Some("reportSection"), Some("report"))
        )
    })
}

fn compare_versions(left: &str, right: &str) -> Option<i8> {
    let left = version_core(left)?;
    let right = version_core(right)?;
    Some(
        left.iter()
            .zip(right.iter())
            .find_map(|(left, right)| (left != right).then_some(left.cmp(right)))
            .map_or(0, |ordering| match ordering {
                std::cmp::Ordering::Less => -1,
                std::cmp::Ordering::Equal => 0,
                std::cmp::Ordering::Greater => 1,
            }),
    )
}

fn version_core(value: &str) -> Option<[u64; 3]> {
    let core = value.split_once('-').map_or(value, |(core, _)| core);
    let mut parts = core.split('.').map(str::parse::<u64>);
    let version = [
        parts.next()?.ok()?,
        parts.next()?.ok()?,
        parts.next()?.ok()?,
    ];
    parts.next().is_none().then_some(version)
}

pub(crate) fn canonical_json(value: &Value) -> Option<String> {
    match value {
        Value::Null => Some("null".into()),
        Value::Bool(value) => Some(value.to_string()),
        Value::Number(value) => canonical_number(&value.to_string()),
        Value::String(value) => serde_json::to_string(value).ok(),
        Value::Array(values) => {
            let values = values
                .iter()
                .map(canonical_json)
                .collect::<Option<Vec<_>>>()?;
            Some(format!("[{}]", values.join(",")))
        }
        Value::Object(values) => {
            let mut entries = values.iter().collect::<Vec<_>>();
            entries.sort_by_key(|(key, _)| *key);
            let entries = entries
                .into_iter()
                .map(|(key, value)| {
                    Some(format!(
                        "{}:{}",
                        serde_json::to_string(key).ok()?,
                        canonical_json(value)?,
                    ))
                })
                .collect::<Option<Vec<_>>>()?;
            Some(format!("{{{}}}", entries.join(",")))
        }
    }
}

fn canonical_number(raw: &str) -> Option<String> {
    let (negative, unsigned) = raw
        .strip_prefix('-')
        .map_or((false, raw), |value| (true, value));
    let (mantissa, exponent) = unsigned
        .split_once(['e', 'E'])
        .map_or((unsigned, 0_i64), |(mantissa, exponent)| {
            (mantissa, exponent.parse::<i64>().ok().unwrap_or(i64::MAX))
        });
    if exponent == i64::MAX {
        return None;
    }
    let (integer, fraction) = mantissa.split_once('.').unwrap_or((mantissa, ""));
    if integer.is_empty()
        || !integer.bytes().all(|byte| byte.is_ascii_digit())
        || !fraction.bytes().all(|byte| byte.is_ascii_digit())
    {
        return None;
    }
    let digits = format!("{integer}{fraction}");
    let decimal_position = i64::try_from(integer.len()).ok()?.checked_add(exponent)?;
    let maximum = i64::try_from(MAX_MANIFEST_BYTES).ok()?;
    if digits.len() > MAX_MANIFEST_BYTES || !(-maximum..=maximum).contains(&decimal_position) {
        return None;
    }
    let mut plain = if decimal_position <= 0 {
        format!(
            "0.{}{}",
            "0".repeat(usize::try_from(-decimal_position).ok()?),
            digits,
        )
    } else if usize::try_from(decimal_position).ok()? >= digits.len() {
        format!(
            "{}{}",
            digits,
            "0".repeat(usize::try_from(decimal_position).ok()? - digits.len()),
        )
    } else {
        let position = usize::try_from(decimal_position).ok()?;
        format!("{}.{}", &digits[..position], &digits[position..])
    };
    if let Some((integer, fraction)) = plain.split_once('.') {
        let fraction = fraction.trim_end_matches('0');
        plain = if fraction.is_empty() {
            integer.into()
        } else {
            format!("{integer}.{fraction}")
        };
    }
    let normalized = plain.trim_start_matches('0');
    if plain.starts_with("0.") {
        plain = format!("0.{}", plain.trim_start_matches("0."));
    } else {
        plain = if normalized.is_empty() {
            "0"
        } else {
            normalized
        }
        .into();
    }
    if plain == "0" || plain.bytes().all(|byte| byte == b'0' || byte == b'.') {
        return Some("0".into());
    }
    Some(if negative { format!("-{plain}") } else { plain })
}

pub(crate) fn sha256(value: &[u8]) -> String {
    Sha256::digest(value)
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect()
}

fn is_sha256(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

fn result(status: ModulePackageStatus) -> ModulePackageVerification {
    ModulePackageVerification {
        status,
        verified_package: None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const INTEROP_FIXTURE: &str =
        include_str!("../../../../packages/module-contract/fixtures/signed-device-info-v1.json");

    fn host() -> ModuleHost {
        ModuleHost {
            kernel_version: "0.1.0".into(),
            android_sdk: 29,
            abis: HashSet::from(["arm64-v8a".into()]),
            available_runtimes: HashSet::from(["declarative".into()]),
        }
    }

    fn trusted_key() -> HashSet<String> {
        HashSet::from(["733bb4002ab8e962a0225eb13e5c625cfec4250d9319fbf11313e23b737fccf5".into()])
    }

    #[test]
    fn verifies_the_shared_node_signed_package() {
        let verification =
            verify_package_envelope(INTEROP_FIXTURE.as_bytes(), &host(), &trusted_key());

        assert_eq!(verification.status, ModulePackageStatus::Verified);
        let package = verification.verified_package.expect("verified package");
        assert_eq!(
            package.manifest.get("id").and_then(Value::as_str),
            Some("dev.opendevice.module.device-info"),
        );
        assert_eq!(
            package.publisher_key_sha256,
            trusted_key().into_iter().next().unwrap()
        );
        assert!(!package.payload_bytes.is_empty());
    }

    #[test]
    fn rejects_manifest_tampering_and_untrusted_publishers() {
        let mut envelope: ModulePackageEnvelope = serde_json::from_str(INTEROP_FIXTURE).unwrap();
        envelope.manifest = envelope.manifest.replace(
            "\"permissions\":[\"device.read\"]",
            "\"permissions\":[\"device.read\",\"network.outbound\"]",
        );
        let tampered = serde_json::to_vec(&envelope).unwrap();

        assert_eq!(
            verify_package_envelope(&tampered, &host(), &trusted_key()).status,
            ModulePackageStatus::SignatureMismatch,
        );
        assert_eq!(
            verify_package_envelope(INTEROP_FIXTURE.as_bytes(), &host(), &HashSet::new()).status,
            ModulePackageStatus::UntrustedPublisher,
        );
    }

    #[test]
    fn rejects_payload_corruption_and_incompatible_hosts() {
        let mut envelope: ModulePackageEnvelope = serde_json::from_str(INTEROP_FIXTURE).unwrap();
        envelope.payload_base64 = BASE64.encode(b"corrupt");
        let corrupted = serde_json::to_vec(&envelope).unwrap();

        assert_eq!(
            verify_package_envelope(&corrupted, &host(), &trusted_key()).status,
            ModulePackageStatus::HashMismatch,
        );
        let mut incompatible = host();
        incompatible.android_sdk = 27;
        assert_eq!(
            verify_package_envelope(INTEROP_FIXTURE.as_bytes(), &incompatible, &trusted_key(),)
                .status,
            ModulePackageStatus::HostIncompatible,
        );
    }

    #[test]
    fn canonicalizes_cross_host_decimal_edges() {
        assert_eq!(canonical_number("1e-7").as_deref(), Some("0.0000001"));
        assert_eq!(
            canonical_number("1e21").as_deref(),
            Some("1000000000000000000000"),
        );
        assert_eq!(canonical_number("-0.0").as_deref(), Some("0"));
        assert_eq!(canonical_number("1e1000000000"), None);
    }

    #[test]
    fn build_trust_configuration_fails_closed_without_a_valid_fingerprint() {
        let trust = PublisherTrustStore::from_build_configuration();
        assert!(trust.keys.iter().all(|value| is_sha256(value)));
    }
}
