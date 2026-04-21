package com.novarehab.ui

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.novarehab.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val handler = Handler(Looper.getMainLooper())
    private var imageUris = mutableListOf<Uri>()
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
        imageUris.clear()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        try {
            contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    imageUris.add(contentUri)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (imageUris.isNotEmpty()) {
            showImage(0)
            startSlideshow()
        } else {
            binding.tvNoImages.visibility = View.VISIBLE
            binding.tvNoImages.text = "Ni slik. Najprej dovoli dostop do slik in posnami fotografije."
        }
    }

    private fun showImage(index: Int) {
        if (imageUris.isEmpty()) return
        currentIndex = index % imageUris.size
        if (currentIndex < 0) currentIndex += imageUris.size
        Glide.with(this).load(imageUris[currentIndex]).into(binding.ivGallery)
        binding.tvImageCount.text = "${currentIndex + 1} / ${imageUris.size}"
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
            showImage(if (currentIndex > 0) currentIndex - 1 else imageUris.size - 1)
        }
        binding.btnNext.setOnClickListener { showImage(currentIndex + 1) }
        binding.btnClose.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
