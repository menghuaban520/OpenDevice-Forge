use serde::{Deserialize, Serialize};

use crate::adb::{AdbCommand, DeviceProperty, located_adb, run_adb};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum TransportState {
    Ready,
    Unauthorized,
    Offline,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdbDevice {
    pub session_serial: String,
    pub transport: TransportState,
    pub model: Option<String>,
    pub product: Option<String>,
}

#[derive(Debug, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InspectionSnapshot {
    pub manufacturer: Option<String>,
    pub product_name: Option<String>,
    pub model: Option<String>,
    pub android_version: Option<String>,
    pub abi: Option<String>,
    pub ram_bytes: Option<u64>,
    pub storage_available_bytes: Option<u64>,
    pub battery_percent: Option<u8>,
    pub root_signals: Vec<String>,
}

#[derive(Debug, Clone, Copy, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InspectionSelection {
    pub identity: bool,
    pub performance: bool,
    pub power: bool,
    pub system: bool,
}

impl Default for InspectionSelection {
    fn default() -> Self {
        Self {
            identity: true,
            performance: true,
            power: true,
            system: true,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum InspectionProbe {
    Manufacturer,
    ProductName,
    Model,
    AndroidVersion,
    Abi,
    Memory,
    Storage,
    Battery,
    Debuggable,
    Secure,
}

fn inspection_plan(selection: InspectionSelection) -> Vec<InspectionProbe> {
    let mut probes = Vec::new();
    if selection.identity {
        probes.extend([
            InspectionProbe::Manufacturer,
            InspectionProbe::ProductName,
            InspectionProbe::Model,
            InspectionProbe::AndroidVersion,
            InspectionProbe::Abi,
        ]);
    }
    if selection.performance {
        probes.extend([InspectionProbe::Memory, InspectionProbe::Storage]);
    }
    if selection.power {
        probes.push(InspectionProbe::Battery);
    }
    if selection.system {
        probes.extend([InspectionProbe::Debuggable, InspectionProbe::Secure]);
    }
    probes
}

fn parse_nonempty(value: String) -> Option<String> {
    let trimmed = value.trim();
    (!trimmed.is_empty()).then(|| trimmed.to_owned())
}

pub fn parse_memory_total(output: &str) -> Option<u64> {
    let line = output
        .lines()
        .find(|line| line.trim_start().starts_with("MemTotal:"))?;
    let kibibytes = line.split_whitespace().nth(1)?.parse::<u64>().ok()?;
    kibibytes.checked_mul(1024)
}

pub fn parse_storage_available(output: &str) -> Option<u64> {
    output.lines().find_map(|line| {
        let kibibytes = line.split_whitespace().nth(3)?.parse::<u64>().ok()?;
        kibibytes.checked_mul(1024)
    })
}

pub fn parse_battery_level(output: &str) -> Option<u8> {
    output.lines().find_map(|line| {
        line.trim()
            .strip_prefix("level:")?
            .trim()
            .parse::<u8>()
            .ok()
            .filter(|level| *level <= 100)
    })
}

fn optional_output(adb_path: &std::path::Path, command: AdbCommand) -> Option<String> {
    run_adb(adb_path, &command).ok()
}

fn property(
    adb_path: &std::path::Path,
    session_serial: &str,
    property: DeviceProperty,
) -> Option<String> {
    optional_output(
        adb_path,
        AdbCommand::Property {
            session_serial: session_serial.to_owned(),
            property,
        },
    )
    .and_then(parse_nonempty)
}

#[tauri::command]
pub fn inspect_device(
    app: tauri::AppHandle,
    session_serial: String,
    selection: InspectionSelection,
) -> Result<InspectionSnapshot, String> {
    let adb_path = located_adb(&app)?;
    let mut snapshot = InspectionSnapshot::default();

    for probe in inspection_plan(selection) {
        match probe {
            InspectionProbe::Manufacturer => {
                snapshot.manufacturer =
                    property(&adb_path, &session_serial, DeviceProperty::Manufacturer);
            }
            InspectionProbe::ProductName => {
                snapshot.product_name =
                    property(&adb_path, &session_serial, DeviceProperty::ProductName);
            }
            InspectionProbe::Model => {
                snapshot.model = property(&adb_path, &session_serial, DeviceProperty::Model);
            }
            InspectionProbe::AndroidVersion => {
                snapshot.android_version =
                    property(&adb_path, &session_serial, DeviceProperty::AndroidVersion);
            }
            InspectionProbe::Abi => {
                snapshot.abi = property(&adb_path, &session_serial, DeviceProperty::Abi);
            }
            InspectionProbe::Memory => {
                snapshot.ram_bytes = optional_output(
                    &adb_path,
                    AdbCommand::Memory {
                        session_serial: session_serial.clone(),
                    },
                )
                .and_then(|output| parse_memory_total(&output));
            }
            InspectionProbe::Storage => {
                snapshot.storage_available_bytes = optional_output(
                    &adb_path,
                    AdbCommand::Storage {
                        session_serial: session_serial.clone(),
                    },
                )
                .and_then(|output| parse_storage_available(&output));
            }
            InspectionProbe::Battery => {
                snapshot.battery_percent = optional_output(
                    &adb_path,
                    AdbCommand::Battery {
                        session_serial: session_serial.clone(),
                    },
                )
                .and_then(|output| parse_battery_level(&output));
            }
            InspectionProbe::Debuggable => {
                if property(&adb_path, &session_serial, DeviceProperty::Debuggable).as_deref()
                    == Some("1")
                {
                    snapshot.root_signals.push("ro.debuggable=1".to_owned());
                }
            }
            InspectionProbe::Secure => {
                if property(&adb_path, &session_serial, DeviceProperty::Secure).as_deref()
                    == Some("0")
                {
                    snapshot.root_signals.push("ro.secure=0".to_owned());
                }
            }
        }
    }

    Ok(snapshot)
}

#[cfg(test)]
mod tests {
    use super::{
        InspectionProbe, InspectionSelection, inspection_plan, parse_battery_level,
        parse_memory_total, parse_storage_available,
    };

    #[test]
    fn inspection_plan_contains_only_enabled_probe_groups() {
        let plan = inspection_plan(InspectionSelection {
            identity: true,
            performance: false,
            power: true,
            system: false,
        });

        assert_eq!(
            plan,
            vec![
                InspectionProbe::Manufacturer,
                InspectionProbe::ProductName,
                InspectionProbe::Model,
                InspectionProbe::AndroidVersion,
                InspectionProbe::Abi,
                InspectionProbe::Battery,
            ],
        );
    }

    #[test]
    fn parses_memory_total_as_bytes() {
        assert_eq!(
            parse_memory_total("MemTotal:        8060928 kB\nMemFree: 100 kB\n"),
            Some(8_254_390_272),
        );
        assert_eq!(parse_memory_total("MemFree: 100 kB"), None);
    }

    #[test]
    fn parses_data_partition_available_space_as_bytes() {
        let output = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-5 122683392 80000000 42683392 66% /data\n";
        assert_eq!(parse_storage_available(output), Some(43_707_793_408));
        assert_eq!(parse_storage_available("Filesystem header only"), None);
    }

    #[test]
    fn parses_storage_when_android_reports_a_non_data_mountpoint() {
        let output = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/sdd72 114612224 29790600 84821624 26% /cust/global/carrier/network\n";
        assert_eq!(parse_storage_available(output), Some(86_857_342_976));
    }

    #[test]
    fn parses_battery_level_without_accepting_unrelated_numbers() {
        assert_eq!(
            parse_battery_level("AC powered: false\n  level: 78\n"),
            Some(78)
        );
        assert_eq!(parse_battery_level("temperature: 310"), None);
    }
}
