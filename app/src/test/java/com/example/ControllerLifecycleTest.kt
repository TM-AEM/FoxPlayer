package com.example

import com.example.ui.player.ControllerLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions of the controller lifecycle of the player screen.
 *
 * The screen drops the controller of a dead session and asks for a new one. Both
 * decisions are made by the lifecycle, so they are verified here without a live
 * media session: a controller that kept being the command target of a session
 * that no longer exists would swallow every playback command.
 */
class ControllerLifecycleTest {

    @Test
    fun aDisconnectOfTheCurrentControllerInvalidatesIt() {
        val lifecycle = ControllerLifecycle<String>()
        val controller = "controller"
        lifecycle.onBuilt(lifecycle.startAttempt(), controller)

        val invalidated = lifecycle.onDisconnected(controller)

        assertTrue("the current controller must be invalidated", invalidated)
        assertNull("the dead controller must not stay the command target", lifecycle.controller)
    }

    @Test
    fun aDisconnectOfTheCurrentControllerRequestsANewOne() {
        val lifecycle = ControllerLifecycle<String>()
        val controller = "controller"
        lifecycle.onBuilt(lifecycle.startAttempt(), controller)
        val requestsBefore = lifecycle.requestCount

        lifecycle.onDisconnected(controller)

        assertEquals(
            "the screen must ask for a controller that reaches the new session",
            requestsBefore + 1,
            lifecycle.requestCount
        )
    }

    @Test
    fun aDisconnectOfAnObsoleteControllerCannotInvalidateTheCurrentOne() {
        val lifecycle = ControllerLifecycle<String>()
        val obsolete = "obsolete"
        lifecycle.onBuilt(lifecycle.startAttempt(), obsolete)
        val current = "current"
        lifecycle.onBuilt(lifecycle.startAttempt(), current)

        val invalidated = lifecycle.onDisconnected(obsolete)

        assertFalse("a notification of an obsolete controller must be ignored", invalidated)
        assertEquals(
            "the controller of the newer attempt must stay the command target",
            current,
            lifecycle.controller
        )
    }

    @Test
    fun aStaleBuildCompletionCannotReplaceANewerController() {
        val lifecycle = ControllerLifecycle<String>()
        val staleAttempt = lifecycle.startAttempt()
        val currentAttempt = lifecycle.startAttempt()
        val current = "current"
        lifecycle.onBuilt(currentAttempt, current)

        val accepted = lifecycle.onBuilt(staleAttempt, "stale")

        assertFalse("the completion of an obsolete attempt must be ignored", accepted)
        assertEquals(
            "the controller of the newer attempt must stay the command target",
            current,
            lifecycle.controller
        )
    }

    @Test
    fun everyAttemptOwnsItsOwnToken() {
        val lifecycle = ControllerLifecycle<String>()

        val first = lifecycle.startAttempt()
        val second = lifecycle.startAttempt()

        assertFalse("an obsolete attempt must not be current", lifecycle.isCurrentAttempt(first))
        assertTrue("the latest attempt must be current", lifecycle.isCurrentAttempt(second))

        lifecycle.invalidateAttempt(second)
        assertFalse("an invalidated attempt must not be current", lifecycle.isCurrentAttempt(second))
    }
}
