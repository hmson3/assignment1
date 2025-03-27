package com.example.assignment1

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var uploadButton: Button
    private lateinit var deleteButton: Button
    private lateinit var rotateButton: Button
    private lateinit var scanButton: Button

    private var currentImageUri: Uri? = null
    private var rotateAngle = 0f

    private var selectedX: Float = -1f
    private var selectedY: Float = -1f

    private lateinit var wifiManager: WifiManager

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            currentImageUri = it
            imageView.setImageURI(it)
            imageView.rotation = rotateAngle
            Toast.makeText(this, "Map uploaded", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.mapImageView)
        uploadButton = findViewById(R.id.btnUploadMap)
        deleteButton = findViewById(R.id.btnDeleteMap)
        rotateButton = findViewById(R.id.btnRotateMap)
        scanButton = findViewById(R.id.btnScan)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // ✅ 권한 체크
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1
            )
        }

        // ✅ 이후 버튼 리스너 설정
        uploadButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        deleteButton.setOnClickListener {
            imageView.setImageDrawable(null)
            imageView.rotation = 0f
            currentImageUri = null
            rotateAngle = 0f
            Toast.makeText(this, "Map deleted", Toast.LENGTH_SHORT).show()
        }

        rotateButton.setOnClickListener {
            rotateAngle = (rotateAngle + 90f) % 360
            imageView.rotation = rotateAngle
            Toast.makeText(this, "Rotated to $rotateAngle°", Toast.LENGTH_SHORT).show()
        }

        imageView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x / imageView.width
                val y = event.y / imageView.height
                selectedX = String.format("%.2f", x).toFloat()
                selectedY = String.format("%.2f", y).toFloat()
                Toast.makeText(this, "Selected: ($selectedX, $selectedY)", Toast.LENGTH_SHORT).show()
            }
            true
        }

        scanButton.setOnClickListener {
            if (selectedX < 0 || selectedY < 0) {
                Toast.makeText(this, "Select a point first", Toast.LENGTH_SHORT).show()
            } else {
                startWifiScan()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun startWifiScan() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) !=
            PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "Wi-Fi 스캔을 위해 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Wi-Fi를 먼저 켜주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val wifiReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val results = wifiManager.scanResults
                    unregisterReceiver(this)

                    val apList = results.map {
                        "${it.SSID}, ${it.BSSID}, ${it.level} dBm"
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Scanned APs")
                        .setItems(apList.toTypedArray(), null)
                        .setPositiveButton("Save") { _, _ ->
                            saveScanData(selectedX, selectedY, results)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            wifiManager.startScan()

        } catch (e: SecurityException) {
            Toast.makeText(this, "권한 오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
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

            if (requestCode == 1 && grantResults.isNotEmpty()
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "위치 권한 허용됨", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Wi-Fi 스캔을 위해 위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }
}