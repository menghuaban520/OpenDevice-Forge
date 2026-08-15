mod adb;
mod device;
mod usb;

pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            adb::probe_adb,
            adb::list_devices,
            usb::probe_usb_device,
            device::inspect_device
        ])
        .run(tauri::generate_context!())
        .expect("failed to run OpenDevice Forge");
}
