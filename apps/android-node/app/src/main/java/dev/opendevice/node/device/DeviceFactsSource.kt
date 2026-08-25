package dev.opendevice.node.device

import kotlinx.coroutines.flow.Flow

interface DeviceFactsSource {
    fun observe(): Flow<DeviceFacts>
}
