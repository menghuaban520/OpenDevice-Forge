package dev.opendevice.node.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class AiNodeServiceContractTest {
    @Test
    fun actionsAndVisibleNotificationIdAreStable() {
        assertEquals(
            "dev.opendevice.node.action.START_AI_NODE",
            AiNodeService.ACTION_START,
        )
        assertEquals(
            "dev.opendevice.node.action.STOP_AI_NODE",
            AiNodeService.ACTION_STOP,
        )
        assertEquals(1_001, AiNodeService.NOTIFICATION_ID)
        assertEquals(
            "系统不允许从后台启动，请回到应用后重试",
            AiNodeService.BACKGROUND_START_MESSAGE,
        )
    }
}
