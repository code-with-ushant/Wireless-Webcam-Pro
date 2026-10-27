package com.wirelesswebcam.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.wirelesswebcam.protocol.StreamProtocol
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-Low Latency MediaCodec H.264 Hardware Encoder.
 * 
 * Configures hardware acceleration for sub-50ms streaming with Surface input,
 * zero B-frames, Annex-B output NAL extraction, and dynamic bitrate adjustment.
 */
class H264HardwareEncoder(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    initialBitrateBps: Int = 5_000_000, // 5 Mbps default for smooth Wi-Fi
    private val onFrameEncoded: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "H264HardwareEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var encoderThread: HandlerThread? = null
    private var encoderHandler: Handler? = null

    private val isRunning = AtomicBoolean(false)
    private var spsPpsBuffer: ByteArray? = null
    private var currentBitrateBps: Int = initialBitrateBps

    /**
     * Initializes the MediaCodec hardware encoder and returns its input Surface.
     */
    fun start(): Surface {
        if (isRunning.get()) {
            inputSurface?.let { return it }
        }

        Log.i(TAG, "Initializing MediaCodec H.264 Encoder ($width x $height @ $fps FPS, $currentBitrateBps bps)")

        encoderThread = HandlerThread("H264EncoderThread").apply { start() }
        encoderHandler = Handler(encoderThread!!.looper)

        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, currentBitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // IDR Keyframe every 1 second
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            
            // Intra-refresh period for instant recovery on packet drop
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, 15)
                // Repeat previous frame if static screen (essential for screen capture)
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1_000_000L / fps)
            }
            
            // Sub-50ms Latency Optimizations
            setInteger(MediaFormat.KEY_COMPLEXITY, 0) // Lowest complexity for fastest encoding
            setInteger(MediaFormat.KEY_PRIORITY, 0) // Realtime priority
            
            // Explicitly disable B-frames to prevent lookahead frame delay
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            } else {
                setInteger("max-bframes", 0)
            }

            // High Profile for superior quality at target bitrates
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
        }

        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        // Surface input mode does not use explicit input buffers
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        processOutputBuffer(codec, index, info)
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(TAG, "MediaCodec Exception: ${e.diagnosticInfo}", e)
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        Log.i(TAG, "MediaCodec Output Format Changed: $format")
                    }
                }, encoderHandler)

                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }
        } catch (e: MediaCodec.CodecException) {
            // Fallback: some devices don't support High profile; retry with Baseline
            Log.w(TAG, "High profile unsupported, falling back to Baseline: ${e.message}")
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}
                    override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                        processOutputBuffer(codec, index, info)
                    }
                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(TAG, "MediaCodec Exception: ${e.diagnosticInfo}", e)
                    }
                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        Log.i(TAG, "MediaCodec Output Format Changed: $format")
                    }
                }, encoderHandler)
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }
        }

        isRunning.set(true)
        Log.i(TAG, "MediaCodec H.264 Hardware Encoder running successfully")
        return inputSurface!!
    }

    private fun processOutputBuffer(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        if (!isRunning.get()) {
            codec.releaseOutputBuffer(index, false)
            return
        }

        try {
            val outputBuffer: ByteBuffer = codec.getOutputBuffer(index) ?: return
            if (info.size <= 0) return

            outputBuffer.position(info.offset)
            outputBuffer.limit(info.offset + info.size)

            val captureTimestampUs = System.currentTimeMillis() * 1000L
            val isCodecConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            val isKeyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

            if (isCodecConfig) {
                // Store SPS/PPS — rare event, allocation is fine here
                val chunk = ByteArray(info.size)
                outputBuffer.get(chunk)
                spsPpsBuffer = chunk
                onFrameEncoded(StreamProtocol.packFrame(StreamProtocol.FrameType.SPS_PPS, captureTimestampUs, chunk))
            } else if (isKeyframe && spsPpsBuffer != null) {
                // Keyframe: concat SPS/PPS + frame into single array (1 allocation instead of 2)
                val sps = spsPpsBuffer!!
                val payload = ByteArray(sps.size + info.size)
                System.arraycopy(sps, 0, payload, 0, sps.size)
                outputBuffer.get(payload, sps.size, info.size)
                onFrameEncoded(StreamProtocol.packFrame(StreamProtocol.FrameType.KEYFRAME, captureTimestampUs, payload))
            } else {
                // P-frame: single allocation, direct buffer read
                val chunk = ByteArray(info.size)
                outputBuffer.get(chunk)
                val frameType = if (isKeyframe) StreamProtocol.FrameType.KEYFRAME else StreamProtocol.FrameType.P_FRAME
                onFrameEncoded(StreamProtocol.packFrame(frameType, captureTimestampUs, chunk))
            }
        } finally {
            codec.releaseOutputBuffer(index, false)
        }
    }

    /**
     * Dynamically updates encoder target bitrate on-the-fly without stopping encoder.
     */
    fun setBitrate(newBitrateBps: Int) {
        if (newBitrateBps == currentBitrateBps || mediaCodec == null || !isRunning.get()) return

        Log.i(TAG, "Dynamically updating bitrate from $currentBitrateBps to $newBitrateBps bps")
        currentBitrateBps = newBitrateBps
        val params = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newBitrateBps)
        }
        try {
            mediaCodec?.setParameters(params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dynamically set bitrate", e)
        }
    }

    /**
     * Requests an immediate IDR Keyframe refresh from the hardware encoder.
     */
    fun requestSyncFrame() {
        if (mediaCodec == null || !isRunning.get()) return
        Log.i(TAG, "Requesting immediate IDR keyframe refresh")
        val params = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        }
        try {
            mediaCodec?.setParameters(params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request sync frame", e)
        }
    }

    /**
     * Stops and releases hardware encoder resources.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        Log.i(TAG, "Stopping MediaCodec H.264 Encoder")
        try {
            mediaCodec?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaCodec", e)
        } finally {
            mediaCodec = null
            inputSurface?.release()
            inputSurface = null
            encoderThread?.quitSafely()
            encoderThread = null
            encoderHandler = null
        }
    }
}
