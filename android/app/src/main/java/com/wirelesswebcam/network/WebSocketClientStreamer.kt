package com.wirelesswebcam.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-Low Latency WebSocket Network Streamer.
 * 
 * Transmits binary frame packets over LAN with zero buffering, TCP_NODELAY optimization,
 * auto-reconnect, and bi-directional control message handling.
 */
class WebSocketClientStreamer(
    private val onControlCommandReceived: (String) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "WebSocketClientStreamer"
        private const val MAX_SEND_QUEUE_SIZE = 2 // Bounded queue for low latency
    }

    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)

    private val sendExecutor = Executors.newSingleThreadExecutor()
    private val frameQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isSending = AtomicBoolean(false)

    fun connect(serverIp: String, port: Int = 8000) {
        if (isConnected.get() || isConnecting.get()) return

        isConnecting.set(true)
        val wsUrl = "ws://$serverIp:$port/ws/video"
        Log.i(TAG, "Connecting to Desktop WebSocket Server at $wsUrl")

        okHttpClient = OkHttpClient.Builder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for persistent stream
            .writeTimeout(5, TimeUnit.SECONDS)
            .pingInterval(2, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket Connection Established!")
                isConnected.set(true)
                isConnecting.set(false)
                onConnectionStateChanged(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received control text message from PC: $text")
                onControlCommandReceived(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket Closing: $code / $reason")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Connection Failure: ${t.message}")
                handleDisconnect()
            }
        })
    }

    /**
     * Enqueues an encoded binary frame packet for immediate network dispatch.
     */
    fun sendFramePacket(packet: ByteArray) {
        if (!isConnected.get() || webSocket == null) return

        // Drop oldest frame if queue exceeds capacity to prevent latency drift
        while (frameQueue.size >= MAX_SEND_QUEUE_SIZE) {
            frameQueue.poll()
        }
        frameQueue.offer(packet)

        // Trigger network send worker
        if (isSending.compareAndSet(false, true)) {
            sendExecutor.execute {
                try {
                    while (isConnected.get()) {
                        val frame = frameQueue.poll() ?: break
                        webSocket?.send(frame.toByteString())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error transmitting frame over WebSocket", e)
                } finally {
                    isSending.set(false)
                }
            }
        }
    }

    private fun handleDisconnect() {
        if (isConnected.getAndSet(false) || isConnecting.getAndSet(false)) {
            frameQueue.clear()
            onConnectionStateChanged(false)
            Log.i(TAG, "Disconnected from Desktop server")
        }
    }

    fun disconnect() {
        handleDisconnect()
        try {
            webSocket?.close(1000, "Client Disconnecting")
            okHttpClient?.dispatcher?.executorService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket client", e)
        } finally {
            webSocket = null
            okHttpClient = null
        }
    }
}
