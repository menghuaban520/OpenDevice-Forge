package dev.opendevice.node.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLayoutPolicyTest {
    @Test
    fun compactPhoneUsesSinglePaneAndCompactGutter() {
        val metrics = forgeLayoutMetrics(360.dp, 800.dp)

        assertEquals(ForgeWindowClass.COMPACT, metrics.windowClass)
        assertEquals(16.dp, metrics.gutter)
        assertFalse(metrics.twoPane)
    }

    @Test
    fun foldablePortraitUsesMediumRulesWithoutForcingTwoPanes() {
        val metrics = forgeLayoutMetrics(673.dp, 840.dp)

        assertEquals(ForgeWindowClass.MEDIUM, metrics.windowClass)
        assertEquals(24.dp, metrics.gutter)
        assertFalse(metrics.twoPane)
    }

    @Test
    fun tabletLandscapeUsesExpandedTwoPaneLayout() {
        val metrics = forgeLayoutMetrics(1_024.dp, 720.dp)

        assertEquals(ForgeWindowClass.EXPANDED, metrics.windowClass)
        assertEquals(32.dp, metrics.gutter)
        assertTrue(metrics.twoPane)
    }

    @Test
    fun shortLandscapeWindowAvoidsCrampedTwoPaneContent() {
        val metrics = forgeLayoutMetrics(900.dp, 420.dp)

        assertEquals(ForgeWindowClass.EXPANDED, metrics.windowClass)
        assertFalse(metrics.twoPane)
    }
}
