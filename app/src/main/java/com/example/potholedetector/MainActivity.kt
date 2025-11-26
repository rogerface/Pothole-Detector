package com.example.potholedetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.android.gms.location.LocationServices
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null // New Sensor

    // UI Elements
    private lateinit var accelText: TextView
    private lateinit var gpsText: TextView
    private lateinit var magnitudeText: TextView
    private lateinit var thresholdText: TextView
    private lateinit var speedBreakerText: TextView // New UI Element
    private lateinit var timestampText: TextView

    private val LOCATION_REQUEST_CODE = 1001

    // TUNED THRESHOLDS
    // Potholes are sharp/hard hits (> 18)
    // TUNED THRESHOLDS
    // Potholes are sharp/hard hits (> 20.0f)
    private val POTHOLE_THRESHOLD = 20.0f // RAISED

    // Speed breakers are softer (> 12.0f) but NOT as hard as potholes (< 16.0f)
    private val SPEED_BREAKER_MIN_THRESHOLD = 12.0f // RAISED
    private val SPEED_BREAKER_MAX_THRESHOLD = 16.0f // RAISED (Wider Range)

    // Increased Rotation Threshold:
    // Speed Breakers PITCH the car (approx > 3.0 rad/s)
    private val ROTATION_THRESHOLD = 3.0f // RAISED
    private val DETECTION_COOLDOWN = 2000 // 2 seconds
    private var lastDetectionTime = 0L

    // ... rest of your class code ...

    // Current Sensor Values for Fusion
    private var currentAccelMagnitude = 0f
    private var currentGyroMagnitude = 0f

    // Firebase Reference
    private val database = FirebaseDatabase.getInstance().reference

    // Handler to reset text color
    private val handler = Handler()
    private val ALERT_DISPLAY_DURATION = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI initialization
        accelText = findViewById(R.id.accelText)
        gpsText = findViewById(R.id.gpsText)
        magnitudeText = findViewById(R.id.magnitudeText)
        thresholdText = findViewById(R.id.thresholdText)
        speedBreakerText = findViewById(R.id.speedBreakerText) // Init new text
        timestampText = findViewById(R.id.timestampText)

        val firebaseLink: TextView = findViewById(R.id.firebaseLink)
        firebaseLink.setOnClickListener {
            val url = "https://potholedetector-dd0d1-default-rtdb.asia-southeast1.firebasedatabase.app/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        // Sensor Setup
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) // Get Gyro

        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            currentAccelMagnitude = sqrt(x * x + y * y + z * z)
            accelText.text = "Accelerometer:\nX=%.2f\nY=%.2f\nZ=%.2f".format(x, y, z)
            magnitudeText.text = "Magnitude: %.2f".format(currentAccelMagnitude)

            // Trigger check logic on Accel updates
            checkForHazard()
        }
        else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val gx = event.values[0]
            val gy = event.values[1]
            val gz = event.values[2]

            // Calculate total rotation magnitude
            currentGyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)
        }
    }

    private fun checkForHazard() {
        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < DETECTION_COOLDOWN) return

        var detectedType = ""

        // 1. POTHOLE CHECK (Priority 1)
        // High Impact. We don't care about rotation here.
        if (currentAccelMagnitude > POTHOLE_THRESHOLD) {
            detectedType = "POTHOLE"
        }

        // 2. SPEED BREAKER CHECK (Priority 2)
        // We add a "Ceiling" (MAX_THRESHOLD).
        // If the impact is > 16.0, it's too hard to be a speed breaker,
        // so we ignore it (it's likely a pothole that just missed the 18.0 cutoff).
        else if (currentAccelMagnitude > SPEED_BREAKER_MIN_THRESHOLD &&
            currentAccelMagnitude < SPEED_BREAKER_MAX_THRESHOLD &&
            currentGyroMagnitude > ROTATION_THRESHOLD) {

            detectedType = "SPEED_BREAKER"
        }

        if (detectedType.isNotEmpty()) {
            lastDetectionTime = now
            triggerDetectionUI(detectedType, now)
        }
    }

    private fun triggerDetectionUI(type: String, timestamp: Long) {
        timestampText.text = "Detected at: $timestamp"

        if (type == "POTHOLE") {
            thresholdText.text = "POTHOLE DETECTED"
            thresholdText.setTextColor(0xFFFF0000.toInt()) // Red
            Toast.makeText(this, "Pothole detected!", Toast.LENGTH_SHORT).show()
        }
        else if (type == "SPEED_BREAKER") {
            speedBreakerText.text = "SPEED BREAKER DETECTED"
            speedBreakerText.setTextColor(0xFFFF8800.toInt()) // Orange
            Toast.makeText(this, "Speed Breaker detected!", Toast.LENGTH_SHORT).show()
        }

        // Reset UI after delay
        handler.postDelayed({
            thresholdText.text = "Pothole Not Detected"
            thresholdText.setTextColor(0xFF000000.toInt())

            speedBreakerText.text = "Speed Breaker Not Detected"
            speedBreakerText.setTextColor(0xFF000000.toInt())
        }, ALERT_DISPLAY_DURATION)

        // Save to Firebase
        getCurrentLocation { location ->
            if (location != null) {
                gpsText.text = "Lat: ${location.latitude}, Lon: ${location.longitude}"
                saveHazard(type, location.latitude, location.longitude, timestamp)
            } else {
                gpsText.text = "Location unavailable"
            }
        }
    }

    private fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { callback(it) }
            .addOnFailureListener { callback(null) }
    }

    // Updated to accept 'type'
    private fun saveHazard(type: String, lat: Double, lon: Double, timestamp: Long) {
        val hazardData = mapOf(
            "type" to type,
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to timestamp
        )

        database.child("hazards").push()
            .setValue(hazardData)
            .addOnSuccessListener { Log.d("RealtimeDB", "$type saved!") }
            .addOnFailureListener { e ->
                Log.e("RealtimeDB", "Error saving data", e)
            }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}