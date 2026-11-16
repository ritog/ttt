/**
 * DestinationTest — checks the navigation table matches SPEC 9: three tabs, a three-item menu, unique routes.
 *
 * Depends on: ui/nav/Destination.kt, JUnit 4.
 * Must not: start Android components (plain JVM unit test).
 */
package com.ritoghosh.ttt.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationTest {

    @Test
    fun `the bottom bar has Todo, Plan and Track in that order`() {
        assertEquals(listOf(Destination.Todo, Destination.Plan, Destination.Track), Destination.mainTabs)
    }

    @Test
    fun `the menu has Log, Settings and About in that order`() {
        assertEquals(listOf(Destination.Log, Destination.Settings, Destination.About), Destination.menuItems)
    }

    @Test
    fun `every route is unique and maps back to its destination`() {
        val routes = Destination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
        Destination.entries.forEach { assertEquals(it, Destination.fromRoute(it.route)) }
    }

    @Test
    fun `an unknown or missing route maps to nothing`() {
        assertNull(Destination.fromRoute("nope"))
        assertNull(Destination.fromRoute(null))
    }
}
