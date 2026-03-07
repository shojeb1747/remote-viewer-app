package com.remoteviewer.network

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

/**
 * Viewer-side signaling client.
 * Joins a room and exchanges WebRTC signals with the host.
 */
class ViewerSignalingClient(
    private val serverUrl: String,
    private val listener: Listener
) {
    private lateinit var socket: Socket

    interface Listener {
        fun onConnected()
        fun onJoined(hostId: String)
        fun onOfferReceived(fromId: String, sdp: String)
        fun onIceCandidateReceived(fromId: String, candidate: JSONObject)
        fun onHostDisconnected()
        fun onError(message: String)
    }

    fun connect(roomId: String) {
        val options = IO.Options.builder().setReconnection(true).build()
        socket = IO.socket(URI.create(serverUrl), options)

        socket.on(Socket.EVENT_CONNECT) {
            listener.onConnected()
            socket.emit("viewer:join", JSONObject().put("roomId", roomId))
        }

        socket.on("viewer:joined") { args ->
            val data = args[0] as JSONObject
            listener.onJoined(data.getString("hostId"))
        }

        socket.on("viewer:error") { args ->
            val data = args[0] as JSONObject
            listener.onError(data.getString("message"))
        }

        socket.on("signal:offer") { args ->
            val data = args[0] as JSONObject
            listener.onOfferReceived(data.getString("fromId"), data.getString("sdp"))
        }

        socket.on("signal:ice") { args ->
            val data = args[0] as JSONObject
            listener.onIceCandidateReceived(
                data.getString("fromId"),
                data.getJSONObject("candidate")
            )
        }

        socket.on("host:disconnected") { listener.onHostDisconnected() }

        socket.connect()
    }

    fun sendAnswer(targetId: String, sdp: String) {
        socket.emit("signal:answer", JSONObject()
            .put("targetId", targetId)
            .put("sdp", sdp))
    }

    fun sendIceCandidate(targetId: String, candidate: JSONObject) {
        socket.emit("signal:ice", JSONObject()
            .put("targetId", targetId)
            .put("candidate", candidate))
    }

    // Send touch/control events to host
    fun sendTouchEvent(hostId: String, action: String,
                       x: Float, y: Float, x2: Float = 0f, y2: Float = 0f) {
        socket.emit("control:touch", JSONObject()
            .put("targetId", hostId)
            .put("action", action)
            .put("x", x).put("y", y)
            .put("x2", x2).put("y2", y2))
    }

    fun sendKeyEvent(hostId: String, keyCode: String) {
        socket.emit("control:key", JSONObject()
            .put("targetId", hostId)
            .put("keyCode", keyCode))
    }

    fun disconnect() {
        if (::socket.isInitialized) socket.disconnect()
    }
}
