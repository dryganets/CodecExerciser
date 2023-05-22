package com.snap.codec.exerciser

import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.IdlingResource.ResourceCallback

inline fun idle(timeMs: Long, body: () -> Unit) {
    val resource = ElapsedTimeIdlingResource(timeMs)
    Espresso.registerIdlingResources(resource)
    try {
        body()
    } finally {
        Espresso.unregisterIdlingResources(resource)
    }
}

class ElapsedTimeIdlingResource(waitingTime: Long) : IdlingResource {
    private val startTime: Long = System.currentTimeMillis()
    private val waitingTime: Long = waitingTime
    private var resourceCallback: ResourceCallback? = null

    override fun getName(): String {
        return ElapsedTimeIdlingResource::class.java.name + ":" + waitingTime
    }

    override fun isIdleNow(): Boolean {
        val elapsed = System.currentTimeMillis() - startTime
        val idle = elapsed >= waitingTime
        if (idle) {
            resourceCallback!!.onTransitionToIdle()
        }
        return idle
    }

    override fun registerIdleTransitionCallback(
        resourceCallback: ResourceCallback
    ) {
        this.resourceCallback = resourceCallback
    }
}