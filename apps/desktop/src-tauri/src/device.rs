use serde::Serialize;

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

#[derive(Debug, Serialize)]
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
) -> Result<InspectionSnapshot, String> {
    let adb_path = located_adb(&app)?;
    let memory = optional_output(
        &adb_path,
        AdbCommand::Memory {
            session_serial: session_serial.clone(),
        },
    )
    .and_then(|output| parse_memory_total(&output));
    let storage = optional_output(
        &adb_path,
        AdbCommand::Storage {
            session_serial: session_serial.clone(),
        },
    )
    .and_then(|output| parse_storage_available(&output));
    let battery = optional_output(
        &adb_path,
        AdbCommand::Battery {
            session_serial: session_serial.clone(),
        },
    )
    .and_then(|output| parse_battery_level(&output));
    let debuggable = property(&adb_path, &session_serial, DeviceProperty::Debuggable);
    let secure = property(&adb_path, &session_serial, DeviceProperty::Secure);
    let mut root_signals = Vec::new();
    if debuggable.as_deref() == Some("1") {
        root_signals.push("ro.debuggable=1".to_owned());
    }
    if secure.as_deref() == Some("0") {
        root_signals.push("ro.secure=0".to_owned());
    }

    Ok(InspectionSnapshot {
        manufacturer: property(&adb_path, &session_serial, DeviceProperty::Manufacturer),
        product_name: property(&adb_path, &session_serial, DeviceProperty::ProductName),
        model: property(&adb_path, &session_serial, DeviceProperty::Model),
        android_version: property(&adb_path, &session_serial, DeviceProperty::AndroidVersion),
        abi: property(&adb_path, &session_serial, DeviceProperty::Abi),
        ram_bytes: memory,
        storage_available_bytes: storage,
        battery_percent: battery,
        root_signals,
    })
}

#[cfg(test)]
mod tests {
    use super::{parse_battery_level, parse_memory_total, parse_storage_available};

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
