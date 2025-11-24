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

    // UI Elements
    private lateinit var accelText: TextView
    private lateinit var gpsText: TextView
    private lateinit var magnitudeText: TextView
    private lateinit var thresholdText: TextView
    private lateinit var timestampText: TextView

    private val LOCATION_REQUEST_CODE = 1001
    private val SHAKE_THRESHOLD = 18.0f
    private val DETECTION_COOLDOWN = 2000 // 2 seconds
    private var lastDetectionTime = 0L

    // Firebase Reference
    private val database = FirebaseDatabase.getInstance().reference

    // Handler to reset "Pothole Detected" text color
    private val handler = Handler()
    private val RED_DISPLAY_DURATION = 3000L // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI initialization
        accelText = findViewById(R.id.accelText)
        gpsText = findViewById(R.id.gpsText)
        magnitudeText = findViewById(R.id.magnitudeText)
        thresholdText = findViewById(R.id.thresholdText)
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

        // Firebase test write
        database.child("test").setValue("Hello from Shradha’s PotholeDetector!")

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
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {

        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(x * x + y * y + z * z)
        accelText.text = "Accelerometer:\nX=%.2f\nY=%.2f\nZ=%.2f".format(x, y, z)
        magnitudeText.text = "Magnitude: %.2f".format(magnitude)

        val now = System.currentTimeMillis()

        if (magnitude > SHAKE_THRESHOLD && now - lastDetectionTime > DETECTION_COOLDOWN) {
            lastDetectionTime = now
            val detectionTimestamp = now // unified timestamp

            // Update UI immediately
            thresholdText.text = "POTHOLE DETECTED"
            thresholdText.setTextColor(0xFFFF0000.toInt())
            timestampText.text = "Detected at: $detectionTimestamp"
            Toast.makeText(this, "Pothole detected!", Toast.LENGTH_SHORT).show()

            // Reset the red color after 3 seconds
            handler.postDelayed({
                thresholdText.text = "Pothole Not Detected"
                thresholdText.setTextColor(0xFF000000.toInt())
            }, RED_DISPLAY_DURATION)

            // Get location and save to Firebase
            getCurrentLocation { location ->
                if (location != null) {
                    gpsText.text = "Lat: ${location.latitude}, Lon: ${location.longitude}"
                    savePothole(location.latitude, location.longitude, detectionTimestamp)
                } else {
                    gpsText.text = "Location unavailable"
                }
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

    private fun savePothole(lat: Double, lon: Double, timestamp: Long) {
        val potholeData = mapOf(
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to timestamp
        )

        database.child("potholes").push()
            .setValue(potholeData)
            .addOnSuccessListener { Log.d("RealtimeDB", "Pothole saved!") }
            .addOnFailureListener { e ->
                Log.e("RealtimeDB", "Error saving pothole", e)
            }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
