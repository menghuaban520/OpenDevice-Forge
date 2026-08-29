package dev.opendevice.node.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProcessCpuSamplerTest {
    @Test
    fun reportsAppCpuAcrossAllCoresAndRequiresTwoValidSamples() {
        val sampler = ProcessCpuSampler()
        assertNull(sampler.sample(1000, 400, 4))
        assertEquals(50.0, sampler.sample(2000, 2400, 4))
        assertEquals(0.0, sampler.sample(3000, 2400, 4))
        assertNull(sampler.sample(4000, -1, 4))
    }

    @Test
    fun unavailableOrMalformedFrequencyIsNotRenderedAsZero() {
        assertEquals(1844, parseCpuFrequencyKhz("1844000\n"))
        assertNull(parseCpuFrequencyKhz(null))
        assertNull(parseCpuFrequencyKhz("permission denied"))
        assertNull(parseCpuFrequencyKhz("0"))
        assertNull(parseCpuFrequencyKhz("-1"))
    }
}
