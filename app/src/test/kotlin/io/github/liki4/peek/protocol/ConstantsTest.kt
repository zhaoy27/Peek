package io.github.liki4.peek.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bike-name matching: the scan filter decides which advertised names are shown
 * as connectable bikes, so it must accept every supported model and nothing else.
 */
class ConstantsTest {

    @Test
    fun `matches C1 Mini advertised name`() {
        assertTrue(Constants.matchesBikeName("Keep_CC_10200901"))
    }

    @Test
    fun `matches C3 advertised name`() {
        // C3 (K0105D) advertises in all upper case; tolerate any casing.
        assertTrue(Constants.matchesBikeName("KEEP_C3_0011223344"))
        assertTrue(Constants.matchesBikeName("Keep_C3_0011223344"))
    }

    @Test
    fun `ignores unrelated and other Keep devices`() {
        assertFalse(Constants.matchesBikeName("Mi_Band_7"))
        assertFalse(Constants.matchesBikeName("Keep_Fit_Scale"))
        assertFalse(Constants.matchesBikeName("Keep"))
        assertFalse(Constants.matchesBikeName(""))
    }
}
