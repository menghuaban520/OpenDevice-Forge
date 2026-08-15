use serde::Serialize;
use std::env;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::Duration;
use tauri::Manager;
use wait_timeout::ChildExt;

use crate::device::{AdbDevice, TransportState};

const COMMAND_TIMEOUT: Duration = Duration::from_secs(8);

#[derive(Debug, Clone, Copy)]
pub enum DeviceProperty {
    Manufacturer,
    ProductName,
    Model,
    AndroidVersion,
    Abi,
    Debuggable,
    Secure,
}

impl DeviceProperty {
    fn key(self) -> &'static str {
        match self {
            Self::Manufacturer => "ro.product.manufacturer",
            Self::ProductName => "ro.product.name",
            Self::Model => "ro.product.model",
            Self::AndroidVersion => "ro.build.version.release",
            Self::Abi => "ro.product.cpu.abi",
            Self::Debuggable => "ro.debuggable",
            Self::Secure => "ro.secure",
        }
    }
}

#[derive(Debug, Clone)]
pub enum AdbCommand {
    Devices,
    Property {
        session_serial: String,
        property: DeviceProperty,
    },
    Memory {
        session_serial: String,
    },
    Storage {
        session_serial: String,
    },
    Battery {
        session_serial: String,
    },
}

impl AdbCommand {
    pub fn args(&self) -> Vec<String> {
        match self {
            Self::Devices => vec!["devices".into(), "-l".into()],
            Self::Property {
                session_serial,
                property,
            } => vec![
                "-s".into(),
                session_serial.clone(),
                "shell".into(),
                "getprop".into(),
                property.key().into(),
            ],
            Self::Memory { session_serial } => vec![
                "-s".into(),
                session_serial.clone(),
                "shell".into(),
                "cat".into(),
                "/proc/meminfo".into(),
            ],
            Self::Storage { session_serial } => vec![
                "-s".into(),
                session_serial.clone(),
                "shell".into(),
                "df".into(),
                "-k".into(),
                "/data".into(),
            ],
            Self::Battery { session_serial } => vec![
                "-s".into(),
                session_serial.clone(),
                "shell".into(),
                "dumpsys".into(),
                "battery".into(),
            ],
        }
    }
}

#[derive(Debug)]
pub enum AdbError {
    Missing,
    Spawn,
    Timeout,
    Failed,
    InvalidUtf8,
}

impl AdbError {
    pub fn code(&self) -> &'static str {
        match self {
            Self::Missing => "adb_missing",
            Self::Spawn => "adb_spawn_failed",
            Self::Timeout => "adb_timeout",
            Self::Failed => "adb_command_failed",
            Self::InvalidUtf8 => "adb_output_invalid",
        }
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdbProbe {
    pub available: bool,
    pub source: Option<&'static str>,
}

fn executable_name() -> &'static str {
    if cfg!(windows) { "adb.exe" } else { "adb" }
}

fn locate_adb(resource_dir: Option<&Path>) -> Result<(PathBuf, &'static str), AdbError> {
    if let Some(resource_dir) = resource_dir {
        let bundled = resource_dir
            .join("resources")
            .join("platform-tools")
            .join(executable_name());
        if bundled.is_file() {
            return Ok((bundled, "bundled"));
        }
    }

    if let Some(path) = env::var_os("PATH") {
        for directory in env::split_paths(&path) {
            let candidate = directory.join(executable_name());
            if candidate.is_file() {
                return Ok((candidate, "path"));
            }
        }
    }
    Err(AdbError::Missing)
}

pub fn run_adb(adb_path: &Path, command: &AdbCommand) -> Result<String, AdbError> {
    let mut child = Command::new(adb_path)
        .args(command.args())
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|_| AdbError::Spawn)?;

    let completed = child
        .wait_timeout(COMMAND_TIMEOUT)
        .map_err(|_| AdbError::Failed)?;
    if completed.is_none() {
        let _ = child.kill();
        let _ = child.wait();
        return Err(AdbError::Timeout);
    }
    let output = child.wait_with_output().map_err(|_| AdbError::Failed)?;
    if !output.status.success() {
        return Err(AdbError::Failed);
    }
    String::from_utf8(output.stdout).map_err(|_| AdbError::InvalidUtf8)
}

pub fn parse_devices_output(output: &str) -> Vec<AdbDevice> {
    output
        .lines()
        .filter_map(|line| {
            let mut fields = line.split_whitespace();
            let session_serial = fields.next()?;
            let state = fields.next()?;
            let transport = match state {
                "device" => TransportState::Ready,
                "unauthorized" => TransportState::Unauthorized,
                "offline" => TransportState::Offline,
                _ => return None,
            };
            let mut model = None;
            let mut product = None;
            for field in fields {
                if let Some(value) = field.strip_prefix("model:") {
                    model = Some(value.replace('_', " "));
                } else if let Some(value) = field.strip_prefix("product:") {
                    product = Some(value.replace('_', " "));
                }
            }
            Some(AdbDevice {
                session_serial: session_serial.to_owned(),
                transport,
                model,
                product,
            })
        })
        .collect()
}

fn resource_dir(app: &tauri::AppHandle) -> Option<PathBuf> {
    app.path().resource_dir().ok()
}

#[tauri::command]
pub fn probe_adb(app: tauri::AppHandle) -> AdbProbe {
    match locate_adb(resource_dir(&app).as_deref()) {
        Ok((_path, source)) => AdbProbe {
            available: true,
            source: Some(source),
        },
        Err(_) => AdbProbe {
            available: false,
            source: None,
        },
    }
}

#[tauri::command]
pub fn list_devices(app: tauri::AppHandle) -> Result<Vec<AdbDevice>, String> {
    let (path, _) = locate_adb(resource_dir(&app).as_deref()).map_err(|error| error.code())?;
    let output = run_adb(&path, &AdbCommand::Devices).map_err(|error| error.code())?;
    Ok(parse_devices_output(&output))
}

pub fn located_adb(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    locate_adb(resource_dir(app).as_deref())
        .map(|(path, _)| path)
        .map_err(|error| error.code().to_owned())
}

#[cfg(test)]
mod tests {
    use super::{AdbCommand, DeviceProperty, parse_devices_output};
    use crate::device::TransportState;

    #[test]
    fn parses_ready_unauthorized_offline_and_multiple_devices() {
        let output = "List of devices attached\nABC123 device product:demo model:Nova_7 transport_id:1\nDEF456 unauthorized usb:2-1\nGHI789 offline transport_id:3\n\n";
        let devices = parse_devices_output(output);

        assert_eq!(devices.len(), 3);
        assert_eq!(devices[0].transport, TransportState::Ready);
        assert_eq!(devices[0].model.as_deref(), Some("Nova 7"));
        assert_eq!(devices[1].transport, TransportState::Unauthorized);
        assert_eq!(devices[2].transport, TransportState::Offline);
    }

    #[test]
    fn ignores_headers_blank_lines_and_malformed_rows() {
        let output = "adb server version mismatch\nList of devices attached\nonly-one-field\n\t\n";
        assert!(parse_devices_output(output).is_empty());
    }

    #[test]
    fn constructs_only_fixed_argument_vectors_without_a_shell_interpreter() {
        assert_eq!(AdbCommand::Devices.args(), vec!["devices", "-l"]);
        assert_eq!(
            AdbCommand::Property {
                session_serial: "ABC; rm -rf ignored".into(),
                property: DeviceProperty::Abi,
            }
            .args(),
            vec![
                "-s",
                "ABC; rm -rf ignored",
                "shell",
                "getprop",
                "ro.product.cpu.abi",
            ],
        );
        assert_eq!(
            AdbCommand::Memory {
                session_serial: "ABC".into(),
            }
            .args(),
            vec!["-s", "ABC", "shell", "cat", "/proc/meminfo"],
        );
        assert_eq!(
            AdbCommand::Storage {
                session_serial: "ABC".into(),
            }
            .args(),
            vec!["-s", "ABC", "shell", "df", "-k", "/data"],
        );
        assert_eq!(
            AdbCommand::Battery {
                session_serial: "ABC".into(),
            }
            .args(),
            vec!["-s", "ABC", "shell", "dumpsys", "battery"],
        );
    }
}
