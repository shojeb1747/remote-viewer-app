package com.remoteviewer.ui

import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.remoteviewer.databinding.ActivityViewerBinding
import com.remoteviewer.network.ViewerSignalingClient
import com.remoteviewer.network.ViewerWebRTCManager
import org.json.JSONObject

class ViewerActivity : AppCompatActivity(), ViewerSignalingClient.Listener {

    private lateinit var binding: ActivityViewerBinding
    private lateinit var signalingClient: ViewerSignalingClient
    private var webRTCManager: ViewerWebRTCManager? = null
    private var hostId: String = ""

    private val SERVER_URL = "https://remote-server-production-4b0d.up.railway.app"

    // Track touch for swipe detection
    private var touchStartX = 0f
    private var touchStartY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomId = intent.getStringExtra("ROOM_ID") ?: ""

        signalingClient = ViewerSignalingClient(SERVER_URL, this)
        signalingClient.connect(roomId)
        updateStatus("Connecting to room: $roomId")

        setupTouchControls()
        setupButtons()
    }

    private fun setupTouchControls() {
        binding.surfaceRemote.setOnTouchListener { _, event ->
            if (hostId.isEmpty()) return@setOnTouchListener false

            // Normalize coordinates to 0.0–1.0 range
            val normX = event.x / binding.surfaceRemote.width
            val normY = event.y / binding.surfaceRemote.height

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = normX
                    touchStartY = normY
                }
                MotionEvent.ACTION_UP -> {
                    val dx = normX - touchStartX
                    val dy = normY - touchStartY
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble())

                    if (dist < 0.02) {
                        // TAP
                        signalingClient.sendTouchEvent(hostId, "TAP", normX, normY)
                    } else {
                        // SWIPE
                        signalingClient.sendTouchEvent(hostId, "SWIPE",
                            touchStartX, touchStartY, normX, normY)
                    }
                }
            }
            true
        }

        // Long press gesture
        binding.surfaceRemote.setOnLongClickListener {
            val x = 0.5f // center as fallback
            val y = 0.5f
            signalingClient.sendTouchEvent(hostId, "LONG_PRESS", x, y)
            true
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            signalingClient.sendKeyEvent(hostId, "BACK")
        }
        binding.btnHome.setOnClickListener {
            signalingClient.sendKeyEvent(hostId, "HOME")
        }
        binding.btnRecents.setOnClickListener {
            signalingClient.sendKeyEvent(hostId, "RECENTS")
        }
        binding.btnDisconnect.setOnClickListener {
            finish()
        }
    }

    // ─── SignalingListener ────────────────────────────────────────────────────

    override fun onConnected() {
        runOnUiThread { updateStatus("Joining room...") }
    }

    override fun onJoined(hostId: String) {
        this.hostId = hostId
        runOnUiThread { updateStatus("Waiting for host to send stream...") }
    }

    override fun onOfferReceived(fromId: String, sdp: String) {
        hostId = fromId
        webRTCManager = ViewerWebRTCManager(this, signalingClient, hostId, binding.surfaceRemote)
        webRTCManager?.createPeerConnection()
        webRTCManager?.setRemoteOffer(sdp)
        runOnUiThread { updateStatus("🔗 Connected to host!") }
    }

    override fun onIceCandidateReceived(fromId: String, candidate: JSONObject) {
        webRTCManager?.addIceCandidate(candidate)
    }

    override fun onHostDisconnected() {
        runOnUiThread {
            Toast.makeText(this, "Host disconnected", Toast.LENGTH_LONG).show()
            updateStatus("Host disconnected")
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
            updateStatus("Error: $message")
        }
    }

    private fun updateStatus(text: String) {
        binding.tvStatus.text = text
    }

    override fun onDestroy() {
        super.onDestroy()
        webRTCManager?.close()
        signalingClient.disconnect()
    }
}
