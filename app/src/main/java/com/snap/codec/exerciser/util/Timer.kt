package com.snap.codec.exerciser.util

import android.os.SystemClock
import android.util.Log

inline fun time(tag: String, message: String, action: () -> Unit) {
    val start = SystemClock.elapsedRealtime()
    action()
    val measured = SystemClock.elapsedRealtime() - start
    Log.i(tag, "$message $measured")
}