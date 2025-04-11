package com.scania.droneautofly

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.register.DJISDKError
import dji.v5.manager.KeyManager
import dji.v5.manager.aircraft.Aircraft
import dji.v5.manager.aircraft.Drone
import dji.v5.manager.datacenter.camera.Camera
import dji.v5.manager.datacenter.media.MediaManager
import dji.v5.manager.interfaces.IProductConnectionStateListener
import dji.v5.manager.diagnostic.DeviceStatusManager
import dji.v5.common.product.ProductType
import java.io.File

class DJIApplication : Application() {
    private val TAG = DJIApplication::class.java.simpleName
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private lateinit var appContext: Context
        private const val DJI_APP_KEY = "b4b60f2edd1d459483d786ac" // Chave da aplicação DJI

        // Getters para componentes DJI usando a API V5
        fun getDroneInstance(): Drone? {
            return SDKManager.getInstance().droneManager.drone
        }

        fun getAircraftInstance(): Aircraft? {
            return SDKManager.getInstance().aircraftManager.aircraft
        }

        fun getCameraInstance(): Camera? {
            return SDKManager.getInstance().datacenterManager.cameraCenter.camera
        }

        fun getMediaManager(): MediaManager? {
            return SDKManager.getInstance().datacenterManager.mediaManager
        }

        fun getKeyManager(): KeyManager {
            return KeyManager.getInstance()
        }

        fun getDeviceStatusManager(): DeviceStatusManager {
            return SDKManager.getInstance().deviceStatusManager
        }

        // Método para obter o contexto da aplicação
        fun getContext(): Context {
            return appContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
        createStorageDirectories()
        initDJISDK()
    }

    private fun createStorageDirectories() {
        try {
            val mediaDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DroneMission")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar diretórios de armazenamento: ${e.message}")
        }
    }

    private fun initDJISDK() {
        // Configurar as opções do SDK de acordo com a documentação oficial
        val sdkManagerCallback = object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                Log.i(TAG, "Registro do SDK DJI bem sucedido")
                handler.post {
                    Toast.makeText(applicationContext, "Registro do SDK DJI bem sucedido", Toast.LENGTH_SHORT).show()
                }
                
                // Configurar o SDK após o registro bem-sucedido
                setupSDK()
            }

            override fun onRegisterFailure(error: IDJIError) {
                Log.e(TAG, "Falha no registro do SDK DJI: ${error.description()}")
                handler.post {
                    Toast.makeText(applicationContext, "Falha no registro do SDK DJI: ${error.description()}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                Log.i(TAG, "Processo de inicialização do SDK DJI: $event, $totalProcess%")
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                val progress = if (total == 0L) 0 else (current * 100 / total).toInt()
                Log.i(TAG, "Progresso de download do banco de dados: $progress%")
            }
        }

        // Inicializar o SDK Manager com a callback configurada
        SDKManager.getInstance().init(this, sdkManagerCallback)
    }
    
    private fun setupSDK() {
        // Registrar listener para o estado de conexão do produto
        SDKManager.getInstance().registerProductConnectionListener(object : IProductConnectionStateListener {
            override fun onProductConnect(isConnect: Boolean) {
                Log.i(TAG, "Produto DJI conectado: $isConnect")
                handler.post {
                    Toast.makeText(applicationContext, 
                        if (isConnect) "Drone conectado" else "Drone desconectado", 
                        Toast.LENGTH_SHORT).show()
                }
            }

            override fun onProductChanged(productType: Int, productConnected: Boolean) {
                val productName = when (productType) {
                    ProductType.DJI_MINI_4_PRO.value() -> "DJI Mini 4 Pro"
                    ProductType.DJI_AIR_3.value() -> "DJI Air 3"
                    ProductType.MAVIC_3.value() -> "Mavic 3"
                    ProductType.MAVIC_3_CLASSIC.value() -> "Mavic 3 Classic"
                    ProductType.MAVIC_3_THERMAL.value() -> "Mavic 3 Thermal"
                    ProductType.DJI_MINI_3.value() -> "DJI Mini 3"
                    ProductType.DJI_MINI_3_PRO.value() -> "DJI Mini 3 Pro"
                    else -> "Desconhecido"
                }
                
                Log.i(TAG, "Produto DJI alterado: $productName, conectado: $productConnected")
            }
        })

        // Habilitar o modo bridge para desenvolvedores (opcional)
        SDKManager.getInstance().enableBridgeModeWithAppIP("192.168.0.1")

        // Iniciar a conexão com o produto
        SDKManager.getInstance().startConnectToProduct()
    }
}