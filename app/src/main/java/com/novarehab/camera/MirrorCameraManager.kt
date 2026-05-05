package com.novarehab.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.novarehab.core.storage.NovaRehabPaths
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

class MirrorCameraManager(private val context: Context) {
    private val paths = NovaRehabPaths(context)
    private var imageCapture: ImageCapture? = null
    private var currentSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    fun resetToFrontCamera(
        owner: LifecycleOwner,
        previewView: PreviewView,
        onError: (Throwable) -> Unit
    ) {
        start(owner, previewView, CameraSelector.DEFAULT_FRONT_CAMERA, onError)
    }

    fun toggleCamera(
        owner: LifecycleOwner,
        previewView: PreviewView,
        onError: (Throwable) -> Unit
    ) {
        currentSelector = if (currentSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        start(owner, previewView, currentSelector, onError)
    }

    fun isUsingBackCamera(): Boolean = currentSelector == CameraSelector.DEFAULT_BACK_CAMERA

    fun capture(
        executor: Executor,
        onSaved: (File) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val capture = imageCapture ?: return
        val fileName = "mirror_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val outputFile = File(paths.galleryCameraDir, fileName)
        outputFile.parentFile?.mkdirs()

        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(outputFile).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSaved(outputFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    private fun start(
        owner: LifecycleOwner,
        previewView: PreviewView,
        selector: CameraSelector,
        onError: (Throwable) -> Unit
    ) {
        currentSelector = selector
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder().build()
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, preview, imageCapture)
            } catch (t: Throwable) {
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
