package com.novarehab.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.novarehab.databinding.ActivityGalleryBinding
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val handler = Handler(Looper.getMainLooper())
    private var images = mutableListOf<File>()
    private var currentIndex = 0
    private val slideshowDelay = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadImages()
        setupButtons()
    }

    private fun loadImages() {
        val galleryDir = File(getExternalFilesDir(null), "gallery")
        if (!galleryDir.exists()) galleryDir.mkdirs()
        images = galleryDir.listFiles { file ->
            file.extension.lowercase() in listOf("jpg", "jpeg", "png")
        }?.toMutableList() ?: mutableListOf()

        if (images.isNotEmpty()) {
            showImage(0)
            startSlideshow()
        } else {
            binding.tvNoImages.visibility = View.VISIBLE
        }
    }

    private fun showImage(index: Int) {
        if (images.isEmpty()) return
        currentIndex = index % images.size
        Glide.with(this).load(images[currentIndex]).into(binding.ivGallery)
        binding.tvImageCount.text = "${currentIndex + 1} / ${images.size}"
    }

    private fun startSlideshow() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                showImage(currentIndex + 1)
                handler.postDelayed(this, slideshowDelay)
            }
        }, slideshowDelay)
    }

    private fun setupButtons() {
        binding.btnPrev.setOnClickListener {
            showImage(if (currentIndex > 0) currentIndex - 1 else images.size - 1)
        }
        binding.btnNext.setOnClickListener { showImage(currentIndex + 1) }
        binding.btnClose.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
