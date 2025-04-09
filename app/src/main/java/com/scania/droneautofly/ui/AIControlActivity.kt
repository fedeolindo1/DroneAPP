package com.scania.droneautofly.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.scania.droneautofly.AIController
import com.scania.droneautofly.DJIApplication
import com.scania.droneautofly.DroneConnectionHelper
import com.scania.droneautofly.MissionExecutor
import com.scania.droneautofly.R
import com.scania.droneautofly.model.DroneStatus
import kotlinx.coroutines.launch
import org.json.JSONObject

class AIControlActivity : AppCompatActivity(), DroneConnectionHelper.DroneStatusCallback {
    private val TAG = "AIControlActivity"
    
    // UI Components
    private lateinit var tvDroneStatus: TextView
    private lateinit var tvAiResponse: TextView
    private lateinit var etCommand: EditText
    private lateinit var btnSendCommand: Button
    private lateinit var btnCapture: ImageButton
    private lateinit var btnExecuteAction: Button
    
    // AI Controller
    private lateinit var aiController: AIController
    
    // Drone Components
    private lateinit var droneConnectionHelper: DroneConnectionHelper
    private lateinit var missionExecutor: MissionExecutor
    
    // Current state
    private var currentDroneStatus: DroneStatus? = null
    private var lastActionJson: JSONObject? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_control)
        
        // Initialize UI components
        tvDroneStatus = findViewById(R.id.tv_drone_status)
        tvAiResponse = findViewById(R.id.tv_ai_response)
        etCommand = findViewById(R.id.et_command)
        btnSendCommand = findViewById(R.id.btn_send_command)
        btnCapture = findViewById(R.id.btn_capture)
        btnExecuteAction = findViewById(R.id.btn_execute_action)
        
        // Initialize AI Controller
        aiController = AIController()
        
        // Initialize Drone Connection
        droneConnectionHelper = DroneConnectionHelper()
        droneConnectionHelper.setStatusCallback(this)
        droneConnectionHelper.initialize()
        
        // Initialize Mission Executor
        missionExecutor = MissionExecutor(this)
        
        // Set button listeners
        setupButtonListeners()
    }
    
    private fun setupButtonListeners() {
        // Send command to AI
        btnSendCommand.setOnClickListener {
            val command = etCommand.text.toString().trim()
            if (command.isNotEmpty()) {
                processAICommand(command)
            } else {
                Toast.makeText(this, "Por favor, digite um comando", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Capture image and analyze
        btnCapture.setOnClickListener {
            captureAndAnalyzeImage()
        }
        
        // Execute recommended action
        btnExecuteAction.setOnClickListener {
            executeAction()
        }
    }
    
    private fun processAICommand(command: String) {
        lifecycleScope.launch {
            try {
                // Mostrar que está processando
                tvAiResponse.text = "Processando comando..."
                btnSendCommand.isEnabled = false
                
                // Processar o comando com a IA
                val result = aiController.processCommand(command, currentDroneStatus)
                lastActionJson = result
                
                // Mostrar a resposta
                val explanation = result.optString("explanation", "Não foi possível interpretar a resposta")
                val action = result.optString("action", "unknown")
                
                val displayText = "Ação: $action\n\nExplicação: $explanation"
                tvAiResponse.text = displayText
                
                // Habilitar o botão de executar
                btnExecuteAction.isEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar comando: ${e.message}")
                tvAiResponse.text = "Erro: ${e.message}"
                btnExecuteAction.isEnabled = false
            } finally {
                btnSendCommand.isEnabled = true
            }
        }
    }
    
    private fun captureAndAnalyzeImage() {
        // Acessar a câmera do drone
        val camera = DJIApplication.getCameraInstance()
        if (camera == null) {
            Toast.makeText(this, "Câmera do drone não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        
        tvAiResponse.text = "Capturando imagem..."
        btnCapture.isEnabled = false
        
        // Código para capturar imagem e analisá-la com IA
        // Este é apenas um esqueleto, a implementação completa dependeria 
        // da API específica do DJI SDK para captura e processamento de imagens
        
        // Após obter a imagem, poderia fazer algo como:
        // lifecycleScope.launch {
        //     val result = aiController.analyzeImage(bitmap, currentDroneStatus)
        //     // Processar resultado
        // }
        
        // Por enquanto, apenas mostrar uma mensagem
        Toast.makeText(this, "Funcionalidade de captura em desenvolvimento", Toast.LENGTH_SHORT).show()
        btnCapture.isEnabled = true
        tvAiResponse.text = "A análise de imagens estará disponível em breve"
    }
    
    private fun executeAction() {
        val actionJson = lastActionJson ?: return
        
        try {
            val action = actionJson.optString("action", "")
            val parameters = actionJson.optJSONObject("parameters")
            
            when (action) {
                "takeoff" -> {
                    // Iniciar decolagem
                    Toast.makeText(this, "Iniciando decolagem", Toast.LENGTH_SHORT).show()
                    // missionExecutor.takeOff()
                }
                "land" -> {
                    // Pousar
                    Toast.makeText(this, "Iniciando pouso", Toast.LENGTH_SHORT).show()
                    // missionExecutor.land()
                }
                "return_home" -> {
                    // Retornar para casa
                    Toast.makeText(this, "Retornando para casa", Toast.LENGTH_SHORT).show()
                    // missionExecutor.returnToHome()
                }
                "move_to_location" -> {
                    // Mover para coordenada
                    val latitude = parameters?.optDouble("latitude", 0.0) ?: 0.0
                    val longitude = parameters?.optDouble("longitude", 0.0) ?: 0.0
                    val altitude = parameters?.optDouble("altitude", 0.0) ?: 0.0
                    
                    Toast.makeText(this, "Movendo para lat: $latitude, lon: $longitude, alt: $altitude", Toast.LENGTH_SHORT).show()
                    // missionExecutor.moveToLocation(latitude, longitude, altitude)
                }
                "take_photo" -> {
                    // Tirar foto
                    Toast.makeText(this, "Tirando foto", Toast.LENGTH_SHORT).show()
                    // Código para capturar foto
                }
                "start_barcode_mission" -> {
                    // Iniciar missão de escaneamento
                    Toast.makeText(this, "Iniciando missão de escaneamento de códigos", Toast.LENGTH_SHORT).show()
                    // missionExecutor.startMission()
                }
                "pause_mission" -> {
                    // Pausar missão
                    Toast.makeText(this, "Pausando missão", Toast.LENGTH_SHORT).show()
                    // missionExecutor.pauseMission()
                }
                "resume_mission" -> {
                    // Continuar missão
                    Toast.makeText(this, "Continuando missão", Toast.LENGTH_SHORT).show()
                    // missionExecutor.resumeMission()
                }
                "cancel_mission" -> {
                    // Cancelar missão
                    Toast.makeText(this, "Cancelando missão", Toast.LENGTH_SHORT).show()
                    // missionExecutor.stopMission()
                }
                else -> {
                    Toast.makeText(this, "Ação desconhecida: $action", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao executar ação: ${e.message}")
            Toast.makeText(this, "Erro ao executar ação: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Implementação de DroneStatusCallback
    override fun onStatusUpdate(status: DroneStatus) {
        currentDroneStatus = status
        
        runOnUiThread {
            // Atualizar interface com status do drone
            val statusText = "Bateria: ${status.batteryPercent.toInt()}%\n" +
                           "Altitude: ${status.altitude.toInt()}m\n" +
                           "GPS: ${status.latitude}, ${status.longitude}\n" +
                           "Modo: ${status.flightMode}\n" +
                           "Voando: ${if (status.isFlying) "Sim" else "Não"}"
            
            tvDroneStatus.text = statusText
        }
    }
    
    override fun onConnectionChanged(isConnected: Boolean) {
        runOnUiThread {
            if (isConnected) {
                Toast.makeText(this, "Drone conectado", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Drone desconectado", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onHomeLocationSet(success: Boolean) {
        runOnUiThread {
            if (success) {
                Toast.makeText(this, "Localização de casa definida", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        droneConnectionHelper.initialize()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        droneConnectionHelper.dispose()
    }
}