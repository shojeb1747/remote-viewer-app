package com.remoteviewer.network

import android.content.Context
import org.json.JSONObject
import org.webrtc.*

/**
 * Viewer-side WebRTC manager.
 * Receives the screen stream from host and renders it on SurfaceViewRenderer.
 */
class ViewerWebRTCManager(
    private val context: Context,
    private val signalingClient: ViewerSignalingClient,
    private val hostId: String,
    private val remoteRenderer: SurfaceViewRenderer
) {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    init {
        initFactory()
    }

    private fun initFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        // Initialize renderer
        val eglBase = EglBase.create()
        remoteRenderer.init(eglBase.eglBaseContext, null)
        remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
    }

    fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    val json = JSONObject()
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex)
                        .put("candidate", candidate.sdp)
                    signalingClient.sendIceCandidate(hostId, json)
                }

                override fun onAddStream(stream: MediaStream) {
                    // Render received video track
                    if (stream.videoTracks.isNotEmpty()) {
                        stream.videoTracks[0].addSink(remoteRenderer)
                    }
                }

                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                    android.util.Log.d("ViewerWebRTC", "State: $state")
                }

                override fun onSignalingChange(s: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
                override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
                override fun onRemoveStream(s: MediaStream) {}
                override fun onDataChannel(dc: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(r: RtpReceiver, streams: Array<MediaStream>) {
                    streams.firstOrNull()?.videoTracks?.firstOrNull()?.addSink(remoteRenderer)
                }
                override fun onIceCandidatesRemoved(c: Array<IceCandidate>) {}
                override fun onIceConnectionReceivingChange(b: Boolean) {}
            }
        )
    }

    fun setRemoteOffer(sdp: String) {
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                createAnswer()
            }
            override fun onSetFailure(e: String) {}
            override fun onCreateSuccess(s: SessionDescription) {}
            override fun onCreateFailure(e: String) {}
        }, offer)
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        signalingClient.sendAnswer(hostId, sdp.description)
                    }
                    override fun onSetFailure(e: String) {}
                    override fun onCreateSuccess(s: SessionDescription) {}
                    override fun onCreateFailure(e: String) {}
                }, sdp)
            }
            override fun onCreateFailure(e: String) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String) {}
        }, MediaConstraints())
    }

    fun addIceCandidate(candidateJson: JSONObject) {
        peerConnection?.addIceCandidate(
            IceCandidate(
                candidateJson.getString("sdpMid"),
                candidateJson.getInt("sdpMLineIndex"),
                candidateJson.getString("candidate")
            )
        )
    }

    fun close() {
        peerConnection?.close()
        remoteRenderer.release()
    }
}
