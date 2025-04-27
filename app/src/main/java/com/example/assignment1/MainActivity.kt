package com.example.assignment1

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var frameLayout: FrameLayout
    private lateinit var imageView: ImageView
    private lateinit var toggleMapButton: Button
    private lateinit var rotateButton: Button
    private lateinit var exportButton: Button
    private lateinit var wardrivingButton: Button
    private lateinit var localizationButton: Button
    private lateinit var predictionLabel: TextView

    private var currentImageUri: Uri? = null
    private var rotateAngle = 0f
    private var isMapUploaded = false
    private var isWardrivingMode = false
    private var blueMarker: View? = null

    private lateinit var wifiManager: WifiManager
    private val markerPositions = mutableSetOf<Pair<Float, Float>>()
    private val markerViews = mutableMapOf<Pair<Float, Float>, View>()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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
        setContentView(R.layout.activity_main)

        frameLayout = findViewById(R.id.mapContainer)
        imageView = findViewById(R.id.mapImageView)
        toggleMapButton = findViewById(R.id.btnToggleMap)
        rotateButton = findViewById(R.id.btnRotateMap)
        exportButton = findViewById(R.id.btnExportCsv)
        wardrivingButton = findViewById(R.id.btnWardriving)
        localizationButton = findViewById(R.id.btnLocalization)
        predictionLabel = findViewById(R.id.txtPrediction)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        predictionLabel.visibility = View.GONE

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
                frameLayout.removeViews(1, frameLayout.childCount - 1)
                markerPositions.clear()
                markerViews.clear()
                blueMarker = null
                predictionLabel.text = ""
                predictionLabel.visibility = View.GONE
                wardrivingButton.isEnabled = true
                localizationButton.isEnabled = true
                rotateButton.isEnabled = true
                stopLocalizationLoop()
                File(filesDir, "wardriving_data.csv").delete()
                Toast.makeText(this, "Map deleted and data cleared", Toast.LENGTH_SHORT).show()
            }
        }

        rotateButton.setOnClickListener {
            rotateAngle = (rotateAngle + 90f) % 360
            imageView.rotation = rotateAngle
        }

        exportButton.setOnClickListener {
            val file = File(filesDir, "wardriving_data.csv")

            // 로그로 파일 경로 & 존재 여부 확인
            Log.d("Export", "File exists: ${file.exists()} - path: ${file.absolutePath}")

            if (!file.exists()) {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // FileProvider를 이용해서 uri 만들기
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Wardriving Data")
                putExtra(Intent.EXTRA_STREAM, uri)


                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(Intent.createChooser(intent, "Send CSV via Email"))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "No email app found.", Toast.LENGTH_SHORT).show()
            }
        }

        wardrivingButton.setOnClickListener {
            if (isMapUploaded) {
                isWardrivingMode = true
                wardrivingButton.isEnabled = false
                localizationButton.isEnabled = true
                rotateButton.isEnabled = false
                stopLocalizationLoop()
                blueMarker?.let {
                    frameLayout.removeView(it)
                    blueMarker = null
                }
                markerViews.values.forEach { it.visibility = View.VISIBLE }
                predictionLabel.text = ""
                predictionLabel.visibility = View.GONE
                if (File(filesDir, "wardriving_data.csv").exists()) {
                    File(filesDir, "wardriving_data.csv").readLines().forEach { line ->
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            val x = parts[0].toFloat()
                            val y = parts[1].toFloat()
                            if (!markerPositions.contains(Pair(x, y))) {
                                addLoadedMarker(x, y)
                            }
                        }
                    }
                }
                Toast.makeText(this, "Tap on the map to scan.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please upload a map first.", Toast.LENGTH_SHORT).show()
            }
        }

        localizationButton.setOnClickListener {
            if (!isMapUploaded) {
                Toast.makeText(this, "Please upload a map first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Location permissions are not granted.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val file = File(filesDir, "wardriving_data.csv")
            if (!file.exists()) {
                Toast.makeText(this@MainActivity, "No saved data to compare.", Toast.LENGTH_SHORT)
                    .show()
            } else {
                isWardrivingMode = false
                wardrivingButton.isEnabled = true
                localizationButton.isEnabled = false
                rotateButton.isEnabled = false
                markerViews.values.forEach { it.visibility = View.INVISIBLE }

                startLocalizationLoop()
            }
        }

        frameLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isWardrivingMode) {
                val imageRect = getImageDisplayRectConsideringRotation(imageView)
                    ?: return@setOnTouchListener true

                // 이미지 내부 터치인지 확인
                if (!imageRect.contains(event.x, event.y)) return@setOnTouchListener true

                val normX = (event.x - imageRect.left) / imageRect.width()
                val normY = (event.y - imageRect.top) / imageRect.height()

                AlertDialog.Builder(this)
                    .setTitle("Wi-Fi Scan")
                    .setMessage("Do you want to scan Wi-Fi data here?")
                    .setPositiveButton("Yes") { _, _ ->
                        addMarker(normX, normY)
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
            true
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    private fun addMarker(normX: Float, normY: Float) {
        if (markerPositions.any { (x, y) ->
                Math.abs(x - normX) < 0.1 && Math.abs(y - normY) < 0.1
            }) {
            Toast.makeText(this, "Marker already exists nearby.", Toast.LENGTH_SHORT).show()
            return
        }
        val imageRect = getImageDisplayRectConsideringRotation(imageView) ?: return

        val imgX = (imageRect.left + imageRect.width() * normX).toInt()
        val imgY = (imageRect.top + imageRect.height() * normY).toInt()

        val marker = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(10.dp, 10.dp).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = imgX - 5.dp
                topMargin = imgY - 5.dp
            }
            background = ContextCompat.getDrawable(context, R.drawable.red_circle)
            visibility = if (isWardrivingMode) View.VISIBLE else View.INVISIBLE
            setOnClickListener {
                if (isWardrivingMode) showMarkerMenu(this, normX, normY)
            }
        }
        if (!startWifiScan(normX, normY)) return
        frameLayout.addView(marker)
        markerPositions.add(Pair(normX, normY))
        markerViews[Pair(normX, normY)] = marker

    }
    // csv 복원용 addLoadedMarker
    private fun addLoadedMarker(x: Float, y: Float) {
        val imageRect = getImageDisplayRectConsideringRotation(imageView) ?: return

        val imgX = (imageRect.left + imageRect.width() * x).toInt()
        val imgY = (imageRect.top + imageRect.height() * y).toInt()

        val marker = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(10.dp, 10.dp).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = imgX - 5.dp
                topMargin = imgY - 5.dp
            }
            background = ContextCompat.getDrawable(context, R.drawable.red_circle)
            visibility = if (isWardrivingMode) View.VISIBLE else View.INVISIBLE
            setOnClickListener {
                if (isWardrivingMode) showMarkerMenu(this, x, y)
            }
        }

        frameLayout.addView(marker)
        markerPositions.add(Pair(x, y))
        markerViews[Pair(x, y)] = marker
    }


    private fun showMarkerMenu(markerView: View, x: Float, y: Float) {
        val options = arrayOf("Add new Wi-Fi data", "Delete all data", "See the Data", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("What would like to do for this location?")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> startWifiScan(x, y)
                    1 -> {
                        frameLayout.removeView(markerView)
                        markerPositions.remove(Pair(x, y))
                        markerViews.remove(Pair(x, y))
                        deleteMarkerDataFromCsv(x, y)
                    }

                    2 -> showSavedData(x, y)
                    3 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun showPredictionMarker(normX: Float, normY: Float) {
        val imageRect = getImageDisplayRectConsideringRotation(imageView) ?: return

        val imgX = (imageRect.left + imageRect.width() * normX).toInt()
        val imgY = (imageRect.top + imageRect.height() * normY).toInt()

        blueMarker?.let { frameLayout.removeView(it) }
        blueMarker = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(12.dp, 12.dp).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = imgX - 6.dp
                topMargin = imgY - 6.dp
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.BLUE)
            }
        }
        frameLayout.addView(blueMarker)
        // 스캔 완료 시 깜빡임 애니메이션
        blueMarker?.animate()?.alpha(0f)?.setDuration(400)
            ?.withEndAction {
                blueMarker?.animate()?.alpha(1f)?.setDuration(400)?.start()
            }?.start()
    }

    private fun getImageDisplayRectConsideringRotation(imageView: ImageView): RectF? {
        val drawable = imageView.drawable ?: return null
        val matrix = Matrix()
        matrix.postRotate(imageView.rotation, imageView.width / 2f, imageView.height / 2f)

        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()

        val imageRatio = imageWidth / imageHeight
        val viewRatio = viewWidth / viewHeight

        val scale: Float
        val dx: Float
        val dy: Float

        if (imageRatio > viewRatio) {
            scale = viewWidth / imageWidth
            dx = 0f
            dy = (viewHeight - imageHeight * scale) / 2f
        } else {
            scale = viewHeight / imageHeight
            dx = (viewWidth - imageWidth * scale) / 2f
            dy = 0f
        }

        val rect = RectF(0f, 0f, imageWidth * scale, imageHeight * scale)
        rect.offset(dx, dy)
        matrix.mapRect(rect)
        return rect
    }

    private fun showSavedData(x: Float, y: Float) {
        val file = File(filesDir, "wardriving_data.csv")
        if (!file.exists()) {
            Toast.makeText(this, "No data saved.", Toast.LENGTH_SHORT).show()
            return
        }
        val lines = file.readLines().filter { it.startsWith("$x,$y,") }
        val message = if (lines.isEmpty()) "No data at this location." else lines.joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("Saved Data")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startWifiScan(x: Float, y: Float): Boolean {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val results = wifiManager.scanResults
                    unregisterReceiver(this)

                    val timestamp = System.currentTimeMillis()
                    val data = results.joinToString("\n") {
                        "$x,$y,$timestamp,${it.SSID},${it.BSSID},${it.level}"
                    }
                    File(filesDir, "wardriving_data.csv").appendText("$data\n")

                    Toast.makeText(this@MainActivity, "Wi-Fi data saved.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            val success = wifiManager.startScan()
            if (!success) {
                Toast.makeText(this, "Wi-Fi scan failed to start", Toast.LENGTH_SHORT).show()
                unregisterReceiver(receiver)
                return false
            }

            return true

        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun deleteMarkerDataFromCsv(x: Float, y: Float) {
        val file = File(filesDir, "wardriving_data.csv")
        if (!file.exists()) return

        val newLines = file.readLines().filterNot { line ->
            line.startsWith("$x,$y,")
        }
        file.writeText(newLines.joinToString("\n"))
    }

    private val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics
        ).toInt()
    private var isLocalizationRunning = false
    private val localizationHandler = Handler(Looper.getMainLooper())
    private var scanDelay = 15000L
    private val localizationRunnable = object : Runnable {
        override fun run() {
            if (!isLocalizationRunning) return
            performLocalizationScan()
            localizationHandler.postDelayed(this, scanDelay) // 3초 간격
        }
    }

    private fun startLocalizationLoop() {
        isLocalizationRunning = true
        localizationHandler.post(localizationRunnable)
    }

    private fun stopLocalizationLoop() {
        isLocalizationRunning = false
        localizationHandler.removeCallbacks(localizationRunnable)
    }

    private fun performLocalizationScan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permissions are not granted.", Toast.LENGTH_SHORT).show()
            return
        }

        if (wifiManager.startScan()) {
            Toast.makeText(this, "Wi-Fi Scan Started", Toast.LENGTH_SHORT).show()
            scanDelay = 15000L  // 성공 시 15초 유지

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val results = try {
                        wifiManager.scanResults
                    } catch (e: SecurityException) {
                        Toast.makeText(this@MainActivity, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
                        unregisterReceiver(this)
                        return
                    }
                    unregisterReceiver(this)

                    val file = File(filesDir, "wardriving_data.csv")
                    if (!file.exists()) {
                        Toast.makeText(this@MainActivity, "No saved data to compare.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val currentScan = results.associateBy { it.BSSID }
                    val scores = mutableMapOf<Pair<Float, Float>, Double>()
                    val commonCounts = mutableMapOf<Pair<Float, Float>, Int>()

                    file.readLines().forEach { line ->
                        val parts = line.split(",")
                        if (parts.size < 6) return@forEach
                        val x = parts[0].toFloat()
                        val y = parts[1].toFloat()
                        val bssid = parts[4]
                        val level = parts[5].toInt()

                        if (currentScan.containsKey(bssid)) {
                            val diff = currentScan[bssid]!!.level - level
                            val distance = diff * diff
                            scores[Pair(x, y)] = (scores[Pair(x, y)] ?: 0.0) + distance
                            commonCounts[Pair(x, y)] = (commonCounts[Pair(x, y)] ?: 0) + 1
                        }
                    }
                    if (scores.isNotEmpty()) {
                        val filteredScores = scores.filter { (pos, _) -> (commonCounts[pos] ?: 0) >= 2 }

                        if (filteredScores.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No matching APs found.", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val finalScores = filteredScores.mapValues { (pos, totalDistance) ->
                            val count = commonCounts[pos] ?: 1
                            count * 200 - totalDistance  // 로그 처리 거리
                        }

                        val best = finalScores.maxByOrNull { it.value }!!.key

                        showPredictionMarker(best.first, best.second)

                        predictionLabel.text = "(${String.format("%.2f", best.first)}, ${String.format("%.2f", best.second)})"
                        predictionLabel.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this@MainActivity, "No matching APs found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        } else {
            Toast.makeText(this, "Scan failed, increasing delay", Toast.LENGTH_SHORT).show()
            scanDelay = 30000L  // 실패 시 30초로 증가
        }
    }

}

