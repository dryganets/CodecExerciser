package com.snap.codec.exerciser.player

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.snap.codec.exerciser.MainActivity

/**
 * Class to keep an eye on set of surfaces
 * Allows randomly select the one and use in the player
 */
class SurfaceManager(
    context: Context,
    countOfDummySurfaces: Int = 0,
    val delegateListener: SurfaceHolder.Callback,
    vararg views: SurfaceView
) {
    var currentHolder: SurfaceHolder = views[0].holder
        private set

    private val holdersInfo = views.associate { it.holder to SurfaceInfo(
        false
    )
    }
        .toMutableMap()
    private val holders = views.map { it.holder } .toMutableList()

    init {
        for (i in 1..countOfDummySurfaces) {
            val dummy =
                DummySurfaceHolder(context)
            holdersInfo[dummy] = SurfaceInfo(
                ready = true,
                dummy = true
            )
            holders.add(dummy)
        }
    }

    private val surfaceListener = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            if (holder == currentHolder) {
                delegateListener.surfaceChanged(holder, format, width, height)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            holdersInfo[holder]?.ready = false
            if (holder == currentHolder) {
                delegateListener.surfaceDestroyed(holder)
            }
        }

        override fun surfaceCreated(holder: SurfaceHolder?) {
            holdersInfo[holder]?.ready = true
            if (holder == currentHolder) {
                delegateListener.surfaceCreated(holder)
            }
        }
    }

    init {
        holders.forEach {
            it.addCallback(surfaceListener)
        }
    }

    fun shuffle() {
        Log.i(TAG, "$LOG_MARK Shuffling the surfaces current is $currentHolder")
        var newHolder = currentHolder
        while (newHolder == currentHolder) {
            val tmp = holders.random()
            Log.i(
                TAG, "$LOG_MARK Trying random surface: ${tmp.surface} " +
                    "info: ${holdersInfo[tmp]}")
            if (tmp == currentHolder) {
                Log.i(TAG, "$LOG_MARK It is the same surface, trying one more")
                continue
            }
            val info = holdersInfo[tmp]
            if (info == null) {
                Log.w(TAG, "$LOG_MARK Surface doesn't belong to the manager")
            } else {
                if (info.ready) {
                    newHolder = tmp
                } else {
                    Log.i(TAG, "$LOG_MARK This surface is not ready trying the next one")
                }
            }
        }

        Log.i("TAG", "$LOG_MARK Changing $currentHolder to $newHolder")
        delegateListener.surfaceDestroyed(currentHolder)
        currentHolder = newHolder
        delegateListener.surfaceCreated(newHolder)
    }

    fun destroy() {
        holdersInfo.forEach { entry ->
            val holder = entry.key
            val info = entry.value
            if (info.dummy) {
                holder.surface.release()
            }
        }
    }

    companion object {
        const val LOG_MARK = "SurfaceManager"
        const val TAG = MainActivity.TAG
    }

}

internal data class SurfaceInfo(var ready: Boolean, val dummy: Boolean = false)