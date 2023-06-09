/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.snap.codec.exerciser.content

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.media.MediaFormat
import android.os.AsyncTask
import android.os.Parcel
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.exoplayer2.Format
import com.snap.codec.exerciser.MainActivity
import com.snap.codec.exerciser.R
import com.snap.codec.exerciser.util.getDecoderFormat
import java.io.File
import java.io.FileInputStream
import java.util.*

/**
 * Manages content generated by the app.
 *
 *
 * [ Originally this was going to prepare stuff on demand, but it's easier to just
 * create it all up front on first launch. ]
 *
 *
 * Class is thread-safe.
 */
class ContentManager private constructor() {
    private var mInitialized = false
    private var mFilesDir: File? = null
    private var mContent: ArrayList<Content>? = null
    /**
     * Returns true if all of the content has been created.
     *
     *
     * If this returns false, call createAll.
     */
    fun isContentCreated(): Boolean {
        // Ideally this would probe each individual item to see if anything needs to be done,
        // and a subsequent "prepare" call would generate only the necessary items.  This
        // takes a much simpler approach and just checks to see if the files exist.  If the
        // content changes the user will need to force a regen (via a menu option) or wipe data.
        for (i in ALL_TAGS.indices) {
            val file = getPath(i)
            if (!file.canRead()) {
                Log.d(TAG, "Can't find readable $file")
                return false
            }
        }
        return true
    }

    /**
     * Creates all content, overwriting any existing entries.
     *
     *
     * Call from main UI thread.
     */
    fun createAll(caller: Activity) {
        prepareContent(caller, ALL_TAGS)
    }

    /**
     * Prepares the specified content.  For example, if the caller requires a movie that doesn't
     * exist, this will post a progress dialog and generate the movie.
     *
     *
     * Call from main UI thread.  This returns immediately.  Content generation continues
     * on a background thread.
     */
    fun prepareContent(
        caller: Activity,
        tags: IntArray
    ) { // Put up the progress dialog.
        val builder =
            WorkDialog.create(caller, R.string.preparing_content)
        builder.setCancelable(false)
        val dialog = builder.show()
        // Generate content in async task.
        val genTask = GenerateTask(caller, dialog, tags)
        genTask.execute()
    }

    /**
     * Returns the specified item.
     */
    fun getContent(tag: Int): Content {
        synchronized(mContent!!) { return mContent!![tag] }
    }

    /**
     * Prepares the specified item.
     *
     *
     * This may be called from the async task thread.
     */
    private fun prepare(prog: ProgressUpdater, tag: Int) {
        val movie: GeneratedMovie
        when (tag) {
            MOVIE_EIGHT_RECTS -> {
                movie = MovieEightRects()
                movie.create(getPath(tag), prog)
                synchronized(mContent!!) { mContent!!.add(tag, movie) }
            }
            MOVIE_SLIDERS -> {
                movie = MovieSliders()
                movie.create(getPath(tag), prog)
                synchronized(mContent!!) { mContent!!.add(tag, movie) }
            }
            else -> throw RuntimeException("Unknown tag $tag")
        }
    }

    /**
     * Returns the filename for the tag.
     */
    private fun getFileName(tag: Int): String {
        return when (tag) {
            MOVIE_EIGHT_RECTS -> "gen-eight-rects.mp4"
            MOVIE_SLIDERS -> "gen-sliders.mp4"
            else -> throw RuntimeException("Unknown tag $tag")
        }
    }

    /**
     * Returns the storage location for the specified item.
     */
    fun getPath(tag: Int): File {
        return File(mFilesDir, getFileName(tag))
    }

    /**
     * returns ExoMediaPlayerFormat for content managed by this ContentManager instance
     */
    fun getMediaFormat(tag: Int, context: Context): Format? {
        return getMediaFormat(getPath(tag), context)
    }

    /**
     * Returns the ExoMediaPlayer format for arbitrary file.

     */
    fun getMediaFormat(mediaPath: File, context: Context) : Format? {
        val mediaFormatPath = File(mediaPath.absolutePath + ".info")
        var result: Format? = null

        if (mediaFormatPath.exists()) {
            val mediaInfoLength = mediaFormatPath.length().toInt()
            with(FileInputStream(mediaFormatPath)) {
                val buffer = ByteArray(mediaInfoLength)
                if (read(buffer) == mediaInfoLength) {
                    val parcel = Parcel.obtain()
                    parcel.unmarshall(buffer, 0, mediaInfoLength)
                    parcel.setDataPosition(0)
                    result = Format.CREATOR.createFromParcel(parcel)
                    parcel.recycle()
                }
            }
        } else {
            result = getDecoderFormat(context, mediaPath, mediaFormatPath)
        }

        return result
    }

    interface ProgressUpdater {
        /**
         * Updates a progress meter.
         * @param percent Percent completed (0-100).
         */
        fun updateProgress(percent: Int)
    }

    /**
     * Performs generation of content on an async task thread.
     */
    private class GenerateTask(
        // ----- accessed from UI thread -----
        private val mContext: Context,
        private val mPrepDialog: AlertDialog,
        // ----- accessed from both -----
        private val mTags: IntArray
    ) : AsyncTask<Void, Int, Int>(), ProgressUpdater {
        private val mProgressBar: ProgressBar
        // ----- accessed from async thread -----
        private var mCurrentIndex = 0
        @Volatile
        private var mFailure: RuntimeException? = null

        // async task thread
        protected override fun doInBackground(vararg params: Void): Int {
            val contentManager = instance
            Log.d(TAG, "doInBackground...")
            for (i in mTags.indices) {
                mCurrentIndex = i
                updateProgress(0)
                try {
                    contentManager.prepare(this, mTags[i])
                } catch (re: RuntimeException) {
                    mFailure = re
                    break
                }
                updateProgress(100)
            }
            if (mFailure != null) {
                Log.w(
                    TAG,
                    "Failed while generating content",
                    mFailure
                )
            } else {
                Log.d(TAG, "generation complete")
            }
            return 0
        }

        // async task thread
        override fun updateProgress(percent: Int) {
            publishProgress(mCurrentIndex, percent)
        }

        // UI thread
        override fun onProgressUpdate(progressArray: Array<Int>) {
            val index = progressArray[0]
            val percent = progressArray[1]
            //Log.d(TAG, "progress " + index + "/" + percent + " of " + mTags.length * 100);
            if (percent == 0) {
                val name =
                    mPrepDialog.findViewById<View>(R.id.workJobName_text) as TextView
                name.text = instance.getFileName(mTags[index])
            }
            mProgressBar.progress = index * 100 + percent
        }

        // UI thread
        override fun onPostExecute(result: Int) {
            Log.d(TAG, "onPostExecute -- dismss")
            mPrepDialog.dismiss()
            if (mFailure != null) {
                showFailureDialog(mContext, mFailure!!)
            }
        }

        /**
         * Posts an error dialog, including the message from the failure exception.
         */
        private fun showFailureDialog(
            context: Context,
            failure: RuntimeException
        ) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.contentGenerationFailedTitle)
            val msg = context.getString(
                R.string.contentGenerationFailedMsg,
                failure.message
            )
            builder.setMessage(msg)
            builder.setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            builder.setCancelable(false)
            val dialog = builder.create()
            dialog.show()
        }

        init {
            mProgressBar =
                mPrepDialog.findViewById<View>(R.id.work_progress) as ProgressBar
            mProgressBar.max = mTags.size * 100
        }
    }

    companion object {
        private const val TAG = MainActivity.TAG
        // Enumerated content tags.  These are used as indices into the mContent ArrayList,
// so don't make them sparse.
// TODO: consider using String tags and a HashMap?  prepare() is currently fragile,
//       depending on the movies being added in tag-order.  Could also just use a plain array.
        const val MOVIE_EIGHT_RECTS = 0
        const val MOVIE_SLIDERS = 1
        private val ALL_TAGS = intArrayOf(
            MOVIE_EIGHT_RECTS,
            MOVIE_SLIDERS
        )
        // Housekeeping.
        private val sLock = Any()
        private lateinit var sInstance: ContentManager
        /**
         * Returns the singleton instance.
         */
        val instance: ContentManager
            get() {
                synchronized(sLock) {
                    if (!::sInstance.isInitialized) {
                        sInstance = ContentManager()
                    }
                    return sInstance
                }
            }

        fun initialize(context: Context) {
            val mgr = instance
            synchronized(sLock) {
                if (!mgr.mInitialized) {
                    mgr.mFilesDir = context.filesDir
                    mgr.mContent = ArrayList()
                    mgr.mInitialized = true
                }
            }
        }
    }
}