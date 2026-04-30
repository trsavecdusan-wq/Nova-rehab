package com.novarehab.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class VideoCallManager(
    private val context: Context,
    private val localRenderer: SurfaceViewRenderer,
    private val remoteRenderer: SurfaceViewRenderer,
    private val signalingBaseUrl: String,
    private val listener: Listener
) {

    interface Listener {
        fun onStatus(text: String)
        fun onCallStarted()
        fun onCallEnded()
        fun onError(text: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val httpClient = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var pollJob: Job? = null
    private var currentRoomId: String? = null
    private var isCaller = false
    private var remoteDescriptionSet = false
    private var callEndedByRemote = false
    private val handledRemoteCandidates = mutableSetOf<String>()

    fun startOutgoingCall(roomId: String) {
        if (!isSignalingConfigured()) {
            listener.onError("Firebase povezava ni nastavljena.")
            return
        }

        currentRoomId = roomId
        isCaller = true
        remoteDescriptionSet = false
        callEndedByRemote = false
        handledRemoteCandidates.clear()

        listener.onStatus("Pripravljam kamero...")
        listener.onCallStarted()

        try {
            initializeWebRtc()
            createPeerConnection()
            startLocalMedia()
            clearRoom(roomId)
            createOffer(roomId)
            startCallerPolling(roomId)
        } catch (e: Exception) {
            listener.onError("Klica ni bilo mogoce zagnati: ${e.message}")
            endCall()
        }
    }

    fun endCall() {
        val roomId = currentRoomId

        pollJob?.cancel()
        pollJob = null

        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }

        videoCapturer?.dispose()
        videoCapturer = null

        localVideoTrack?.dispose()
        localVideoTrack = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        videoSource?.dispose()
        videoSource = null

        audioSource?.dispose()
        audioSource = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        try {
            localRenderer.clearImage()
            remoteRenderer.clearImage()
        } catch (_: Exception) {
        }

        if (roomId != null && isCaller && isSignalingConfigured() && !callEndedByRemote) {
            scope.launch(Dispatchers.IO) {
                runCatching { deleteRoom(roomId) }
            }
        }

        currentRoomId = null
        isCaller = false
        remoteDescriptionSet = false
        callEndedByRemote = false
        handledRemoteCandidates.clear()

        listener.onStatus("Klic prekinjen")
        listener.onCallEnded()
    }

    fun muteAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(!enabled)
        listener.onStatus(if (enabled) "Mikrofon izklopljen" else "Mikrofon vklopljen")
    }

    fun enableCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        listener.onStatus(if (enabled) "Kamera vklopljena" else "Kamera izklopljena")
    }

    private fun initializeWebRtc() {
        if (peerConnectionFactory != null) return

        eglBase = EglBase.create()
        val eglContext = eglBase!!.eglBaseContext

        localRenderer.init(eglContext, null)
        localRenderer.setMirror(true)

        remoteRenderer.init(eglContext, null)
        remoteRenderer.setMirror(false)

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection() {
        val factory = peerConnectionFactory ?: return
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                    listener.onStatus("Klic vzpostavljen")
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                val roomId = currentRoomId ?: return

                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val path = if (isCaller) "callerCandidates" else "receiverCandidates"
                        sendIceCandidate(roomId, path, candidate)
                    }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
            }

            override fun onAddStream(stream: MediaStream) {
            }

            override fun onRemoveStream(stream: MediaStream) {
            }

            override fun onDataChannel(dataChannel: DataChannel) {
            }

            override fun onRenegotiationNeeded() {
            }

            override fun onAddTrack(receiver: RtpReceiver, streams: Array<MediaStream>) {
                val track = receiver.track()
                if (track is VideoTrack) {
                    track.addSink(remoteRenderer)
                }
            }
        })
    }

    private fun startLocalMedia() {
        val factory = peerConnectionFactory ?: return
        val connection = peerConnection ?: return
        val eglContext = eglBase?.eglBaseContext ?: return

        val capturer = createCameraCapturer()
            ?: throw IllegalStateException("Kamera ni najdena")

        val createdVideoSource = factory.createVideoSource(false)
        val helper = SurfaceTextureHelper.create("NovaRehabCameraThread", eglContext)

        capturer.initialize(helper, context, createdVideoSource.capturerObserver)
        capturer.startCapture(640, 480, 24)

        val createdVideoTrack = factory.createVideoTrack("novarehab_video", createdVideoSource)
        createdVideoTrack.addSink(localRenderer)

        val createdAudioSource = factory.createAudioSource(MediaConstraints())
        val createdAudioTrack = factory.createAudioTrack("novarehab_audio", createdAudioSource)

        connection.addTrack(createdVideoTrack, listOf("novarehab_stream"))
        connection.addTrack(createdAudioTrack, listOf("novarehab_stream"))

        videoCapturer = capturer
        videoSource = createdVideoSource
        localVideoTrack = createdVideoTrack
        audioSource = createdAudioSource
        localAudioTrack = createdAudioTrack
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                val capturer = enumerator.createCapturer(name, null)
                if (capturer != null) return capturer
            }
        }

        for (name in deviceNames) {
            if (!enumerator.isFrontFacing(name)) {
                val capturer = enumerator.createCapturer(name, null)
                if (capturer != null) return capturer
            }
        }

        return null
    }

    private fun createOffer(roomId: String) {
        val connection = peerConnection ?: return
        val constraints = MediaConstraints()

        connection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                connection.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        scope.launch(Dispatchers.IO) {
                            try {
                                sendSessionDescription(roomId, "offer", description)

                                withContext(Dispatchers.Main) {
                                    listener.onStatus("Klicem...")
                                }
                            } catch (_: Exception) {
                                withContext(Dispatchers.Main) {
                                    listener.onError("Ponudbe ni bilo mogoce poslati.")
                                }
                            }
                        }
                    }
                }, description)
            }

            override fun onCreateFailure(error: String) {
                listener.onError("Ponudbe ni bilo mogoce ustvariti.")
            }
        }, constraints)
    }

    private fun startCallerPolling(roomId: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                try {
                    val status = withContext(Dispatchers.IO) {
                        getRoomStatus(roomId)
                    }

                    if (status == "rejected") {
                        callEndedByRemote = true
                        listener.onError("Klic je zavrnjen.")
                        endCall()
                        return@launch
                    }

                    if (status == "ended") {
                        callEndedByRemote = true
                        listener.onError("Klic je prekinjen.")
                        endCall()
                        return@launch
                    }

                    if (!remoteDescriptionSet) {
                        val answer = withContext(Dispatchers.IO) {
                            getSessionDescription(roomId, "answer")
                        }

                        if (answer != null) {
                            peerConnection?.setRemoteDescription(SimpleSdpObserver(), answer)
                            remoteDescriptionSet = true
                            listener.onStatus("Klic vzpostavljen")
                        }
                    }

                    val candidates = withContext(Dispatchers.IO) {
                        getIceCandidates(roomId, "receiverCandidates")
                    }

                    candidates.forEach { candidate ->
                        val key = candidate.sdpMid + candidate.sdpMLineIndex + candidate.sdp
                        if (handledRemoteCandidates.add(key)) {
                            peerConnection?.addIceCandidate(candidate)
                        }
                    }
                } catch (_: Exception) {
                }

                delay(1200L)
            }
        }
    }

    private fun isSignalingConfigured(): Boolean {
        return signalingBaseUrl.startsWith("https://") && !signalingBaseUrl.contains("YOUR_FIREBASE")
    }

    private fun roomUrl(roomId: String, child: String = ""): String {
        val base = signalingBaseUrl.trimEnd('/')
        val suffix = if (child.isBlank()) "" else "/$child"
        return "$base/rooms/$roomId$suffix.json"
    }

    private fun clearRoom(roomId: String) {
        deleteRoom(roomId)
    }

    private fun deleteRoom(roomId: String) {
        val request = Request.Builder()
            .url(roomUrl(roomId))
            .delete()
            .build()

        httpClient.newCall(request).execute().use {
        }
    }

    private fun getRoomStatus(roomId: String): String {
        val request = Request.Builder()
            .url(roomUrl(roomId, "status"))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""

            val text = response.body?.string().orEmpty().trim()
            if (text.isBlank() || text == "null") return ""

            return text.trim('"')
        }
    }

    private fun sendSessionDescription(roomId: String, child: String, description: SessionDescription) {
        val json = JSONObject()
            .put("type", description.type.canonicalForm())
            .put("sdp", description.description)

        val request = Request.Builder()
            .url(roomUrl(roomId, child))
            .put(json.toString().toRequestBody(jsonType))
            .build()

        httpClient.newCall(request).execute().use {
        }
    }

    private fun getSessionDescription(roomId: String, child: String): SessionDescription? {
        val request = Request.Builder()
            .url(roomUrl(roomId, child))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null

            val text = response.body?.string().orEmpty()
            if (text.isBlank() || text == "null") return null

            val json = JSONObject(text)
            val type = when (json.optString("type")) {
                "answer" -> SessionDescription.Type.ANSWER
                "offer" -> SessionDescription.Type.OFFER
                else -> return null
            }

            return SessionDescription(type, json.optString("sdp"))
        }
    }

    private fun sendIceCandidate(roomId: String, child: String, candidate: IceCandidate) {
        val json = JSONObject()
            .put("sdpMid", candidate.sdpMid)
            .put("sdpMLineIndex", candidate.sdpMLineIndex)
            .put("sdp", candidate.sdp)

        val request = Request.Builder()
            .url(roomUrl(roomId, child))
            .post(json.toString().toRequestBody(jsonType))
            .build()

        httpClient.newCall(request).execute().use {
        }
    }

    private fun getIceCandidates(roomId: String, child: String): List<IceCandidate> {
        val request = Request.Builder()
            .url(roomUrl(roomId, child))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()

            val text = response.body?.string().orEmpty()
            if (text.isBlank() || text == "null") return emptyList()

            val root = JSONObject(text)
            val candidates = mutableListOf<IceCandidate>()
            val keys = root.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val item = root.optJSONObject(key) ?: continue

                candidates.add(
                    IceCandidate(
                        item.optString("sdpMid"),
                        item.optInt("sdpMLineIndex"),
                        item.optString("sdp")
                    )
                )
            }

            return candidates
        }
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {
        }

        override fun onSetSuccess() {
        }

        override fun onCreateFailure(error: String) {
        }

        override fun onSetFailure(error: String) {
        }
    }
}
