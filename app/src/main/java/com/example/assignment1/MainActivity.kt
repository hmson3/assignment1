package com.example.assignment1

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var toggleMapButton: Button
    private lateinit var rotateButton: Button
    private lateinit var wardrivingButton: Button

    private var currentImageUri: Uri? = null
    private var rotateAngle = 0f
    private var isMapUploaded = false
    private var isWardrivingMode = false

    private var selectedX: Float = -1f
    private var selectedY: Float = -1f

    private lateinit var wifiManager: WifiManager

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            currentImageUri = it
            imageView.setImageURI(it)
            imageView.rotation = rotateAngle
            isMapUploaded = true
            toggleMapButton.text = "Delete Map"
            Toast.makeText(this, "Map uploaded", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.mapImageView)
        toggleMapButton = findViewById(R.id.btnToggleMap)
        rotateButton = findViewById(R.id.btnRotateMap)
        wardrivingButton = findViewById(R.id.btnWardriving)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // 권한 요청
        if (!hasLocationPermission()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE
                ),
                1
            )
        }

        toggleMapButton.setOnClickListener {
            if (!isMapUploaded) {
                pickImageLauncher.launch("image/*")
            } else {
                imageView.setImageDrawable(null)
                imageView.rotation = 0f
                currentImageUri = null
                rotateAngle = 0f
                isMapUploaded = false
                toggleMapButton.text = "Upload Map"
                Toast.makeText(this, "Map deleted", Toast.LENGTH_SHORT).show()
            }
        }

        rotateButton.setOnClickListener {
            rotateAngle = (rotateAngle + 90f) % 360
            imageView.rotation = rotateAngle
            Toast.makeText(this, "Rotated to $rotateAngle°", Toast.LENGTH_SHORT).show()
        }

        wardrivingButton.setOnClickListener {
            if (isMapUploaded) {
                isWardrivingMode = true
                Toast.makeText(this, "Tap on the map to scan.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please upload a map first.", Toast.LENGTH_SHORT).show()
            }
        }

        imageView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isWardrivingMode) {
                val x = event.x / imageView.width
                val y = event.y / imageView.height
                selectedX = String.format("%.2f", x).toFloat()
                selectedY = String.format("%.2f", y).toFloat()

                AlertDialog.Builder(this)
                    .setTitle("Scanned APs")
                    .setMessage("Would you like to scan here?")
                    .setPositiveButton("Yes") { _, _ -> startWifiScan() }
                    .setNegativeButton("No") { _, _ ->
                        Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                    }
                    .show()

                isWardrivingMode = false
            }
            true
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun startWifiScan() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "위치 권한 필요", Toast.LENGTH_SHORT).show()
            return
        }

        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Wi-Fi를 켜주세요", Toast.LENGTH_SHORT).show()
            return
        }

        val wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    val results = wifiManager.scanResults
                    unregisterReceiver(this)

                    if (results.isEmpty()) {
                        Toast.makeText(this@MainActivity, "📡 AP를 찾지 못했습니다", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val scanText = results.joinToString("\n") {
                        "${it.SSID}; ${it.BSSID}; ${it.level} dBm"
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Scanned APs\nWould you like to save?")
                        .setMessage(scanText)
                        .setPositiveButton("Yes") { _, _ -> saveScanData(selectedX, selectedY, results) }
                        .setNegativeButton("No", null)
                        .show()

                } catch (e: SecurityException) {
                    Toast.makeText(this@MainActivity, "⚠️ 권한 오류: ${e.message}", Toast.LENGTH_LONG).show()
                    unregisterReceiver(this)
                }
            }
        }

        registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        // ✅ 이 부분에 보안 처리
        try {
            if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                val success = wifiManager.startScan()
                Log.d("DEBUG", "startScan success: $success")
            } else {
                Toast.makeText(this, "CHANGE_WIFI_STATE 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "SecurityException: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("DEBUG", "startScan 실패: ${e.message}")
        }
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }

    private fun saveScanData(x: Float, y: Float, scanResults: List<ScanResult>) {
        val timestamp = System.currentTimeMillis()
        val dataLines = scanResults.map {
            "$x,$y,$timestamp,${it.SSID},${it.BSSID},${it.level}"
        }

        val file = File(filesDir, "wardriving_data.csv")
        file.appendText(dataLines.joinToString("\n") + "\n")

        Toast.makeText(this, "Data saved!", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
        }
    }
}
