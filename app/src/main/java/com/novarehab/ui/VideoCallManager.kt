package com.novarehab.ui

import android.content.Context
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class VideoCallManager(
    private val context: Context,
    private val roomId: String,
    private val localRenderer: SurfaceViewRenderer,
    private val remoteRenderer: SurfaceViewRenderer,
    private val listener: Listener
) {

    interface Listener {
        fun onStatus(text: String)
        fun onConnected()
        fun onError(message: String)
    }

    private val eglBase: EglBase = EglBase.create()
    private val roomRef: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("nova_rehab_calls").child(roomId)

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var answerListener: ValueEventListener? = null
    private var offerListener: ValueEventListener? = null
    private var remoteCandidateListener: ChildEventListener? = null
    private var started = false
    private var callerMode = false

    fun connectAsCaller() {
        callerMode = true
        startBase()
        listener.onStatus("Kličem...")
        createOffer()
        listenForAnswer()
        listenForRemoteCandidates("calleeCandidates")
    }

    fun connectAsReceiver() {
        callerMode = false
        startBase()
        listener.onStatus("Čakam klic...")
        listenForOfferAndAnswer()
        listenForRemoteCandidates("callerCandidates")
    }

    private fun startBase() {
        if (started) return
        started = true

        initRenderers()
        initPeerConnectionFactory()
        initPeerConnection()
        initLocalMedia()
    }

    private fun initRenderers() {
        localRenderer.init(eglBase.eglBaseContext, null)
        remoteRenderer.init(eglBase.eglBaseContext, null)
        localRenderer.setMirror(true)
        remoteRenderer.setMirror(false)
        localRenderer.setEnableHardwareScaler(true)
        remoteRenderer.setEnableHardwareScaler(true)
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun initPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.CONNECTED ||
                    state == PeerConnection.IceConnectionState.COMPLETED
                ) {
                    listener.onConnected()
                }
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val path = if (callerMode) "callerCandidates" else "calleeCandidates"
                roomRef.child(path).push().setValue(candidate.toMap())
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    track.addSink(remoteRenderer)
                }
            }
        })
    }

    private fun initLocalMedia() {
        val currentFactory = factory ?: return
        val currentPeerConnection = peerConnection ?: return

        surfaceTextureHelper = SurfaceTextureHelper.create("NovaRehabCameraThread", eglBase.eglBaseContext)

        videoCapturer = createCameraCapturer()
        videoSource = currentFactory.createVideoSource(false)

        videoCapturer?.initialize(
            surfaceTextureHelper,
            context.applicationContext,
            videoSource?.capturerObserver
        )
        videoCapturer?.startCapture(640, 480, 30)

        localVideoTrack = currentFactory.createVideoTrack("nova_rehab_video", videoSource)
        localVideoTrack?.addSink(localRenderer)

        audioSource = currentFactory.createAudioSource(MediaConstraints())
        localAudioTrack = currentFactory.createAudioTrack("nova_rehab_audio", audioSource)

        localVideoTrack?.let { currentPeerConnection.addTrack(it, listOf("nova_rehab_stream")) }
        localAudioTrack?.let { currentPeerConnection.addTrack(it, listOf("nova_rehab_stream")) }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator =
            if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context) else Camera1Enumerator(true)

        val frontCamera = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        if (frontCamera != null) return enumerator.createCapturer(frontCamera, null)

        val anyCamera = enumerator.deviceNames.firstOrNull()
        return anyCamera?.let { enumerator.createCapturer(it, null) }
    }

    private fun createOffer() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), description)
                roomRef.child("offer").setValue(description.toMap())
            }

            override fun onCreateFailure(error: String?) {
                listener.onError(error ?: "Napaka pri ustvarjanju klica.")
            }
        }, constraints)
    }

    private fun listenForOfferAndAnswer() {
        offerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val offer = snapshotToSessionDescription(snapshot) ?: return
                roomRef.child("offer").removeEventListener(this)

                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        createAnswer()
                    }
                }, offer)
            }

            override fun onCancelled(error: DatabaseError) {
                listener.onError(error.message)
            }
        }

        roomRef.child("offer").addValueEventListener(offerListener as ValueEventListener)
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), description)
                roomRef.child("answer").setValue(description.toMap())
                listener.onStatus("Klic vzpostavljen")
            }

            override fun onCreateFailure(error: String?) {
                listener.onError(error ?: "Napaka pri odgovoru na klic.")
            }
        }, MediaConstraints())
    }

    private fun listenForAnswer() {
        answerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val answer = snapshotToSessionDescription(snapshot) ?: return
                peerConnection?.setRemoteDescription(SimpleSdpObserver(), answer)
                listener.onStatus("Klic vzpostavljen")
            }

            override fun onCancelled(error: DatabaseError) {
                listener.onError(error.message)
            }
        }

        roomRef.child("answer").addValueEventListener(answerListener as ValueEventListener)
    }

    private fun listenForRemoteCandidates(path: String) {
        remoteCandidateListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val candidate = snapshotToIceCandidate(snapshot) ?: return
                peerConnection?.addIceCandidate(candidate)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                listener.onError(error.message)
            }
        }

        roomRef.child(path).addChildEventListener(remoteCandidateListener as ChildEventListener)
    }

    fun close(clearRoom: Boolean = true) {
        answerListener?.let { roomRef.child("answer").removeEventListener(it) }
        offerListener?.let { roomRef.child("offer").removeEventListener(it) }

        remoteCandidateListener?.let {
            roomRef.child("callerCandidates").removeEventListener(it)
            roomRef.child("calleeCandidates").removeEventListener(it)
        }

        if (clearRoom) roomRef.removeValue()

        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }

        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        factory?.dispose()

        localRenderer.release()
        remoteRenderer.release()
        eglBase.release()
    }

    private fun SessionDescription.toMap(): Map<String, String> {
        return mapOf(
            "type" to type.canonicalForm(),
            "sdp" to description
        )
    }

    private fun IceCandidate.toMap(): Map<String, Any?> {
        return mapOf(
            "sdpMid" to sdpMid,
            "sdpMLineIndex" to sdpMLineIndex,
            "candidate" to sdp
        )
    }

    private fun snapshotToSessionDescription(snapshot: DataSnapshot): SessionDescription? {
        val type = snapshot.child("type").getValue(String::class.java) ?: return null
        val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return null
        return SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
    }

    private fun snapshotToIceCandidate(snapshot: DataSnapshot): IceCandidate? {
        val sdpMid = snapshot.child("sdpMid").getValue(String::class.java)
        val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Int::class.java)
            ?: snapshot.child("sdpMLineIndex").getValue(Long::class.java)?.toInt()
            ?: return null
        val candidate = snapshot.child("candidate").getValue(String::class.java) ?: return null

        return IceCandidate(sdpMid, sdpMLineIndex, candidate)
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
