package com.snap.codec.exerciser.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.Surface
import android.view.SurfaceHolder
import com.google.android.exoplayer2.video.DummySurface

/**
 * The holder to manage the surface lifecycle
 */
class DummySurfaceHolder(context: Context) : SurfaceHolder {

    private val dummySurface =
        DummySurface.newInstanceV17(
            context,
            false
        )

    override fun getSurface(): Surface {
        return dummySurface
    }

    override fun setType(type: Int) {
        TODO("setType not implemented")
    }

    override fun setSizeFromLayout() {
        TODO("setSizeFromLayout not implemented")
    }

    override fun lockCanvas(): Canvas {
        TODO("lockCanvas not implemented")
    }

    override fun lockCanvas(dirty: Rect?): Canvas {
        TODO("not implemented")
    }

    override fun getSurfaceFrame(): Rect {
        TODO("getSurfaceFrame not implemented")
    }

    override fun setFixedSize(width: Int, height: Int) {
    }

    override fun removeCallback(callback: SurfaceHolder.Callback?) {
        TODO("removeCallback not implemented")
    }

    override fun isCreating(): Boolean {
        TODO("not implemented")
    }

    override fun addCallback(callback: SurfaceHolder.Callback?) {
        // Required as it is used by Espresso
    }

    override fun setFormat(format: Int) {
        TODO("not implemented")
    }

    override fun setKeepScreenOn(screenOn: Boolean) {
        TODO("not implemented")
    }

    override fun unlockCanvasAndPost(canvas: Canvas?) {
        TODO("not implemented")
    }
}
