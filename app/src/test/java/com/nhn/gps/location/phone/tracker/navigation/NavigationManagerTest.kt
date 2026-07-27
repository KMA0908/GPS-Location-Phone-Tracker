package com.nhn.gps.location.phone.tracker.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationManagerTest {

    @Test
    fun navigateTo_updatesCurrentAndPreviousDestination() {
        val manager = NavigationManager()

        manager.navigateTo(AppDestination.Map)

        assertEquals(AppDestination.Map, manager.currentDestination.value)
        assertEquals(AppDestination.Home, manager.previousDestination.value)
    }

    @Test
    fun reset_returnsNavigationToHome() {
        val manager = NavigationManager()
        manager.navigateTo(AppDestination.Tracking)

        manager.reset()

        assertEquals(AppDestination.Home, manager.currentDestination.value)
        assertNull(manager.previousDestination.value)
    }
}
