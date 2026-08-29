mod adb;
mod device;
mod module_package;
mod module_package_store;
mod usb;

pub fn run() {
    tauri::Builder::default()
        .manage(module_package::PublisherTrustStore::from_build_configuration())
        .invoke_handler(tauri::generate_handler![
            adb::probe_adb,
            adb::list_devices,
            usb::probe_usb_device,
            device::inspect_device,
            module_package::verify_module_package,
            module_package_store::install_module_package,
            module_package_store::rollback_module_package
        ])
        .run(tauri::generate_context!())
        .expect("failed to run OpenDevice Forge");
}
