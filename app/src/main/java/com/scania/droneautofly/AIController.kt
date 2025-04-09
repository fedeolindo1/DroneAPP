package com.scania.droneautofly

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.scania.droneautofly.model.DroneStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Interface para integração do aplicativo Android com o controlador de IA em Python
 * para processamento de comandos de voz e análise de imagens usando OpenAI
 */
class AIController {
    private val TAG = "AIController"

    /**
     * Processa um comando de linguagem natural e retorna ações para o drone
     * 
     * @param command Comando em linguagem natural
     * @param droneStatus Status atual do drone (opcional)
     * @return JSONObject com a ação a ser executada pelo drone
     */
    suspend fun processCommand(command: String, droneStatus: DroneStatus? = null): JSONObject = withContext(Dispatchers.IO) {
        try {
            // Cria um arquivo temporário com o comando
            val commandFile = File.createTempFile("command", ".json")
            val commandJson = JSONObject().apply {
                put("command", command)
                
                if (droneStatus != null) {
                    val statusObj = JSONObject().apply {
                        put("battery", droneStatus.batteryPercent)
                        put("altitude", droneStatus.altitude)
                        put("latitude", droneStatus.latitude)
                        put("longitude", droneStatus.longitude)
                        put("is_flying", droneStatus.isFlying)
                        put("flight_mode", droneStatus.flightMode)
                    }
                    put("drone_status", statusObj)
                }
            }
            
            FileOutputStream(commandFile).use { out ->
                out.write(commandJson.toString().toByteArray(StandardCharsets.UTF_8))
            }
            
            // Executa o script Python
            val resultJson = executePythonScript(
                "from ai_controller import DroneAIController\n" +
                "import json\n" +
                "import sys\n" +
                "import os\n\n" +
                
                "# Carrega o comando do arquivo\n" +
                "with open('${commandFile.absolutePath}', 'r') as f:\n" +
                "    data = json.load(f)\n\n" +
                
                "controller = DroneAIController()\n" +
                "command = data['command']\n" +
                "drone_status = data.get('drone_status')\n\n" +
                
                "# Processa o comando\n" +
                "result = controller.process_command(command, drone_status)\n" +
                "print(json.dumps(result))"
            )
            
            // Limpa o arquivo temporário
            commandFile.delete()
            
            if (resultJson.isBlank()) {
                return@withContext JSONObject("{\"error\": \"Sem resposta do processador de IA\"}")
            }
            
            return@withContext JSONObject(resultJson)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar comando: ${e.message}")
            return@withContext JSONObject("{\"error\": \"${e.message}\"}")
        }
    }
    
    /**
     * Analisa objetos em uma imagem usando a visão computacional da OpenAI
     * 
     * @param bitmap Imagem a ser analisada
     * @param droneStatus Status atual do drone (opcional)
     * @return JSONObject com os resultados da análise
     */
    suspend fun analyzeImage(bitmap: Bitmap, droneStatus: DroneStatus? = null): JSONObject = withContext(Dispatchers.IO) {
        try {
            // Converte o bitmap para Base64
            val base64Image = bitmapToBase64(bitmap)
            
            // Cria um arquivo temporário com a imagem
            val imageFile = File.createTempFile("image", ".json")
            val imageJson = JSONObject().apply {
                put("image_base64", base64Image)
                
                if (droneStatus != null) {
                    val statusObj = JSONObject().apply {
                        put("battery", droneStatus.batteryPercent)
                        put("altitude", droneStatus.altitude)
                        put("latitude", droneStatus.latitude)
                        put("longitude", droneStatus.longitude)
                        put("is_flying", droneStatus.isFlying)
                    }
                    put("drone_status", statusObj)
                }
            }
            
            FileOutputStream(imageFile).use { out ->
                out.write(imageJson.toString().toByteArray(StandardCharsets.UTF_8))
            }
            
            // Executa o script Python
            val resultJson = executePythonScript(
                "from ai_controller import DroneAIController\n" +
                "import json\n" +
                "import sys\n" +
                "import os\n\n" +
                
                "# Carrega a imagem do arquivo\n" +
                "with open('${imageFile.absolutePath}', 'r') as f:\n" +
                "    data = json.load(f)\n\n" +
                
                "controller = DroneAIController()\n" +
                "image_base64 = data['image_base64']\n" +
                "drone_status = data.get('drone_status')\n\n" +
                
                "# Analisa a imagem\n" +
                "result = controller.analyze_detected_objects(image_base64, drone_status)\n" +
                "print(json.dumps(result))"
            )
            
            // Limpa o arquivo temporário
            imageFile.delete()
            
            if (resultJson.isBlank()) {
                return@withContext JSONObject("{\"error\": \"Sem resposta do analisador de imagem\"}")
            }
            
            return@withContext JSONObject(resultJson)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao analisar imagem: ${e.message}")
            return@withContext JSONObject("{\"error\": \"${e.message}\"}")
        }
    }
    
    /**
     * Gera um plano de missão com base na descrição da área e objetos alvo
     * 
     * @param areaDescription Descrição da área de operação
     * @param targetObjects Descrição dos objetos alvo
     * @param constraints Restrições da missão (opcional)
     * @return JSONObject com o plano de missão gerado
     */
    suspend fun generateMissionPlan(
        areaDescription: String, 
        targetObjects: String, 
        constraints: Map<String, String>? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            // Cria um arquivo temporário com os dados da missão
            val missionFile = File.createTempFile("mission", ".json")
            val missionJson = JSONObject().apply {
                put("area_description", areaDescription)
                put("target_objects", targetObjects)
                
                if (constraints != null) {
                    val constraintsObj = JSONObject()
                    for ((key, value) in constraints) {
                        constraintsObj.put(key, value)
                    }
                    put("constraints", constraintsObj)
                }
            }
            
            FileOutputStream(missionFile).use { out ->
                out.write(missionJson.toString().toByteArray(StandardCharsets.UTF_8))
            }
            
            // Executa o script Python
            val resultJson = executePythonScript(
                "from ai_controller import DroneAIController\n" +
                "import json\n" +
                "import sys\n" +
                "import os\n\n" +
                
                "# Carrega os dados da missão do arquivo\n" +
                "with open('${missionFile.absolutePath}', 'r') as f:\n" +
                "    data = json.load(f)\n\n" +
                
                "controller = DroneAIController()\n" +
                "area_description = data['area_description']\n" +
                "target_objects = data['target_objects']\n" +
                "constraints = data.get('constraints')\n\n" +
                
                "# Gera o plano de missão\n" +
                "result = controller.generate_mission_plan(area_description, target_objects, constraints)\n" +
                "print(json.dumps(result))"
            )
            
            // Limpa o arquivo temporário
            missionFile.delete()
            
            if (resultJson.isBlank()) {
                return@withContext JSONObject("{\"error\": \"Sem resposta do gerador de missão\"}")
            }
            
            return@withContext JSONObject(resultJson)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar plano de missão: ${e.message}")
            return@withContext JSONObject("{\"error\": \"${e.message}\"}")
        }
    }
    
    /**
     * Executa um script Python e retorna a saída como string
     */
    private fun executePythonScript(script: String): String {
        val scriptFile = File.createTempFile("script", ".py")
        
        FileOutputStream(scriptFile).use { out ->
            out.write(script.toByteArray(StandardCharsets.UTF_8))
        }
        
        try {
            val process = ProcessBuilder("python", scriptFile.absolutePath)
                .redirectErrorStream(true)
                .start()
                
            val reader = InputStreamReader(process.inputStream)
            val output = reader.readText()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                Log.e(TAG, "Script Python falhou com código de saída $exitCode: $output")
                return ""
            }
            
            return output.trim()
        } finally {
            scriptFile.delete()
        }
    }
    
    /**
     * Converte um bitmap para uma string Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }
}