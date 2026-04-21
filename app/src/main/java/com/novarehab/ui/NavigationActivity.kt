package com.novarehab.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.novarehab.R
import com.novarehab.utils.PrefsManager

class NavigationActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvNavSpeed: TextView
    private lateinit var tvNavStatus: TextView
    private var locationManager: android.location.LocationManager? = null
    private val speedHandler = Handler(Looper.getMainLooper())
    private var speedRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_navigation)

        webView = findViewById(R.id.webViewMap)
        tvNavSpeed = findViewById(R.id.tvNavSpeed)
        tvNavStatus = findViewById(R.id.tvNavStatus)

        setupWebView()
        setupButtons()
        startGps()
    }

    private fun setupWebView() {
        val prefs = PrefsManager(this)
        val home = prefs.getHomeAddress()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webViewClient = WebViewClient()

        // Naloži OpenStreetMap z lokacijo doma
        val encodedHome = java.net.URLEncoder.encode(home.ifEmpty { "Ljubljana, Slovenia" }, "UTF-8")
        val mapHtml = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  body { margin:0; padding:0; }
  #map { width:100%; height:100vh; }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var map = L.map('map', { zoomControl: true });
  
  // Satelitski pogled (Esri)
  L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
    attribution: 'Esri',
    maxZoom: 19
  }).addTo(map);
  
  // Označi dom
  var homeAddr = "$home";
  
  // Geocoding prek Nominatim
  fetch('https://nominatim.openstreetmap.org/search?q=' + encodeURIComponent(homeAddr) + '&format=json&limit=1', {
    headers: {'User-Agent': 'NovaRehab/1.0'}
  })
  .then(r => r.json())
  .then(data => {
    if (data && data.length > 0) {
      var lat = parseFloat(data[0].lat);
      var lon = parseFloat(data[0].lon);
      map.setView([lat, lon], 16);
      L.marker([lat, lon]).addTo(map)
        .bindPopup('<b>🏠 DOM</b><br/>' + homeAddr).openPopup();
    } else {
      map.setView([46.0569, 14.5058], 12); // Ljubljana
    }
  })
  .catch(() => {
    map.setView([46.0569, 14.5058], 12);
  });

  // Funkcija za posodobitev pozicije
  function updatePosition(lat, lon, accuracy) {
    if (window.userMarker) {
      window.userMarker.setLatLng([lat, lon]);
    } else {
      window.userMarker = L.circleMarker([lat, lon], {
        radius: 10, fillColor: '#e94560', color: '#fff', weight: 2, fillOpacity: 0.9
      }).addTo(map);
    }
  }
</script>
</body>
</html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://nominatim.openstreetmap.org", mapHtml, "text/html", "UTF-8", null)
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnNavBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnNavHome).setOnClickListener {
            setupWebView() // Ponastavi pogled na dom
        }
    }

    private fun startGps() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        try {
            locationManager?.requestLocationUpdates(
                android.location.LocationManager.GPS_PROVIDER, 1000L, 0.5f
            ) { loc ->
                val kmh = (loc.speed * 3.6).toInt()
                tvNavSpeed.text = if (kmh < 2) "0" else kmh.toString()

                // Posodobi pozicijo na mapi prek JavaScript
                webView.evaluateJavascript(
                    "updatePosition(${loc.latitude}, ${loc.longitude}, ${loc.accuracy});", null
                )
            }
        } catch (e: Exception) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    override fun onDestroy() {
        locationManager?.removeUpdates {}
        webView.destroy()
        super.onDestroy()
    }
}
