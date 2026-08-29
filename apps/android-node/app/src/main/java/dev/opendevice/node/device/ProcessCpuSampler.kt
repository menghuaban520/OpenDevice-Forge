package dev.opendevice.node.device

internal class ProcessCpuSampler {
    private var previousElapsed: Long? = null
    private var previousCpu: Long? = null
    private var latest: Double? = null

    @Synchronized
    fun sample(elapsedMillis: Long, cpuMillis: Long, cores: Int): Double? {
        if (cpuMillis < 0 || cores <= 0) {
            previousElapsed = null
            previousCpu = null
            latest = null
            return null
        }
        val elapsed = previousElapsed
        val cpu = previousCpu
        if (elapsed != null && cpu != null && elapsedMillis > elapsed && elapsedMillis - elapsed < 1000) {
            return latest
        }
        latest = if (elapsed != null && cpu != null && elapsedMillis > elapsed && cpuMillis >= cpu) {
            ((cpuMillis - cpu).toDouble() / (elapsedMillis - elapsed) / cores * 100.0).coerceIn(0.0, 100.0)
        } else null
        previousElapsed = elapsedMillis
        previousCpu = cpuMillis
        return latest
    }
}

internal fun parseCpuFrequencyKhz(value: String?): Int? = value?.trim()?.toLongOrNull()
    ?.takeIf { it in 1_000L..10_000_000L }?.div(1_000L)?.toInt()
