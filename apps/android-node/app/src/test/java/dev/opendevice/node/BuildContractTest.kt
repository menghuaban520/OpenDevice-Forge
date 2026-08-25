package dev.opendevice.node

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildContractTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("dev.opendevice.node", OpenDeviceNodeApp.PACKAGE_NAME)
    }
}
