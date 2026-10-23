package com.example.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The lifecycle of the media controller of the player screen.
 *
 * The screen owns one controller at a time. Every build attempt owns a token, so
 * that a notification of an obsolete attempt can never clear or replace the
 * controller of a newer attempt.
 *
 * The controller and the request counter are Compose state, so the screen
 * recomposes when the current controller or the number of requests changes.
 *
 * The type of the controller is a parameter, so that the decisions of the
 * lifecycle can be verified without a live media session.
 */
internal class ControllerLifecycle<C : Any> {

    /**
     * The controller the screen may command, or `null` while there is none.
     */
    var controller: C? by mutableStateOf(null)
        private set

    /**
     * The number of controller requests. Every request asks for a new controller,
     * which starts a new build attempt.
     */
    var requestCount: Int by mutableIntStateOf(0)
        private set

    private var nextAttemptId: Int = 0
    private var currentAttemptId: Int? = null

    /**
     * Starts a new build attempt and returns its token.
     */
    fun startAttempt(): Int {
        nextAttemptId += 1
        currentAttemptId = nextAttemptId
        return nextAttemptId
    }

    /**
     * Invalidates the given attempt, for example because its resources were
     * released.
     */
    fun invalidateAttempt(attempt: Int) {
        if (currentAttemptId == attempt) currentAttemptId = null
    }

    /**
     * Whether the given attempt is still the current one.
     */
    fun isCurrentAttempt(attempt: Int): Boolean = currentAttemptId == attempt

    /**
     * The controller of the given attempt was built and becomes the current one.
     *
     * The completion of an obsolete attempt is ignored, so a late build cannot
     * replace the controller of a newer attempt.
     */
    fun onBuilt(attempt: Int, controller: C): Boolean {
        if (!isCurrentAttempt(attempt)) return false
        this.controller = controller
        return true
    }

    /**
     * The session of the given controller is gone.
     *
     * The controller is dropped and a new one is requested, so that the screen
     * stops commanding a proxy of a session that no longer exists. A notification
     * about a controller that is not the current one is ignored, so it can never
     * clear the controller of a newer attempt.
     */
    fun onDisconnected(controller: C): Boolean {
        if (this.controller !== controller) return false
        this.controller = null
        requestCount += 1
        return true
    }
}
