use serde::Serialize;
use std::process::{Command, Stdio};

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UsbDeviceHint {
    manufacturer: Option<String>,
    product: Option<String>,
}

fn quoted_property(line: &str, key: &str) -> Option<String> {
    let marker = format!("\"{key}\" = \"");
    let value = line.split_once(&marker)?.1.split('"').next()?.trim();
    (!value.is_empty()).then(|| value.to_owned())
}

fn is_android_vendor(value: &str) -> bool {
    const VENDORS: [&str; 19] = [
        "ASUS", "GOOGLE", "HONOR", "HUAWEI", "LENOVO", "LG", "MEIZU", "MOTOROLA", "NOTHING",
        "NUBIA", "ONEPLUS", "OPPO", "REALME", "SAMSUNG", "SONY", "TCL", "VIVO", "XIAOMI", "ZTE",
    ];
    let normalized = value.to_ascii_uppercase();
    VENDORS.iter().any(|vendor| normalized.contains(vendor))
}

fn finish_hint(manufacturer: Option<String>, product: Option<String>) -> Option<UsbDeviceHint> {
    manufacturer
        .as_deref()
        .is_some_and(is_android_vendor)
        .then_some(UsbDeviceHint {
            manufacturer,
            product,
        })
}

fn parse_usb_device_hint(output: &str) -> Option<UsbDeviceHint> {
    let mut manufacturer = None;
    let mut product = None;

    for line in output.lines() {
        if line.trim_start().starts_with("+-o")
            && let Some(hint) = finish_hint(manufacturer.take(), product.take())
        {
            return Some(hint);
        }
        if let Some(value) = quoted_property(line, "USB Vendor Name") {
            manufacturer = Some(value);
        }
        if let Some(value) = quoted_property(line, "USB Product Name") {
            product = Some(value);
        }
    }

    finish_hint(manufacturer, product)
}

#[tauri::command]
pub fn probe_usb_device() -> Option<UsbDeviceHint> {
    #[cfg(target_os = "macos")]
    {
        let output = Command::new("/usr/sbin/ioreg")
            .args(["-p", "IOUSB", "-l", "-w0", "-r", "-c", "IOUSBHostDevice"])
            .stdin(Stdio::null())
            .output()
            .ok()?;
        if !output.status.success() {
            return None;
        }
        String::from_utf8(output.stdout)
            .ok()
            .and_then(|value| parse_usb_device_hint(&value))
    }

    #[cfg(not(target_os = "macos"))]
    None
}

#[cfg(test)]
mod tests {
    use super::parse_usb_device_hint;

    #[test]
    fn finds_an_android_vendor_without_exposing_the_usb_serial() {
        let output = r#"
+-o Root Hub@00000000  <class IOUSBHostDevice>
  {
    "USB Vendor Name" = "Apple Inc."
    "USB Product Name" = "Root Hub"
  }
+-o CDL-AN50@00100000  <class IOUSBHostDevice>
  {
    "USB Serial Number" = "must-not-leave-the-native-layer"
    "USB Vendor Name" = "HUAWEI"
    "USB Product Name" = "CDL-AN50"
  }
"#;

        let hint = parse_usb_device_hint(output).expect("Huawei phone should be recognized");
        assert_eq!(hint.manufacturer.as_deref(), Some("HUAWEI"));
        assert_eq!(hint.product.as_deref(), Some("CDL-AN50"));
        assert!(!format!("{hint:?}").contains("must-not-leave"));
    }
}
