package com.scania.droneautofly

import android.Manifest
import android.content.Intent
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
import com.scania.droneautofly.model.DroneStatus
import com.scania.droneautofly.model.ScannedBarcode
import com.scania.droneautofly.ui.AIControlActivity
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
    private lateinit var btnAIControl: Button
    private lateinit var btnAnalytics: Button
    private lateinit var btnFlightPath: Button
    private lateinit var imgStreamPlaceholder: ImageView
    private lateinit var textureVideoStream: TextureView
    
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
        // Status elements
        tvConnectionStatus = findViewById(R.id.tv_connection_status)
        tvBatteryLevel = findViewById(R.id.tv_battery_level)
        tvAltitude = findViewById(R.id.tv_altitude)
        tvMissionStatus = findViewById(R.id.tv_mission_status)
        tvScannedBarcodes = findViewById(R.id.tv_scanned_barcodes)
        
        // Video stream elements
        imgStreamPlaceholder = findViewById(R.id.img_stream_placeholder)
        textureVideoStream = findViewById(R.id.texture_video_stream)
        
        // Buttons
        btnStartMission = findViewById(R.id.btn_start_mission)
        btnPauseMission = findViewById(R.id.btn_pause_mission)
        btnStopMission = findViewById(R.id.btn_stop_mission)
        btnAIControl = findViewById(R.id.btn_ai_control)
        btnAnalytics = findViewById(R.id.btn_analytics)
        btnFlightPath = findViewById(R.id.btn_flight_path)
        
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
            btnPauseMission.text = "Retomar"
            btnPauseMission.setOnClickListener {
                missionExecutor.resumeMission()
                btnPauseMission.text = "Pausar"
                btnPauseMission.setOnClickListener { missionExecutor.pauseMission() }
            }
        }
        
        btnStopMission.setOnClickListener {
            // Show confirmation dialog
            AlertDialog.Builder(this)
                .setTitle("Parar Missão")
                .setMessage("Tem certeza que deseja parar a missão? O drone retornará ao ponto inicial.")
                .setPositiveButton("Sim") { _, _ ->
                    missionExecutor.stopMission()
                    updateButtonStates(false)
                }
                .setNegativeButton("Não", null)
                .show()
        }
        
        btnAIControl.setOnClickListener {
            // Abrir a tela de controle por IA
            val intent = Intent(this, AIControlActivity::class.java)
            startActivity(intent)
        }
        
        btnAnalytics.setOnClickListener {
            // Abrir o dashboard de análise de códigos de barras
            val intent = Intent(this, BarcodeAnalyticsActivity::class.java)
            startActivity(intent)
        }
        
        btnFlightPath.setOnClickListener {
            // Abrir a visualização do caminho de voo
            val intent = Intent(this, FlightPathActivity::class.java)
            startActivity(intent)
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
                tvConnectionStatus.text = "Conectado ao drone DJI"
                btnStartMission.isEnabled = true
                
                // Quando o drone estiver conectado, temos stream de vídeo disponível,
                // então escondemos o placeholder e mostramos o stream
                imgStreamPlaceholder.visibility = View.GONE
                textureVideoStream.visibility = View.VISIBLE
                
                // Iniciar o streaming de vídeo (implementação simplificada)
                startVideoStreaming()
            } else {
                tvConnectionStatus.text = "Desconectado do drone"
                btnStartMission.isEnabled = false
                btnPauseMission.isEnabled = false
                btnStopMission.isEnabled = false
                
                // Quando o drone estiver desconectado, escondemos o stream 
                // e mostramos o placeholder com o logo da Scania
                imgStreamPlaceholder.visibility = View.VISIBLE
                textureVideoStream.visibility = View.GONE
            }
        }
    }
    
    private fun startVideoStreaming() {
        // Implementação simplificada do início do streaming
        // Em um app real, aqui configurariamos o LiveStreamManager da DJI
        val product = DJIApplication.getProductInstance()
        if (product != null) {
            Log.d(TAG, "Iniciando streaming de vídeo do drone")
            
            // Aqui seria a configuração do streaming em um app real
            try {
                val liveStreamManager = LiveStreamManager.getInstance()
                // Configuração e início do streaming
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar streaming: ${e.message}")
            }
        } else {
            Log.e(TAG, "Produto não conectado, não é possível iniciar streaming")
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
        missionExecutor.stopMission()
        droneConnectionHelper.dispose()
    }
}