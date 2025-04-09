package com.djidronecontrol

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.camera.Camera
import dji.sdk.products.Aircraft
import dji.sdk.products.HandHeld
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager
import java.io.File

class DJIApplication : Application() {
    private val TAG = DJIApplication::class.java.simpleName
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private lateinit var appContext: Context

        // Getters for DJI Components
        fun getProductInstance(): BaseProduct? {
            return DJISDKManager.getInstance().product
        }

        fun getAircraftInstance(): Aircraft? {
            val product = getProductInstance()
            if (product != null && product is Aircraft) {
                return product
            }
            return null
        }

        fun getCameraInstance(): Camera? {
            val product = getProductInstance() ?: return null

            if (product is Aircraft) {
                return product.camera
            } else if (product is HandHeld) {
                return product.camera
            }
            return null
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
        // Initialize DJI SDK Manager
        DJISDKManager.getInstance().registerApp(this, object : DJISDKManager.SDKManagerCallback {
            override fun onRegister(error: DJIError?) {
                if (error == DJISDKError.REGISTRATION_SUCCESS) {
                    Log.i(TAG, "DJI SDK Registration Success")
                    handler.post {
                        Toast.makeText(applicationContext, "DJI SDK Registration Success", Toast.LENGTH_SHORT).show()
                    }
                    DJISDKManager.getInstance().startConnectionToProduct()
                } else {
                    Log.e(TAG, "DJI SDK Registration Failed: ${error?.description}")
                    handler.post {
                        Toast.makeText(applicationContext, "DJI SDK Registration Failed: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onProductDisconnect() {
                Log.i(TAG, "DJI Product Disconnected")
                handler.post {
                    Toast.makeText(applicationContext, "DJI Product Disconnected", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onProductConnect(baseProduct: BaseProduct?) {
                Log.i(TAG, "DJI Product Connected")
                handler.post {
                    Toast.makeText(applicationContext, "DJI Product Connected", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onProductChanged(baseProduct: BaseProduct?) {
                Log.i(TAG, "DJI Product Changed")
            }

            override fun onComponentChange(componentKey: BaseProduct.ComponentKey?, oldComponent: BaseComponent?, newComponent: BaseComponent?) {
                Log.i(TAG, "DJI Component Changed: $componentKey")
                newComponent?.setComponentListener { isConnected ->
                    Log.i(TAG, "Component $componentKey connection changed: $isConnected")
                }
            }

            override fun onInitProcess(djisdkInitEvent: DJISDKInitEvent?, i: Int) {
                Log.i(TAG, "DJI SDK Init Progress: $djisdkInitEvent, $i%")
            }

            override fun onDatabaseDownloadProgress(i: Long, l1: Long) {
                Log.i(TAG, "DJI SDK Database Download Progress: $i, $l1")
            }
        })
    }
}
