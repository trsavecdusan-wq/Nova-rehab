package com.novarehab.companion

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.novarehab.video.model.CallState
import com.novarehab.video.signaling.CallSnapshot
import com.novarehab.video.signaling.RemoteCallStateStore
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

class CompanionCallManager(
    private val context: Context,
    private val localRenderer: SurfaceViewRenderer,
    private val remoteRenderer: SurfaceViewRenderer,
    private val signalingBaseUrl: String,
    private val roomId: String,
    private val listener: Listener
) {

    interface Listener {
        fun onStatus(text: String)
        fun onIncomingCall()
        fun onCallStarted()
        fun onCallEnded()
        fun onTabletMessage(text: String)
        fun onError(text: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val httpClient = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val remoteCallStateStore = RemoteCallStateStore(signalingBaseUrl, httpClient)

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var waitJob: Job? = null
    private var pollJob: Job? = null
    private var outgoingRequestJob: Job? = null
    private var remoteOffer: SessionDescription? = null
    private var renderersInitialized = false
    private var lastTabletMessageAt = 0L
    private val handledRemoteCandidates = mutableSetOf<String>()

    fun startWaitingForCall() {
        if (!isSignalingConfigured()) {
            listener.onError("Firebase povezava ni nastavljena.")
            return
        }

        waitJob?.cancel()
        waitJob = scope.launch {
            listener.onStatus("Čakam povezavo")

            while (true) {
                try {
                    val offer = withContext(Dispatchers.IO) {
                        getSessionDescription(roomId, "offer")
                    }

                    if (offer != null) {
                        remoteOffer = offer
                        listener.onIncomingCall()
                        listener.onStatus("Lana kliče")
                        return@launch
                    }

                    val incomingStatus = withContext(Dispatchers.IO) {
                        getIncomingRequestStatus()
                    }

                    if (incomingStatus == "calling") {
                        listener.onIncomingCall()
                        listener.onStatus("Lana kliče")
                        return@launch
                    }
                } catch (_: Exception) {
                }

                delay(1200L)
            }
        }
    }

    fun acceptCall() {
        val offer = remoteOffer ?: runCatching {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                getSessionDescription(roomId, "offer")
            }
        }.getOrNull()

        if (offer == null) {
            listener.onStatus("Čakam Lanin klic")
            startWaitingForCall()
            return
        }

        try {
            waitJob?.cancel()
            initializeWebRtc()
            createPeerConnection()
            startLocalMedia()

            peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    createAnswer()
                }
            }, offer)

            listener.onCallStarted()
            listener.onStatus("Povezujem...")
            startReceiverPolling()
        } catch (e: Exception) {
            listener.onError("Klica ni bilo mogoče sprejeti: ${e.message}")
            endCall()
        }
    }

    fun rejectCall() {
        remoteOffer = null
        listener.onStatus("Klic zavrnjen")

        scope.launch(Dispatchers.IO) {
            runCatching {
                sendRoomStatus("rejected")
                clearActiveCallData()
            }

            withContext(Dispatchers.Main) {
                startWaitingForCall()
            }
        }
    }

    fun sendOutgoingTestCall(contactName: String) {
        if (!isSignalingConfigured()) {
            listener.onError("Povezava ni uspela")
            return
        }

        outgoingRequestJob?.cancel()
        listener.onStatus("Klic poslan")

        scope.launch(Dispatchers.IO) {
            try {
                sendOutgoingRequest("calling", contactName)

                withContext(Dispatchers.Main) {
                    startOutgoingRequestPolling()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    listener.onError("Povezava ni uspela")
                }
            }
        }
    }

    fun endCall() {
        waitJob?.cancel()
        pollJob?.cancel()
        outgoingRequestJob?.cancel()

        waitJob = null
        pollJob = null
        outgoingRequestJob = null

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

        releaseRenderers()
        eglBase?.release()
        eglBase = null

        if (remoteOffer != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    sendRoomStatus("ended")
                    clearActiveCallData()
                }
            }
        }

        remoteOffer = null
        handledRemoteCandidates.clear()
        listener.onCallEnded()
    }

    private fun initializeWebRtc() {
        if (peerConnectionFactory != null) return

        eglBase = EglBase.create()
        val eglContext = eglBase!!.eglBaseContext

        runCatching { localRenderer.release() }
        runCatching { remoteRenderer.release() }

        remoteRenderer.init(eglContext, null)
        remoteRenderer.setMirror(false)
        remoteRenderer.setZOrderMediaOverlay(false)

        localRenderer.init(eglContext, null)
        localRenderer.setMirror(true)
        localRenderer.setZOrderMediaOverlay(true)

        renderersInitialized = true

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

    private fun releaseRenderers() {
        if (!renderersInitialized) return

        runCatching { localRenderer.release() }
        runCatching { remoteRenderer.release() }

        renderersInitialized = false
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

                if (
                    newState == PeerConnection.IceConnectionState.DISCONNECTED ||
                    newState == PeerConnection.IceConnectionState.FAILED
                ) {
                    listener.onStatus("Povezava je prekinjena")
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        sendIceCandidate(roomId, "receiverCandidates", candidate)
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
        val helper = SurfaceTextureHelper.create("CompanionCameraThread", eglContext)

        capturer.initialize(helper, context, createdVideoSource.capturerObserver)
        capturer.startCapture(640, 480, 24)

        val createdVideoTrack = factory.createVideoTrack("companion_video", createdVideoSource)
        createdVideoTrack.addSink(localRenderer)

        val createdAudioSource = factory.createAudioSource(MediaConstraints())
        val createdAudioTrack = factory.createAudioTrack("companion_audio", createdAudioSource)

        connection.addTrack(createdVideoTrack, listOf("companion_stream"))
        connection.addTrack(createdAudioTrack, listOf("companion_stream"))

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

    private fun createAnswer() {
        val connection = peerConnection ?: return

        connection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                connection.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        scope.launch(Dispatchers.IO) {
                            try {
                                sendSessionDescription(roomId, "answer", description)

                                withContext(Dispatchers.Main) {
                                    listener.onStatus("Klic vzpostavljen")
                                }
                            } catch (_: Exception) {
                                withContext(Dispatchers.Main) {
                                    listener.onError("Odgovora ni bilo mogoče poslati.")
                                }
                            }
                        }
                    }
                }, description)
            }

            override fun onCreateFailure(error: String) {
                listener.onError("Odgovora ni bilo mogoče ustvariti.")
            }
        }, MediaConstraints())
    }

    private fun startReceiverPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                try {
                    val candidates = withContext(Dispatchers.IO) {
                        getIceCandidates(roomId, "callerCandidates")
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

    private fun startOutgoingRequestPolling() {
        outgoingRequestJob?.cancel()
        outgoingRequestJob = scope.launch {
            while (true) {
                try {
                    val status = withContext(Dispatchers.IO) {
                        getOutgoingRequestStatus()
                    }

                    when (status) {
                        "accepted" -> {
                            listener.onStatus("Lana je sprejela klic")
                        }

                        "rejected" -> {
                            listener.onStatus("Klic zavrnjen")
                            return@launch
                        }

                        "ended" -> {
                            listener.onStatus("Klic končan")
                            return@launch
                        }
                    }

                    val message = withContext(Dispatchers.IO) {
                        getTabletCommunicationMessage()
                    }

                    if (message != null && message.updatedAt > lastTabletMessageAt) {
                        lastTabletMessageAt = message.updatedAt
                        listener.onTabletMessage(message.text)
                    }
                } catch (_: Exception) {
                    listener.onStatus("Povezava ni uspela")
                    return@launch
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

    private fun sendRoomStatus(status: String) {
        val request = Request.Builder()
            .url(roomUrl(roomId, "status"))
            .put(JSONObject.quote(status).toRequestBody(jsonType))
            .build()

        httpClient.newCall(request).execute().use {
        }
    }

    private fun sendOutgoingRequest(status: String, contactName: String) {
        val body = JSONObject()
            .put("status", status)
            .put("contactId", CompanionConfig.contactId)
            .put("contactName", contactName)
            .put("updatedAt", System.currentTimeMillis())
            .toString()
            .toRequestBody(jsonType)

        val request = Request.Builder()
            .url(roomUrl(roomId, "outgoingRequest"))
            .put(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Firebase ${response.code}")
        }
    }

    private fun getIncomingRequestStatus(): String {
        val request = Request.Builder()
            .url(roomUrl(roomId, "incomingRequest/status"))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""

            val text = response.body?.string().orEmpty().trim()
            if (text.isBlank() || text == "null") return ""

            return text.trim('"')
        }
    }

    private fun getOutgoingRequestStatus(): String {
        val request = Request.Builder()
            .url(roomUrl(roomId, "outgoingRequest/status"))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Firebase ${response.code}")

            val text = response.body?.string().orEmpty().trim()
            if (text.isBlank() || text == "null") return ""

            return text.trim('"')
        }
    }

    private fun getTabletCommunicationMessage(): TabletMessage? {
        val request = Request.Builder()
            .url(roomUrl(roomId, "communicationMessage"))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string().orEmpty()
            if (text.isBlank() || text == "null") return null

            val json = JSONObject(text)
            if (json.optString("from") != "tablet") return null

            val messageText = json.optString("text").trim()
            if (messageText.isBlank()) return null

            return TabletMessage(messageText, json.optLong("updatedAt", 0L))
        }
    }

    private fun clearActiveCallData() {
        listOf("offer", "answer", "callerCandidates", "receiverCandidates").forEach { child ->
            val request = Request.Builder()
                .url(roomUrl(roomId, child))
                .delete()
                .build()

            httpClient.newCall(request).execute().use {
            }
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

    private fun updateCallState(state: CallState, errorMessage: String = "") {
        remoteCallStateStore.write(
            CallSnapshot(
                roomId = roomId,
                contactId = CompanionConfig.contactId,
                contactName = CompanionConfig.contactName,
                state = state,
                updatedAt = System.currentTimeMillis(),
                errorMessage = errorMessage
            )
        )
    }

    private data class TabletMessage(
        val text: String,
        val updatedAt: Long
    )
}
