package dev.opendevice.node.kernel

class CrashWindow(
    private val limit: Int,
    private val durationMillis: Long,
) {
    private val events = ArrayDeque<Long>()

    fun add(atMillis: Long): Boolean {
        while (events.isNotEmpty() && atMillis - events.first() >= durationMillis) {
            events.removeFirst()
        }
        events.addLast(atMillis)
        return events.size >= limit
    }
}
