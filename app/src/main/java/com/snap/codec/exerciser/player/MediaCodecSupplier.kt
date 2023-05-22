package com.snap.codec.exerciser.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.video.DummySurface
import com.snap.codec.exerciser.MainActivity
import com.snap.codec.exerciser.util.time
import java.lang.IllegalArgumentException

private const val TAG = MainActivity.TAG
/**
 * Interface to simplify the codec re-use
 */
interface MediaCodecSupplier {
    fun acquireCodec(mediaFormat: MediaFormat, exoFormat: Format?, surface: Surface) : MediaCodec
    fun releaseCodec(mediaFormat: MediaFormat, exoFormat: Format?, codec: MediaCodec)

    fun destroy()
}

/**
 * The supplier without support of the codec reuse
 */
class DefaultCodecSupplier : MediaCodecSupplier {
    override fun acquireCodec(format: MediaFormat, exoFormat: Format?, surface: Surface): MediaCodec {
        // Create a MediaCodec decoder, and configure it with the MediaFormat from the
        // extractor.  It's very important to use the format from the extractor because
        // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
        lateinit var result: MediaCodec
        time(
            TAG,
            "MediaCodec DefaultCodecSupplier acquireCodec"
        ) {
            val mime = format.getString(MediaFormat.KEY_MIME);
            result = MediaCodec.createDecoderByType(mime)
            result.configure(format, surface, null, 0)
            result.start()
        }
        return result
    }

    override fun releaseCodec(mediaFormat: MediaFormat, exoFormat: Format?, codec: MediaCodec) {
        time(
            TAG,
            "MediaCodec DefaultCodecSupplier releaseCodec"
        ) {
            codec.stop();
            codec.release()
        }
    }

    override fun destroy() {
        // Doesn't hold on any extra resources
    }

}

class DummySurfaceCodecSupplier(context: Context) :
    MediaCodecSupplier {

    private val dummySurface = DummySurface.newInstanceV17(context, false)

    override fun acquireCodec(format: MediaFormat, exoFormat: Format?, surface: Surface): MediaCodec {
        // Create a MediaCodec decoder, and configure it with the MediaFormat from the
        // extractor.  It's very important to use the format from the extractor because
        // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
        val mime = format.getString(MediaFormat.KEY_MIME);
        val result = MediaCodec.createDecoderByType(mime)
        result.configure(format, dummySurface, null, 0)
        result.start()
        result.setOutputSurface(surface)
        return result
    }

    override fun releaseCodec(mediaFormat: MediaFormat, exoFormat: Format?, codec: MediaCodec) {
        codec.stop();
        codec.release()
    }

    override fun destroy() {
        dummySurface.release()
    }
}

/**
 * The Supplier which allows to reuse a single instance of the codec
 * It basically single instance pool
 *
 * if userHostSwap mode is set to true it uses decoderInitializationData to reconfigure the codec
 * instead of doing stop/configure/start cycle.
 */
class SingleReuseCodecSupplier(
    context: Context,
    private val useHotSwap: Boolean
) :
    MediaCodecSupplier {
    private var codecHolder: MediaCodecHolder? = null
    private val dummySurface = DummySurface.newInstanceV17(context, false)

    @Synchronized
    override fun acquireCodec(format: MediaFormat, exoFormat: Format?, surface: Surface): MediaCodec {
        val localHolder = codecHolder;

        val mime = format.getString(MediaFormat.KEY_MIME);
        lateinit var result: MediaCodec
        time(
            TAG,
            "SingleReuseCodecSupplier acquireCodec"
        ) {
            result = if (localHolder != null) {
                localHolder.reconfigure(format, exoFormat, surface)
                codecHolder = null
                localHolder.codec
            } else {
                val result = MediaCodec.createDecoderByType(mime)
                result.configure(format, surface, null, 0)
                result.start()
                result
            }
        }
        return result
    }

    @Synchronized
    override fun releaseCodec(format: MediaFormat, exoFormat: Format?, codec: MediaCodec) {
        time(
            TAG,
            "SingleReuseCodecSupplier releaseCodec"
        ) {
            if (codecHolder != null) {
                Log.i(TAG, "releaseCodec Full release")
                codec.stop()
                codec.release()
            } else {
                try {
                    codec.setOutputSurface(dummySurface)
                    codecHolder =
                        MediaCodecHolder(
                            MediaInfo(format),
                            codec,
                            useHotSwap
                        )
                } catch (iae: IllegalArgumentException) {
                    Log.w(TAG, "Swallowing IllegalArgumentException in setOutputSurface", iae)
                } catch (ex: Exception) {
                    Log.e(TAG, "releaseCodec setDummySurface failed, doing full release")
                    codec.release()
                    codecHolder = null
                    throw ex
                }
            }
        }
    }

    override fun destroy() {
        dummySurface.release()
        codecHolder?.release()
    }
}

class MediaInfo(format: MediaFormat) {
    val width = format.getInteger(MediaFormat.KEY_WIDTH)
    val height = format.getInteger(MediaFormat.KEY_HEIGHT)
}

data class  MediaCodecHolder(
    var info: MediaInfo,
    var codec: MediaCodec,
    val useHotSwap: Boolean
) {
    fun release() {
        codec.release()
    }

    /**
     * This method is simplified version of the ExoPlayer codec reconfiguration logic.
     * There are following options supported now:
     *  - No resolution change - only update the surface
     *  - Resolution has changed:
     *     - If we have codec initialization data ready and experimentalConfiguration is enabled
     *       we are feeding this data to the codec and updating the surface
     *     - Otherwise we are stopping the codec, reconfigure it, starting again and only after that
     *       updating the codec's surface
     */
    fun reconfigure(format: MediaFormat, exoFormat: Format?, surface: Surface) {
        val newInfo = MediaInfo(format)

        if (newInfo.width != info.width || newInfo.height != info.height) {
            if (useHotSwap && exoFormat != null) {
                if (!reconfigureWithHotSwap(exoFormat, surface)) {
                    // In case re-configure has failed by some reason we use traditional method
                    reconfigureWithResolutionChange(newInfo, format, surface)
                }
            } else {
                reconfigureWithResolutionChange(newInfo, format, surface)
            }
        } else {
            time(
                TAG,
                "MediaCodec reconfigure setOutputSurface"
            ) {
                codec.setOutputSurface(surface)
            }
        }
    }

    /**
     * This implementation uses well documented MediaCodec API's to prepare the decoder for the new
     * resolution
     * We are basically stopping the codec, configure it for the new format and starting it again
     *
     * It is very safe and reliable way but it takes 70+ ms to complete depending on the device
     */
    private fun reconfigureWithResolutionChange(
        newInfo: MediaInfo,
        format: MediaFormat,
        surface: Surface
    ) {
        time(
            TAG,
            "MediaCodec reconfigure with restart"
        ) {
            info = newInfo
            codec.stop()
            codec.configure(format, surface, null, 0)
            codec.start()
        }
    }

    /**
     * @param exoFormat exoplayer extractor data - we need initializationData to bootstrap the codec
     * @param surface the new surface we want to set to the codec
     *
     * @return true if configuration was successful and false otherwise
     */
    private fun reconfigureWithHotSwap(
        exoFormat: Format,
        surface: Surface
    ): Boolean {
        var configured = false
        time(TAG, "MediaCodec reconfigure with exoPlayer init data") {
            try {
                exoFormat.initializationData
                val inputIndex = codec.dequeueInputBuffer(0);
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)
                    var inputSize = 0
                    if (buffer != null) {
                        buffer.clear()
                        for (i in 0 until exoFormat.initializationData.size) {
                            val data: ByteArray = exoFormat.initializationData[i]
                            inputSize += data.size
                            buffer.put(data)
                        }
                        codec.queueInputBuffer(
                            inputIndex, 0, inputSize,
                            0, 0 /*flags*/
                        )
                        codec.setOutputSurface(surface)
                        configured = true
                    }
                }
            } catch (ex: IllegalArgumentException) {
                Log.w(TAG, "MediaCodec: experimental re-configuration failed")
            }
        }
        return configured
    }
}
