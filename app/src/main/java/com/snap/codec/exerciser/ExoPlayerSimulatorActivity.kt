package com.snap.codec.exerciser

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import com.snap.codec.exerciser.player.MoviePlayer.PlayTask
import com.snap.codec.exerciser.gles.GlUtil
import com.snap.codec.exerciser.player.MediaCodecSupplier
import com.snap.codec.exerciser.player.SingleReuseCodecSupplier
import com.snap.codec.exerciser.player.MoviePlayer
import com.snap.codec.exerciser.player.SpeedControlCallback
import com.snap.codec.exerciser.util.MiscUtils
import java.io.File
import java.io.IOException

/**
 * This activity emulates the exoplayer Surface workaround flow without actual codec reuse
 */
class ExoPlayerSimulatorActivity : Activity(), AdapterView.OnItemSelectedListener,
    MoviePlayer.PlayerFeedback {

    private lateinit var movieFiles: Array<String>
    private var selectedMovie = 0
    private var showStopLabel = false

    private lateinit var player: MoviePlayer
    private lateinit var codecSupplier: MediaCodecSupplier
    private var playTask: PlayTask? = null
    private lateinit var surfaceView: SurfaceView

    private var mSurfaceHolderReady = false

    private val surfaceListener: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) { // There's a short delay between the start of the activity and the initialization
            // of the SurfaceHolder that backs the SurfaceView.  We don't want to try to
            // send a video stream to the SurfaceView before it has initialized, so we disable
            // the "play" button until this callback fires.
            Log.d(TAG, "ExoPlayerSimulatorActivity surfaceCreated")
            mSurfaceHolderReady = true
            updateControls()
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) { // ignore
            Log.d(
                TAG,
                "ExoPlayerSimulatorActivity surfaceChanged fmt=" + format + " size=" + width + "x" + height
            )
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) { // ignore
            Log.d(TAG, "ExoPlayerSimulatorActivity Surface destroyed")
        }
    }

    override fun onResume() {
        Log.d(TAG, "ExoPlayerSimulatorActivity onResume")
        super.onResume()
    }

    override fun onDestroy() {
        Log.d(TAG, "ExoPlayerSimulatorActivity onDestroy")
        super.onDestroy()
        codecSupplier.destroy()
    }

    override fun onPause() {
        Log.d(TAG, "ExoPlayerSimulatorActivity onPause")
        super.onPause()
        // We're not keeping track of the state in static fields, so we need to shut the
        // playback down.  Ideally we'd preserve the state so that the player would continue
        // after a device rotation.
        //
        // We want to be sure that the player won't continue to send frames after we pause,
        // because we're tearing the view down.  So we wait for it to stop here.
        if (playTask != null) {
            stopPlayback()
            playTask!!.waitForStop()
        }
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
        val spinner = parent as Spinner
        selectedMovie = spinner.selectedItemPosition
        Log.d(
            TAG,
            "onItemSelected: " + selectedMovie + " '" + movieFiles.get(selectedMovie) + "'"
        )
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    /**
     * onClick handler for "play"/"stop" button.
     */
    fun clickPlayStop(unused: View?) {
        if (showStopLabel) {
            Log.d(TAG, "stopping movie")
            stopPlayback()
            // Don't update the controls here -- let the task thread do it after the movie has
            // actually stopped.
            //mShowStopLabel = false;
            //updateControls();
        } else {
            if (playTask != null) {
                Log.w(TAG, "movie already playing")
                return
            }
            Log.d(TAG, "starting movie")
            val callback = SpeedControlCallback()
            val holder: SurfaceHolder = surfaceView.holder
            val surface = holder.surface
            // Don't leave the last frame of the previous video hanging on the screen.
            // Looks weird if the aspect ratio changes.
            GlUtil.clearSurface(surface)


            player = try {
                MoviePlayer(this,
                    File(filesDir, movieFiles.get(selectedMovie)), surface, callback
                )
            } catch (ioe: IOException) {
                Log.e(TAG, "Unable to play movie", ioe)
                surface.release()
                return
            }
            codecSupplier?.let {
                player.setDecoderSupplier(it)
            }
            //AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.playMovie_afl);
            val width = player.videoWidth
            val height = player.videoHeight
            //layout.setAspectRatio((double) width / height);
            holder.setFixedSize(width, height)
            playTask = PlayTask(player, this)
            playTask!!.setLoopMode(true)
            showStopLabel = true
            updateControls()
            playTask!!.execute()
        }
    }

    /**
     * Overridable  method to get layout id.  Any provided layout needs to include
     * the same views (or compatible) as active_play_movie_surface
     *
     */
    protected fun getContentViewId(): Int {
        return R.layout.exo_player_simulation_activity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getContentViewId())

        codecSupplier =
            SingleReuseCodecSupplier(this, true)
        surfaceView = findViewById<SurfaceView>(R.id.playMovie_surface)
        surfaceView.holder.addCallback(surfaceListener)

        // Populate file-selection spinner.
        movieFiles = MiscUtils.getFiles(this.filesDir, "*.mp4")
        val spinner =
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
        updateControls()
    }

    /**
     * Requests stoppage if a movie is currently playing.
     */
    private fun stopPlayback() {
        if (playTask != null) {
            playTask!!.requestStop()
        }
    }

    // MoviePlayer.PlayerFeedback
    override fun playbackStopped() {
        Log.d(TAG, "playback stopped")
        showStopLabel = false
        playTask = null
        updateControls()
    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private fun updateControls() {
        val play =
            findViewById<View>(R.id.play_stop_button) as Button
        if (showStopLabel) {
            play.setText(R.string.stop_button_text)
        } else {
            play.setText(R.string.play_button_text)
        }
        play.isEnabled = mSurfaceHolderReady
    }

    companion object {
        private const val TAG = MainActivity.TAG
    }
}