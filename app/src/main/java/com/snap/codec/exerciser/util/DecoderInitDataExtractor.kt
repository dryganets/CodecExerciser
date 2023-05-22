package com.snap.codec.exerciser.util

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer
import com.google.android.exoplayer2.video.VideoRendererEventListener
import com.snap.codec.exerciser.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Starts playback of the file to fetch the decoderInit data.
 * The decoder data is saved to the output file once we have it
 *
 * WARNING:
 * This is super hacky implementation and it is not supposed to be anywhere close to production
 */
fun getDecoderFormat(context: Context, file: File, outputFile: File) : Format? {
    val formatListener = CompositeFormatListener()
    val playerBuilder = SimpleExoPlayer.Builder(context, ExtractorRendererFactory(context,
        formatListener))

    val player = playerBuilder.build()
    player.playWhenReady = true

    var format: Format? = null
    player.prepare(fileToDataSource(context, file))
    formatListener.addListener(object : InputFormatListener {
        override fun onInputFormatChanged(formatHolder: FormatHolder) {
            Log.i(MainActivity.TAG, "Format detected: " + formatHolder.format)

            format = formatHolder.format
            formatHolder.format?.let {
                val parcel = Parcel.obtain()
                formatHolder.format?.writeToParcel(parcel, 0)

                // After OS upgrade might become not serializable, but fine for test app
                val marshalledParcel = parcel.marshall()
                parcel.recycle()
                with(FileOutputStream(outputFile)) {
                    write(marshalledParcel)
                }

            }
            player.stop()
            player.release()

        }
    })

    return format
}

fun fileToDataSource(
    context: Context,
    file: File
): ProgressiveMediaSource {
    val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(
        context,
        Util.getUserAgent(context, "CodecExerciser")
    )
    return ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(Uri.fromFile(file))
}

class DecoderExtractionVideoRenderer(
    context: Context,
    private val listener: InputFormatListener
) : MediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT) {

    override fun onInputFormatChanged(formatHolder: FormatHolder) {
        super.onInputFormatChanged(formatHolder)
        listener.onInputFormatChanged(formatHolder)
    }
}

class ExtractorRendererFactory(
    private val context: Context,
    private val listener: InputFormatListener
) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?,
        playClearSamplesWithoutKeys: Boolean,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        out.add(
            DecoderExtractionVideoRenderer(
                context, listener
            )
        )
    }
}


interface InputFormatListener {
    fun onInputFormatChanged(formatHolder: FormatHolder)
}

class CompositeFormatListener: InputFormatListener {
    val listeners = mutableListOf<InputFormatListener>()

    fun addListener(listener: InputFormatListener) {
        listeners.add(listener)
    }

    override fun onInputFormatChanged(formatHolder: FormatHolder) {
        listeners.forEach {
            it.onInputFormatChanged(formatHolder)
        }
    }
}
