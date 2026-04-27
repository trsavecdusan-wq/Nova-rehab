package com.novarehab.ui

class VideoCallManager(
    private val listener: Listener
) {

    interface Listener {
        fun onStatus(text: String)
        fun onCallStarted()
        fun onCallEnded()
    }

    private var currentRoomId: String? = null
    private var audioMuted: Boolean = false
    private var cameraEnabled: Boolean = true

    fun startOutgoingCall(roomId: String) {
        currentRoomId = roomId
        audioMuted = false
        cameraEnabled = true

        listener.onStatus("Kličem...")
        listener.onCallStarted()
        listener.onStatus("Klic vzpostavljen")
    }

    fun endCall() {
        currentRoomId = null
        audioMuted = false
        cameraEnabled = true

        listener.onStatus("Klic prekinjen")
        listener.onCallEnded()
    }

    fun muteAudio(enabled: Boolean) {
        audioMuted = enabled
        listener.onStatus(if (enabled) "Mikrofon izklopljen" else "Mikrofon vklopljen")
    }

    fun enableCamera(enabled: Boolean) {
        cameraEnabled = enabled
        listener.onStatus(if (enabled) "Kamera vklopljena" else "Kamera izklopljena")
    }
}
