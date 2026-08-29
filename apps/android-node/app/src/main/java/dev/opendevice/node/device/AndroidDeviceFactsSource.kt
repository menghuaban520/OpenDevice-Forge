package dev.opendevice.node.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

class AndroidDeviceFactsSource(
    context: Context,
) : DeviceFactsSource {
    private val applicationContext = context.applicationContext
    private val cpuSampler = ProcessCpuSampler()
    private val activityManager =
        applicationContext.getSystemService(ActivityManager::class.java)
    private val storageManager =
        applicationContext.getSystemService(StorageManager::class.java)
    private val powerManager =
        applicationContext.getSystemService(PowerManager::class.java)

    override fun observe(): Flow<DeviceFacts> = flow {
        while (currentCoroutineContext().isActive) {
            emit(readOnce())
            delay(SAMPLE_INTERVAL_MILLIS)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    internal fun readOnce(): DeviceFacts {
        val memory = readMemory()
        val battery = readBattery()
        return DeviceFacts(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            logicalProcessorCount = Runtime.getRuntime().availableProcessors().takeIf { it > 0 },
            totalMemoryBytes = memory?.totalMem,
            availableMemoryBytes = memory?.availMem,
            allocatableStorageBytes = readAllocatableStorage(),
            batteryPercent = battery?.percent,
            batteryTemperatureC = battery?.temperatureC,
            thermalStatus = readThermalStatus(),
            rootSignals = readRootSignals(),
            appCpuPercent = cpuSampler.sample(
                SystemClock.elapsedRealtime(),
                Process.getElapsedCpuTime(),
                Runtime.getRuntime().availableProcessors(),
            ),
            cpuFrequenciesMhz = readCpuFrequencies(),
        )
    }

    private fun readCpuFrequencies(): Map<Int, Int?> = try {
        File("/sys/devices/system/cpu").listFiles().orEmpty()
            .mapNotNull { file ->
                val id = file.name.removePrefix("cpu").toIntOrNull() ?: return@mapNotNull null
                val mhz = try {
                    parseCpuFrequencyKhz(file.resolve("cpufreq/scaling_cur_freq").readText())
                } catch (_: IOException) { null } catch (_: SecurityException) { null }
                id to mhz
            }.sortedBy { it.first }.toMap()
    } catch (_: SecurityException) { emptyMap() }

    private fun readMemory(): ActivityManager.MemoryInfo? = try {
        ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    } catch (_: SecurityException) {
        null
    }

    private fun readAllocatableStorage(): Long? = try {
        storageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun readBattery(): BatteryReading? {
        val intent = try {
            applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
        } catch (_: SecurityException) {
            null
        } ?: return null

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val temperatureTenths = intent.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            Int.MIN_VALUE,
        )
        return BatteryReading(
            percent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            temperatureC = temperatureTenths
                .takeUnless { it == Int.MIN_VALUE }
                ?.div(10f),
        )
    }

    private fun readThermalStatus(): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalLevel.UNKNOWN
        return try {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
                else -> ThermalLevel.UNKNOWN
            }
        } catch (_: SecurityException) {
            ThermalLevel.UNKNOWN
        }
    }

    private fun readRootSignals(): List<String> = ROOT_SIGNAL_PATHS.filter { path ->
        try {
            File(path).exists()
        } catch (_: SecurityException) {
            false
        }
    }

    private data class BatteryReading(
        val percent: Int?,
        val temperatureC: Float?,
    )

    private companion object {
        const val SAMPLE_INTERVAL_MILLIS = 5_000L

        val ROOT_SIGNAL_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/adb/magisk",
        )
    }
}
