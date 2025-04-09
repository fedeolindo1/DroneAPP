package com.djidronecontrol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.djidronecontrol.model.DroneStatus
import com.djidronecontrol.model.ScannedBarcode
import dji.sdk.sdkmanager.DJISDKManager
import dji.sdk.sdkmanager.LiveStreamManager
import dji.sdk.products.Aircraft
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), DroneConnectionHelper.DroneStatusCallback, MissionExecutor.MissionCallback {
    private val TAG = "MainActivity"
    
    // Permissions
    private val PERMISSION_REQUEST_CODE = 100
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.VIBRATE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )
    private val REQUIRED_PERMISSIONS_ANDROID12 = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )
    
    // UI Components
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvBatteryLevel: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvMissionStatus: TextView
    private lateinit var tvScannedBarcodes: TextView
    private lateinit var btnStartMission: Button
    private lateinit var btnPauseMission: Button
    private lateinit var btnStopMission: Button
    
    // Drone related components
    private lateinit var droneConnectionHelper: DroneConnectionHelper
    private lateinit var missionExecutor: MissionExecutor
    
    // State
    private val isRegistered = AtomicBoolean(false)
    private var barcodeCount = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize UI
        initUI()
        
        // Request permissions
        checkAndRequestPermissions()
        
        // Initialize drone connection
        droneConnectionHelper = DroneConnectionHelper()
        droneConnectionHelper.setStatusCallback(this)
        
        // Initialize mission executor
        missionExecutor = MissionExecutor(this)
        missionExecutor.setMissionCallback(this)
    }
    
    private fun initUI() {
        tvConnectionStatus = findViewById(R.id.tv_connection_status)
        tvBatteryLevel = findViewById(R.id.tv_battery_level)
        tvAltitude = findViewById(R.id.tv_altitude)
        tvMissionStatus = findViewById(R.id.tv_mission_status)
        tvScannedBarcodes = findViewById(R.id.tv_scanned_barcodes)
        
        btnStartMission = findViewById(R.id.btn_start_mission)
        btnPauseMission = findViewById(R.id.btn_pause_mission)
        btnStopMission = findViewById(R.id.btn_stop_mission)
        
        // Set initial button states
        btnStartMission.isEnabled = false
        btnPauseMission.isEnabled = false
        btnStopMission.isEnabled = false
        
        // Set button listeners
        btnStartMission.setOnClickListener {
            missionExecutor.startMission()
            updateButtonStates(true)
        }
        
        btnPauseMission.setOnClickListener {
            missionExecutor.pauseMission()
            btnPauseMission.text = "Resume"
            btnPauseMission.setOnClickListener {
                missionExecutor.resumeMission()
                btnPauseMission.text = "Pause"
                btnPauseMission.setOnClickListener { missionExecutor.pauseMission() }
            }
        }
        
        btnStopMission.setOnClickListener {
            // Show confirmation dialog
            AlertDialog.Builder(this)
                .setTitle("Stop Mission")
                .setMessage("Are you sure you want to stop the mission? The drone will return to home.")
                .setPositiveButton("Yes") { _, _ ->
                    missionExecutor.stopMission()
                    updateButtonStates(false)
                }
                .setNegativeButton("No", null)
                .show()
        }
    }
    
    private fun updateButtonStates(missionActive: Boolean) {
        btnStartMission.isEnabled = !missionActive
        btnPauseMission.isEnabled = missionActive
        btnStopMission.isEnabled = missionActive
        
        if (missionActive) {
            btnPauseMission.text = "Pause"
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = ArrayList<String>()
        
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        
        // Add Android 12 specific Bluetooth permissions if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (permission in REQUIRED_PERMISSIONS_ANDROID12) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission)
                }
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // All permissions are already granted
            startSDKRegistration()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted
                startSDKRegistration()
            } else {
                // Some permissions denied
                Toast.makeText(this, "Some permissions denied. The app may not function correctly.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startSDKRegistration() {
        if (isRegistered.get()) {
            return
        }
        
        tvConnectionStatus.text = "Registering DJI SDK..."
        
        // The DJI SDK is registered in the Application class
        // Here we just need to check the registration status
        if (DJISDKManager.getInstance().hasSDKRegistered()) {
            isRegistered.set(true)
            tvConnectionStatus.text = "DJI SDK registered, connecting to product..."
            droneConnectionHelper.initialize()
        } else {
            Log.e(TAG, "DJI SDK registration has not completed successfully")
            tvConnectionStatus.text = "DJI SDK registration failed. Restart app."
        }
    }
    
    // DroneStatusCallback implementation
    override fun onStatusUpdate(status: DroneStatus) {
        runOnUiThread {
            tvBatteryLevel.text = "Battery: ${status.batteryPercent.toInt()}%"
            tvAltitude.text = "Altitude: ${status.altitude.toInt()}m"
            
            // Enable start mission button if drone is properly connected
            if (!btnStartMission.isEnabled && status.isFlying.not()) {
                btnStartMission.isEnabled = true
            }
        }
    }
    
    override fun onConnectionChanged(isConnected: Boolean) {
        runOnUiThread {
            if (isConnected) {
                tvConnectionStatus.text = "Connected to DJI drone"
                btnStartMission.isEnabled = true
            } else {
                tvConnectionStatus.text = "Disconnected from drone"
                btnStartMission.isEnabled = false
                btnPauseMission.isEnabled = false
                btnStopMission.isEnabled = false
            }
        }
    }
    
    override fun onHomeLocationSet(success: Boolean) {
        runOnUiThread {
            if (success) {
                Toast.makeText(this, "Home location set successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to set home location", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // MissionCallback implementation
    override fun onMissionStatusUpdate(phase: MissionExecutor.MissionPhase, message: String) {
        runOnUiThread {
            tvMissionStatus.text = "Status: $message"
            
            when (phase) {
                MissionExecutor.MissionPhase.COMPLETED -> {
                    updateButtonStates(false)
                    Toast.makeText(this, "Mission completed", Toast.LENGTH_LONG).show()
                }
                MissionExecutor.MissionPhase.ERROR -> {
                    updateButtonStates(false)
                    Toast.makeText(this, "Mission error: $message", Toast.LENGTH_LONG).show()
                }
                else -> { /* Other states don't need special handling */ }
            }
        }
    }
    
    override fun onMissionProgress(completedTargets: Int, totalDetected: Int) {
        runOnUiThread {
            // Update progress information
        }
    }
    
    override fun onBarcodeScanned(barcode: ScannedBarcode) {
        runOnUiThread {
            barcodeCount++
            val currentText = tvScannedBarcodes.text.toString()
            val newBarcode = "${barcodeCount}. ${barcode.barcodeValue} (${barcode.barcodeFormat})"
            
            if (currentText.isEmpty() || currentText == "No barcodes scanned yet") {
                tvScannedBarcodes.text = newBarcode
            } else {
                tvScannedBarcodes.text = "$currentText\n$newBarcode"
            }
            
            Toast.makeText(this, "New barcode scanned: ${barcode.barcodeValue}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onMissionCompleted(totalScanned: Int) {
        runOnUiThread {
            tvMissionStatus.text = "Mission completed. Scanned $totalScanned barcodes."
            updateButtonStates(false)
            
            // Show completion dialog
            AlertDialog.Builder(this)
                .setTitle("Mission Complete")
                .setMessage("The mission has been completed. Scanned $totalScanned barcodes.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    override fun onMissionError(error: String) {
        runOnUiThread {
            tvMissionStatus.text = "Error: $error"
            updateButtonStates(false)
            
            Toast.makeText(this, "Mission error: $error", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        droneConnectionHelper.initialize()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        missionExecutor.dispose()
        droneConnectionHelper.dispose()
    }
}
