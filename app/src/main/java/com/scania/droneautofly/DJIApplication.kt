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
import dji.v5.manager.aircraft.Aircraft
import dji.v5.manager.aircraft.Drone
import dji.v5.manager.datacenter.camera.Camera
import dji.v5.manager.datacenter.media.MediaManager
import dji.v5.manager.interfaces.IProductConnectionStateListener
import java.io.File

class DJIApplication : Application() {
    private val TAG = DJIApplication::class.java.simpleName
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private lateinit var appContext: Context

        // Getters for DJI Components
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

        // Method to get application context
        fun getContext(): Context {
            return appContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
        initDJISDK()
        createStorageDirectories()
    }

    private fun createStorageDirectories() {
        try {
            val mediaDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DroneMission")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating storage directories: ${e.message}")
        }
    }

    private fun initDJISDK() {
        // Inicializar o DJI SDK Manager v5
        SDKManager.getInstance().init(this, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                Log.i(TAG, "DJI SDK Registration Success")
                handler.post {
                    Toast.makeText(applicationContext, "DJI SDK Registration Success", Toast.LENGTH_SHORT).show()
                }
                // Iniciar conexão com o produto
                SDKManager.getInstance().enableBridgeModeWithAppIP("192.168.0.1")
                registerProductListener()
            }

            override fun onRegisterFailure(error: IDJIError) {
                Log.e(TAG, "DJI SDK Registration Failed: ${error.description()}")
                handler.post {
                    Toast.makeText(applicationContext, "DJI SDK Registration Failed: ${error.description()}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                Log.i(TAG, "DJI SDK Init Progress: $event, $totalProcess%")
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                Log.i(TAG, "DJI SDK Database Download Progress: $current/$total")
            }
        })
    }
    
    private fun registerProductListener() {
        // Registrar listener de conexão com o produto
        SDKManager.getInstance().registerProductConnectionListener(object : IProductConnectionStateListener {
            override fun onProductConnect(isConnect: Boolean) {
                Log.i(TAG, "DJI Product Connected: $isConnect")
                handler.post {
                    Toast.makeText(applicationContext, 
                        if (isConnect) "Drone Conectado" else "Drone Desconectado", 
                        Toast.LENGTH_SHORT).show()
                }
            }

            override fun onProductChanged(productType: Int, productConnected: Boolean) {
                Log.i(TAG, "DJI Product Changed: type=$productType, connected=$productConnected")
            }
        })
    }
}