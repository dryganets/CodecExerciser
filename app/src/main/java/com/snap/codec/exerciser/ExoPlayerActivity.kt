package com.snap.codec.exerciser

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.LoopingMediaSource

import com.snap.codec.exerciser.util.MiscUtils
import com.snap.codec.exerciser.util.fileToDataSource
import java.io.File

class ExoPlayerActivity : Activity(), AdapterView.OnItemSelectedListener {

    lateinit var player: SimpleExoPlayer
    var selectedMovie = 0
    private lateinit var movieFiles: Array<String>

    private lateinit var surfaceView: SurfaceView
    private lateinit var spinner: Spinner

    private val surfaceListener: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) { // There's a short delay between the start of the activity and the initialization
            // of the SurfaceHolder that backs the SurfaceView.  We don't want to try to
            // send a video stream to the SurfaceView before it has initialized, so we disable
            // the "play" button until this callback fires.
            Log.d(TAG, "ExoPlayerActivity surfaceCreated")
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            Log.d(
                TAG,
                "$LOG_MARK surfaceChanged fmt=" + format + " size=" + width + "x" + height
            )
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) { // ignore
            Log.d(TAG, "$LOG_MARK Surface destroyed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.single_surface_layout)

        surfaceView = findViewById<SurfaceView>(R.id.playMovie_surface)
        surfaceView.holder.addCallback(surfaceListener)

        // Populate file-selection spinner.
        movieFiles = MiscUtils.getFiles(this.filesDir, "*.mp4")
        spinner =
            findViewById<Spinner>(R.id.playMovieFile_spinner)
        // Need to create one of these fancy ArrayAdapter thingies, and specify the generic layout
        // for the widget itself.
        val adapter: ArrayAdapter<String> = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item, movieFiles
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner.
        spinner.adapter = adapter
        spinner.onItemSelectedListener = this
        player = createPlayer()

        val stopButton = findViewById<Button>(R.id.play_stop_button)
        stopButton.setText(R.string.set_source)
        stopButton.setOnClickListener(View.OnClickListener {
            val movie = File(filesDir, movieFiles.get(selectedMovie))
            player.prepare(LoopingMediaSource(fileToDataSource(this, movie)))
        })

        val surfaceParent: ViewGroup = findViewById(R.id.surface_row1)

        val addRemoveSurfaceButton = findViewById<Button>(R.id.add_remove_surface)
        addRemoveSurfaceButton.setOnClickListener(View.OnClickListener {
            if (surfaceView.isAttachedToWindow) {
                surfaceParent.removeView(surfaceView)
                addRemoveSurfaceButton.setText(R.string.add_surface_button_text)
            } else {
                surfaceParent.addView(surfaceView)
                addRemoveSurfaceButton.setText(R.string.remove_surface_button_text)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    private fun createPlayer() : SimpleExoPlayer {
        val player = SimpleExoPlayer.Builder(this).build()
        player.playWhenReady = true
        player.setVideoSurfaceHolder(surfaceView.holder)
        return player
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    /*
     * Called when the movie Spinner gets touched.
     */
    override fun onItemSelected(
        parent: AdapterView<*>,
        view: View?,
        pos: Int,
        id: Long
    ) {
        selectedMovie = spinner.selectedItemPosition
        Log.d(
            TAG,
            "${LOG_MARK }onItemSelected: " + selectedMovie + " '" + movieFiles.get(selectedMovie) + "'"
        )
    }

    companion object {
        const val TAG = MainActivity.TAG
        const val LOG_MARK = "ExoPlayerActivity"
    }
}