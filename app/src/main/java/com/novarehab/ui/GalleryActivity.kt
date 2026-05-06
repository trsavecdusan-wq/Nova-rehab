package com.novarehab.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.novarehab.databinding.ActivityGalleryBinding
import com.novarehab.media_messaging.model.MediaMessage
import com.novarehab.media_messaging.repository.MediaGalleryRepository
import com.novarehab.utils.StatEvent
import com.novarehab.utils.StatsManager
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var repository: MediaGalleryRepository
    private lateinit var statsManager: StatsManager
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = MediaGalleryRepository(this)
        statsManager = StatsManager(this)
        statsManager.log(StatEvent.GALLERY_OPEN)
        repository.markAllSeen()

        loadImage()
        setupButtons()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnToMain()
            }
        })
    }

    private fun loadImage() {
        val items = safeItems()
        if (items.isEmpty()) {
            binding.ivGallery.setImageDrawable(null)
            binding.tvNoImages.visibility = View.VISIBLE
            binding.tvNoImages.text = "Galerija je prazna"
            binding.tvImageCount.text = ""
            binding.tvImageMeta.text = ""
            binding.btnDelete.visibility = View.GONE
            return
        }

        if (currentIndex >= items.size) currentIndex = items.lastIndex
        if (currentIndex < 0) currentIndex = 0

        val item = items[currentIndex]
        binding.tvNoImages.visibility = View.GONE
        binding.btnDelete.visibility = View.GONE
        binding.tvImageCount.text = "${currentIndex + 1} / ${items.size}"
        binding.tvImageMeta.text =
            "Od: ${item.senderName}\n${android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", item.receivedAt)}"

        val imageFile = File(item.localPath)
        if (!imageFile.exists()) {
            binding.ivGallery.setImageDrawable(null)
            binding.tvNoImages.visibility = View.VISIBLE
            binding.tvNoImages.text = "Galerija je prazna"
            return
        }

        runCatching {
            Glide.with(this).load(Uri.fromFile(imageFile)).into(binding.ivGallery)
        }.onFailure {
            binding.ivGallery.setImageDrawable(null)
            binding.tvNoImages.visibility = View.VISIBLE
            binding.tvNoImages.text = "Galerije ni bilo mogoče odpreti."
        }
    }

    private fun setupButtons() {
        binding.btnPrev.setOnClickListener {
            val size = safeItems().size
            if (size == 0) return@setOnClickListener
            currentIndex = if (currentIndex > 0) currentIndex - 1 else size - 1
            loadImage()
        }

        binding.btnNext.setOnClickListener {
            val size = safeItems().size
            if (size == 0) return@setOnClickListener
            currentIndex = (currentIndex + 1) % size
            loadImage()
        }

        binding.btnClose.setOnClickListener { returnToMain() }
    }
    private fun returnToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    private fun safeItems(): List<MediaMessage> {
        return runCatching {
            repository.loadAll().filter { it.localPath.isNotBlank() && File(it.localPath).exists() }
        }.getOrDefault(emptyList())
    }
}
