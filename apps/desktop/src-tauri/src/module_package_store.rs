use std::{
    collections::BTreeSet,
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    path::{Path, PathBuf},
    sync::{
        Mutex,
        atomic::{AtomicU64, Ordering},
    },
    time::{SystemTime, UNIX_EPOCH},
};

use base64::{Engine as _, engine::general_purpose::STANDARD as BASE64};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use tauri::{AppHandle, Manager, State};

use crate::module_package::{
    ModuleHostInput, ModulePackageStatus, PublisherTrustStore, VerifiedModulePackage,
    canonical_json, sha256, verify_package_envelope,
};

const MAX_MANIFEST_BYTES: u64 = 256 * 1024;
const MAX_PAYLOAD_BYTES: u64 = 8 * 1024 * 1024;
const MAX_KEY_BYTES: u64 = 4 * 1024;
const MAX_SIGNATURE_BYTES: u64 = 512;
const MAX_POINTER_BYTES: u64 = 4 * 1024;
const RETAINED_POINTER_RECORDS: usize = 16;
static STORE_LOCK: Mutex<()> = Mutex::new(());
static UNIQUE_COUNTER: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum ModulePackageStoreStatus {
    Stored,
    Unchanged,
    RolledBack,
    RollbackUnavailable,
    IdentityInvalid,
    ArtifactInvalid,
    VersionConflict,
    VersionNotNewer,
    RollbackVersionMismatch,
    ReviewRequired,
    StorageError,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ModulePackageReview {
    added_permissions: Vec<String>,
    runtime_changed: bool,
    publisher_changed: bool,
    risk_changed: bool,
}

impl ModulePackageReview {
    fn required(&self) -> bool {
        !self.added_permissions.is_empty()
            || self.runtime_changed
            || self.publisher_changed
            || self.risk_changed
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ModulePackageStoreResult {
    status: ModulePackageStoreStatus,
    review: Option<ModulePackageReview>,
    current_version: Option<String>,
    previous_version: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ModulePackageInstallResponse {
    verification_status: ModulePackageStatus,
    store: Option<ModulePackageStoreResult>,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct StoredModulePointer {
    current_version: String,
    previous_version: Option<String>,
}

struct ModulePackageStore {
    root: PathBuf,
}

#[tauri::command]
pub(crate) fn install_module_package(
    envelope_json: String,
    host: ModuleHostInput,
    review_accepted: bool,
    app: AppHandle,
    trust_store: State<'_, PublisherTrustStore>,
) -> ModulePackageInstallResponse {
    let verification = verify_package_envelope(
        envelope_json.as_bytes(),
        &host.into_host(),
        trust_store.keys(),
    );
    if verification.status != ModulePackageStatus::Verified {
        return ModulePackageInstallResponse {
            verification_status: verification.status,
            store: None,
        };
    }
    let Some(package) = verification.verified_package else {
        return ModulePackageInstallResponse {
            verification_status: ModulePackageStatus::ManifestInvalid,
            store: None,
        };
    };
    let store = app
        .path()
        .app_data_dir()
        .ok()
        .map(|root| ModulePackageStore::new(root.join("module-packages-v1")))
        .map_or_else(
            || store_result(ModulePackageStoreStatus::StorageError, None, None),
            |store| store.store(&package, review_accepted),
        );
    ModulePackageInstallResponse {
        verification_status: ModulePackageStatus::Verified,
        store: Some(store),
    }
}

#[tauri::command]
pub(crate) fn rollback_module_package(
    module_id: String,
    review_accepted: bool,
    app: AppHandle,
) -> ModulePackageStoreResult {
    app.path()
        .app_data_dir()
        .ok()
        .map(|root| ModulePackageStore::new(root.join("module-packages-v1")))
        .map_or_else(
            || store_result(ModulePackageStoreStatus::StorageError, None, None),
            |store| store.rollback(&module_id, review_accepted),
        )
}

impl ModulePackageStore {
    fn new(root: PathBuf) -> Self {
        Self { root }
    }

    fn store(
        &self,
        package: &VerifiedModulePackage,
        review_accepted: bool,
    ) -> ModulePackageStoreResult {
        let Ok(_guard) = STORE_LOCK.lock() else {
            return store_result(ModulePackageStoreStatus::StorageError, None, None);
        };
        let Some(module_id) = string_at(&package.manifest, "/id") else {
            return store_result(ModulePackageStoreStatus::IdentityInvalid, None, None);
        };
        let Some(version) = string_at(&package.manifest, "/version") else {
            return store_result(ModulePackageStoreStatus::IdentityInvalid, None, None);
        };
        if !safe_module_id(module_id) || !safe_version(version) {
            return store_result(ModulePackageStoreStatus::IdentityInvalid, None, None);
        }
        if !artifact_matches(package) {
            return store_result(ModulePackageStoreStatus::ArtifactInvalid, None, None);
        }

        let existing = self.pointer(module_id);
        if let Some(pointer) = &existing {
            let Some(comparison) = compare_versions(version, &pointer.current_version) else {
                return store_result(ModulePackageStoreStatus::IdentityInvalid, None, None);
            };
            if comparison < 0 {
                return store_result(
                    ModulePackageStoreStatus::VersionNotNewer,
                    None,
                    Some(pointer),
                );
            }
            if comparison == 0 {
                let status = if self.stored_version_matches(module_id, version, package) {
                    ModulePackageStoreStatus::Unchanged
                } else {
                    ModulePackageStoreStatus::VersionConflict
                };
                return store_result(status, None, Some(pointer));
            }
            if string_at(&package.manifest, "/integrity/rollbackVersion")
                != Some(pointer.current_version.as_str())
            {
                return store_result(
                    ModulePackageStoreStatus::RollbackVersionMismatch,
                    None,
                    Some(pointer),
                );
            }
        } else if package
            .manifest
            .pointer("/integrity/rollbackVersion")
            .is_some_and(|value| !value.is_null())
        {
            return store_result(
                ModulePackageStoreStatus::RollbackVersionMismatch,
                None,
                None,
            );
        }

        let current_manifest = existing
            .as_ref()
            .and_then(|pointer| self.load_manifest(module_id, &pointer.current_version));
        if existing.is_some() && current_manifest.is_none() {
            return store_result(
                ModulePackageStoreStatus::ArtifactInvalid,
                None,
                existing.as_ref(),
            );
        }
        let review = package_review(current_manifest.as_ref(), &package.manifest);
        if review.required() && !review_accepted {
            return store_result(
                ModulePackageStoreStatus::ReviewRequired,
                Some(review),
                existing.as_ref(),
            );
        }

        let version_directory = self.version_directory(module_id, version);
        if version_directory.exists() && !self.stored_version_matches(module_id, version, package) {
            return store_result(
                ModulePackageStoreStatus::VersionConflict,
                None,
                existing.as_ref(),
            );
        }
        if !version_directory.exists() && self.publish_version(package, &version_directory).is_err()
        {
            return store_result(
                ModulePackageStoreStatus::StorageError,
                None,
                existing.as_ref(),
            );
        }
        let pointer = StoredModulePointer {
            current_version: version.to_owned(),
            previous_version: existing.as_ref().map(|value| value.current_version.clone()),
        };
        if self.write_pointer(module_id, &pointer).is_err() {
            return store_result(
                ModulePackageStoreStatus::StorageError,
                None,
                existing.as_ref(),
            );
        }
        store_result(ModulePackageStoreStatus::Stored, None, Some(&pointer))
    }

    fn rollback(&self, module_id: &str, review_accepted: bool) -> ModulePackageStoreResult {
        let Ok(_guard) = STORE_LOCK.lock() else {
            return store_result(ModulePackageStoreStatus::StorageError, None, None);
        };
        if !safe_module_id(module_id) {
            return store_result(ModulePackageStoreStatus::IdentityInvalid, None, None);
        }
        let Some(current) = self.pointer(module_id) else {
            return store_result(ModulePackageStoreStatus::RollbackUnavailable, None, None);
        };
        let Some(previous) = current.previous_version.as_deref() else {
            return store_result(
                ModulePackageStoreStatus::RollbackUnavailable,
                None,
                Some(&current),
            );
        };
        let Some(current_manifest) = self.load_manifest(module_id, &current.current_version) else {
            return store_result(
                ModulePackageStoreStatus::ArtifactInvalid,
                None,
                Some(&current),
            );
        };
        let Some(previous_manifest) = self.load_manifest(module_id, previous) else {
            return store_result(
                ModulePackageStoreStatus::ArtifactInvalid,
                None,
                Some(&current),
            );
        };
        let review = package_review(Some(&current_manifest), &previous_manifest);
        if review.required() && !review_accepted {
            return store_result(
                ModulePackageStoreStatus::ReviewRequired,
                Some(review),
                Some(&current),
            );
        }
        let pointer = StoredModulePointer {
            current_version: previous.to_owned(),
            previous_version: Some(current.current_version.clone()),
        };
        if self.write_pointer(module_id, &pointer).is_err() {
            return store_result(ModulePackageStoreStatus::StorageError, None, Some(&current));
        }
        store_result(ModulePackageStoreStatus::RolledBack, None, Some(&pointer))
    }

    fn publish_version(
        &self,
        package: &VerifiedModulePackage,
        version_directory: &Path,
    ) -> std::io::Result<()> {
        fs::create_dir_all(self.root.join("staging"))?;
        let staging = self.root.join("staging").join(unique_name("package"));
        fs::create_dir(&staging)?;
        let published = (|| {
            write_synced(
                &staging.join("manifest.json"),
                &package.unsigned_manifest_bytes,
            )?;
            write_synced(&staging.join("payload.bin"), &package.payload_bytes)?;
            write_synced(
                &staging.join("publisher-key.spki"),
                &package.publisher_public_key_spki,
            )?;
            write_synced(&staging.join("signature.der"), &package.publisher_signature)?;
            fs::create_dir_all(
                version_directory
                    .parent()
                    .ok_or_else(|| std::io::Error::other("version directory has no parent"))?,
            )?;
            fs::rename(&staging, version_directory)
        })();
        if staging.exists() {
            let _ = fs::remove_dir_all(&staging);
        }
        published
    }

    fn pointer(&self, module_id: &str) -> Option<StoredModulePointer> {
        let directory = self.pointer_directory(module_id);
        let mut entries = fs::read_dir(directory)
            .ok()?
            .filter_map(Result::ok)
            .filter(|entry| entry.file_name().to_string_lossy().ends_with(".json"))
            .collect::<Vec<_>>();
        entries.sort_by_key(|entry| std::cmp::Reverse(entry.file_name()));
        entries.into_iter().find_map(|entry| {
            let bytes = read_bounded(&entry.path(), 1, MAX_POINTER_BYTES).ok()?;
            let pointer: StoredModulePointer = serde_json::from_slice(&bytes).ok()?;
            (safe_version(&pointer.current_version)
                && pointer.previous_version.as_deref().is_none_or(safe_version)
                && self
                    .version_directory(module_id, &pointer.current_version)
                    .is_dir()
                && pointer
                    .previous_version
                    .as_deref()
                    .is_none_or(|version| self.version_directory(module_id, version).is_dir()))
            .then_some(pointer)
        })
    }

    fn write_pointer(&self, module_id: &str, pointer: &StoredModulePointer) -> std::io::Result<()> {
        let directory = self.pointer_directory(module_id);
        fs::create_dir_all(&directory)?;
        let path = directory.join(format!("{}.json", unique_name("pointer")));
        let bytes = serde_json::to_vec(pointer)?;
        write_synced(&path, &bytes)?;

        let mut entries = fs::read_dir(&directory)?
            .filter_map(Result::ok)
            .filter(|entry| entry.file_name().to_string_lossy().ends_with(".json"))
            .collect::<Vec<_>>();
        entries.sort_by_key(|entry| std::cmp::Reverse(entry.file_name()));
        for entry in entries.into_iter().skip(RETAINED_POINTER_RECORDS) {
            let _ = fs::remove_file(entry.path());
        }
        Ok(())
    }

    fn stored_version_matches(
        &self,
        module_id: &str,
        version: &str,
        package: &VerifiedModulePackage,
    ) -> bool {
        self.load(module_id, version).is_some_and(|stored| {
            stored.manifest == package.unsigned_manifest_bytes
                && stored.key == package.publisher_public_key_spki
                && stored.signature == package.publisher_signature
                && sha256(&stored.payload)
                    == string_at(&package.manifest, "/integrity/sha256").unwrap_or_default()
        })
    }

    fn load_manifest(&self, module_id: &str, version: &str) -> Option<Value> {
        let stored = self.load(module_id, version)?;
        serde_json::from_slice(&stored.manifest).ok()
    }

    fn load(&self, module_id: &str, version: &str) -> Option<StoredComponents> {
        if !safe_module_id(module_id) || !safe_version(version) {
            return None;
        }
        let directory = self.version_directory(module_id, version);
        Some(StoredComponents {
            manifest: read_bounded(&directory.join("manifest.json"), 1, MAX_MANIFEST_BYTES).ok()?,
            payload: read_bounded(&directory.join("payload.bin"), 0, MAX_PAYLOAD_BYTES).ok()?,
            key: read_bounded(&directory.join("publisher-key.spki"), 1, MAX_KEY_BYTES).ok()?,
            signature: read_bounded(&directory.join("signature.der"), 1, MAX_SIGNATURE_BYTES)
                .ok()?,
        })
    }

    fn version_directory(&self, module_id: &str, version: &str) -> PathBuf {
        self.root.join("packages").join(module_id).join(version)
    }

    fn pointer_directory(&self, module_id: &str) -> PathBuf {
        self.root.join("state").join(module_id)
    }
}

struct StoredComponents {
    manifest: Vec<u8>,
    payload: Vec<u8>,
    key: Vec<u8>,
    signature: Vec<u8>,
}

fn artifact_matches(package: &VerifiedModulePackage) -> bool {
    if string_at(&package.manifest, "/integrity/kind") != Some("package")
        || string_at(&package.manifest, "/runtime/kind") != Some("declarative")
        || package.manifest.get("protected").and_then(Value::as_bool) != Some(false)
        || string_at(&package.manifest, "/integrity/sha256")
            != Some(&sha256(&package.payload_bytes))
        || string_at(&package.manifest, "/integrity/publisherKeySha256")
            != Some(&sha256(&package.publisher_public_key_spki))
        || string_at(&package.manifest, "/integrity/publisherKeySha256")
            != Some(package.publisher_key_sha256.as_str())
        || string_at(&package.manifest, "/integrity/publisherSignature")
            != Some(BASE64.encode(&package.publisher_signature).as_str())
    {
        return false;
    }
    let mut unsigned = package.manifest.clone();
    let Some(integrity) = unsigned.get_mut("integrity").and_then(Value::as_object_mut) else {
        return false;
    };
    integrity.insert("publisherSignature".into(), Value::Null);
    canonical_json(&unsigned)
        .is_some_and(|value| value.as_bytes() == package.unsigned_manifest_bytes)
}

fn package_review(current: Option<&Value>, candidate: &Value) -> ModulePackageReview {
    let candidate_permissions = string_set(candidate.pointer("/permissions"));
    let current_permissions = current
        .map(|value| string_set(value.pointer("/permissions")))
        .unwrap_or_default();
    ModulePackageReview {
        added_permissions: candidate_permissions
            .difference(&current_permissions)
            .cloned()
            .collect(),
        runtime_changed: current.is_none_or(|value| {
            ["kind", "entry", "companionPackage"]
                .into_iter()
                .any(|field| {
                    value.pointer(&format!("/runtime/{field}"))
                        != candidate.pointer(&format!("/runtime/{field}"))
                })
        }),
        publisher_changed: current.is_some_and(|value| {
            value.pointer("/integrity/publisherKeySha256")
                != candidate.pointer("/integrity/publisherKeySha256")
        }),
        risk_changed: current.is_some_and(|value| value.get("risk") != candidate.get("risk")),
    }
}

fn string_set(value: Option<&Value>) -> BTreeSet<String> {
    value
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .map(str::to_owned)
        .collect()
}

fn string_at<'a>(value: &'a Value, pointer: &str) -> Option<&'a str> {
    value.pointer(pointer).and_then(Value::as_str)
}

fn write_synced(path: &Path, bytes: &[u8]) -> std::io::Result<()> {
    let mut file = OpenOptions::new().write(true).create_new(true).open(path)?;
    file.write_all(bytes)?;
    file.sync_all()
}

fn read_bounded(path: &Path, minimum: u64, maximum: u64) -> std::io::Result<Vec<u8>> {
    let metadata = fs::symlink_metadata(path)?;
    if !metadata.file_type().is_file() || metadata.len() < minimum || metadata.len() > maximum {
        return Err(std::io::Error::other(
            "stored component is outside its bounds",
        ));
    }
    let mut bytes = Vec::with_capacity(usize::try_from(metadata.len()).unwrap_or_default());
    File::open(path)?.read_to_end(&mut bytes)?;
    Ok(bytes)
}

fn safe_module_id(value: &str) -> bool {
    value.contains('.')
        && !value.starts_with('.')
        && !value.ends_with('.')
        && !value.contains("..")
        && value.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'.' || byte == b'-'
        })
}

fn safe_version(value: &str) -> bool {
    let suffix_valid = value.split_once('-').is_none_or(|(_, suffix)| {
        !suffix.is_empty()
            && suffix
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || byte == b'.' || byte == b'-')
    });
    suffix_valid && version_core(value).is_some()
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

fn unique_name(prefix: &str) -> String {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_nanos())
        .unwrap_or_default();
    let count = UNIQUE_COUNTER.fetch_add(1, Ordering::Relaxed);
    format!("{nanos:032}-{count:020}-{prefix}")
}

fn store_result(
    status: ModulePackageStoreStatus,
    review: Option<ModulePackageReview>,
    pointer: Option<&StoredModulePointer>,
) -> ModulePackageStoreResult {
    ModulePackageStoreResult {
        status,
        review,
        current_version: pointer.map(|value| value.current_version.clone()),
        previous_version: pointer.and_then(|value| value.previous_version.clone()),
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashSet;

    use super::*;
    use crate::module_package::{ModuleHost, verify_package_envelope};

    const INTEROP_FIXTURE: &str =
        include_str!("../../../../packages/module-contract/fixtures/signed-device-info-v1.json");
    const TRUSTED_KEY: &str = "733bb4002ab8e962a0225eb13e5c625cfec4250d9319fbf11313e23b737fccf5";

    #[test]
    fn stores_updates_and_rolls_back_with_append_only_pointers() {
        with_store(|store| {
            let first = verified_fixture();
            let second = update_package(&first, "0.2.0", first_permissions(&first));

            assert_eq!(
                store.store(&first, false).status,
                ModulePackageStoreStatus::ReviewRequired,
            );
            assert!(store.pointer(module_id(&first)).is_none());
            assert_eq!(
                store.store(&first, true).status,
                ModulePackageStoreStatus::Stored,
            );
            assert_eq!(
                store.store(&second, false).status,
                ModulePackageStoreStatus::Stored,
            );
            assert_eq!(
                store.rollback(module_id(&first), false).status,
                ModulePackageStoreStatus::RolledBack,
            );
            assert_eq!(
                store.pointer(module_id(&first)).unwrap().current_version,
                "0.1.0",
            );
        });
    }

    #[test]
    fn rollback_cannot_restore_permissions_without_review() {
        with_store(|store| {
            let first = verified_fixture();
            let second = update_package(&first, "0.2.0", Vec::new());
            store.store(&first, true);
            store.store(&second, false);

            let blocked = store.rollback(module_id(&first), false);

            assert_eq!(blocked.status, ModulePackageStoreStatus::ReviewRequired);
            assert_eq!(
                blocked.review.unwrap().added_permissions,
                vec!["device.read"],
            );
            assert_eq!(
                store.pointer(module_id(&first)).unwrap().current_version,
                "0.2.0",
            );
            assert_eq!(
                store.rollback(module_id(&first), true).status,
                ModulePackageStoreStatus::RolledBack,
            );
        });
    }

    #[test]
    fn rejects_version_conflicts_downgrades_and_path_identities() {
        with_store(|store| {
            let first = verified_fixture();
            store.store(&first, true);
            let mut conflict = first.clone();
            conflict.payload_bytes = b"different".to_vec();
            assert_eq!(
                store.store(&conflict, false).status,
                ModulePackageStoreStatus::ArtifactInvalid,
            );
            let older = update_package(&first, "0.0.9", first_permissions(&first));
            assert_eq!(
                store.store(&older, false).status,
                ModulePackageStoreStatus::VersionNotNewer,
            );
            let mut escaped = update_package(&first, "0.2.0", first_permissions(&first));
            escaped.manifest["id"] = Value::String("../escape".into());
            assert_eq!(
                store.store(&escaped, true).status,
                ModulePackageStoreStatus::IdentityInvalid,
            );
        });
    }

    fn verified_fixture() -> VerifiedModulePackage {
        let host = ModuleHost {
            kernel_version: "0.1.0".into(),
            android_sdk: 29,
            abis: HashSet::from(["arm64-v8a".into()]),
            available_runtimes: HashSet::from(["declarative".into()]),
        };
        verify_package_envelope(
            INTEROP_FIXTURE.as_bytes(),
            &host,
            &HashSet::from([TRUSTED_KEY.into()]),
        )
        .verified_package
        .expect("fixture verifies")
    }

    fn update_package(
        current: &VerifiedModulePackage,
        version: &str,
        permissions: Vec<String>,
    ) -> VerifiedModulePackage {
        let mut update = current.clone();
        update.payload_bytes = format!("payload-{version}").into_bytes();
        update.publisher_signature = format!("signature-{version}").into_bytes();
        update.manifest["version"] = Value::String(version.into());
        update.manifest["permissions"] =
            Value::Array(permissions.into_iter().map(Value::String).collect());
        update.manifest["integrity"]["rollbackVersion"] = Value::String("0.1.0".into());
        update.manifest["integrity"]["sha256"] = Value::String(sha256(&update.payload_bytes));
        update.manifest["integrity"]["publisherSignature"] =
            Value::String(BASE64.encode(&update.publisher_signature));
        let mut unsigned = update.manifest.clone();
        unsigned["integrity"]["publisherSignature"] = Value::Null;
        update.unsigned_manifest_bytes = canonical_json(&unsigned).unwrap().into_bytes();
        update
    }

    fn first_permissions(package: &VerifiedModulePackage) -> Vec<String> {
        string_set(package.manifest.pointer("/permissions"))
            .into_iter()
            .collect()
    }

    fn module_id(package: &VerifiedModulePackage) -> &str {
        string_at(&package.manifest, "/id").unwrap()
    }

    fn with_store(test: impl FnOnce(&ModulePackageStore)) {
        let root = std::env::temp_dir().join(unique_name("opendevice-store-test"));
        fs::create_dir(&root).unwrap();
        test(&ModulePackageStore::new(root.clone()));
        fs::remove_dir_all(root).unwrap();
    }
}
