package com.example.potholedetector

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
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
    private lateinit var accelText: TextView
    private lateinit var gpsText: TextView

    private val LOCATION_REQUEST_CODE = 1001
    private val SHAKE_THRESHOLD = 15.0f

    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        accelText = findViewById(R.id.accelText)
        gpsText = findViewById(R.id.gpsText)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // ✅ Test Realtime Database connection
        database.child("test").setValue("Hello from Shradha’s PotholeDetector!")
            .addOnSuccessListener {
                Toast.makeText(this, "Realtime DB connected!", Toast.LENGTH_SHORT).show()
                Log.d("Firebase", "Realtime DB connected")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Realtime DB connection failed", Toast.LENGTH_SHORT).show()
                Log.e("Firebase", "Error: ", e)
            }

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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
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
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val magnitude = sqrt(x * x + y * y + z * z)
            accelText.text = "Accelerometer:\nX=%.2f\nY=%.2f\nZ=%.2f".format(x, y, z)

            if (magnitude > SHAKE_THRESHOLD) {
                getCurrentLocation { location ->
                    if (location != null) {
                        gpsText.text = "Lat: ${location.latitude}, Lon: ${location.longitude}"
                        savePothole(location.latitude, location.longitude)
                        Toast.makeText(this, "Pothole detected!", Toast.LENGTH_SHORT).show()
                    } else {
                        gpsText.text = "Location unavailable"
                    }
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
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            callback(location)
        }.addOnFailureListener {
            callback(null)
        }
    }

    private fun savePothole(lat: Double, lon: Double) {
        val potholeData = mapOf(
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to System.currentTimeMillis()
        )

        val newRef = database.child("potholes").push()
        newRef.setValue(potholeData)
            .addOnSuccessListener { Log.d("RealtimeDB", "Pothole saved!") }
            .addOnFailureListener { e -> Log.e("RealtimeDB", "Error saving pothole", e) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
