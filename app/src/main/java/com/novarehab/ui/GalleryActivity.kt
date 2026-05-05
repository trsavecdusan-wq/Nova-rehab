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
import com.novarehab.media_messaging.repository.MediaGalleryRepository
import com.novarehab.utils.StatEvent
import com.novarehab.utils.StatsManager

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
        val items = repository.loadAll()
        if (items.isEmpty()) {
            binding.ivGallery.setImageDrawable(null)
            binding.tvNoImages.visibility = View.VISIBLE
            binding.tvNoImages.text = "Ni prejetih slik v NovaRehab galeriji."
            binding.tvImageCount.text = ""
            binding.tvImageMeta.text = ""
            binding.btnDelete.visibility = View.GONE
            return
        }

        if (currentIndex >= items.size) currentIndex = items.lastIndex
        if (currentIndex < 0) currentIndex = 0

        val item = items[currentIndex]
        binding.tvNoImages.visibility = View.GONE
        binding.btnDelete.visibility = View.VISIBLE
        binding.tvImageCount.text = "${currentIndex + 1} / ${items.size}"
        binding.tvImageMeta.text = "Od: ${item.senderName}\n${android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", item.receivedAt)}"
        Glide.with(this).load(Uri.fromFile(java.io.File(item.localPath))).into(binding.ivGallery)
    }

    private fun setupButtons() {
        binding.btnPrev.setOnClickListener {
            val size = repository.loadAll().size
            if (size == 0) return@setOnClickListener
            currentIndex = if (currentIndex > 0) currentIndex - 1 else size - 1
            loadImage()
        }

        binding.btnNext.setOnClickListener {
            val size = repository.loadAll().size
            if (size == 0) return@setOnClickListener
            currentIndex = (currentIndex + 1) % size
            loadImage()
        }

        binding.btnDelete.setOnClickListener {
            val items = repository.loadAll()
            val item = items.getOrNull(currentIndex) ?: return@setOnClickListener
            repository.delete(item.messageId)
            if (currentIndex > 0) currentIndex--
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
}
