package com.djidronecontrol

import android.util.Log
import dji.common.error.DJIError
import dji.common.flightcontroller.ConnectionFailSafeBehavior
import dji.common.flightcontroller.FlightControllerState
import dji.common.flightcontroller.FlightMode
import dji.common.model.LocationCoordinate3D
import dji.sdk.flightcontroller.FlightController
import dji.sdk.products.Aircraft
import dji.sdk.sdkmanager.DJISDKManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import com.djidronecontrol.model.DroneStatus

class DroneConnectionHelper {
    private val TAG = "DroneConnectionHelper"
    private val disposables = CompositeDisposable()
    private var flightController: FlightController? = null
    private var lastKnownState: FlightControllerState? = null
    private var homeLocation: LocationCoordinate3D? = null

    // Callback interface for drone status updates
    interface DroneStatusCallback {
        fun onStatusUpdate(status: DroneStatus)
        fun onConnectionChanged(isConnected: Boolean)
        fun onHomeLocationSet(success: Boolean)
    }

    private var statusCallback: DroneStatusCallback? = null

    fun setStatusCallback(callback: DroneStatusCallback) {
        statusCallback = callback
    }

    fun initialize() {
        val aircraft = DJIApplication.getAircraftInstance()
        if (aircraft != null) {
            flightController = aircraft.flightController
            configureFlightController()
            startStatusListening()
        } else {
            Log.e(TAG, "No aircraft connected")
            startConnectionPolling()
        }
    }

    private fun startConnectionPolling() {
        val disposable = Observable.interval(2, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                val aircraft = DJIApplication.getAircraftInstance()
                if (aircraft != null) {
                    flightController = aircraft.flightController
                    configureFlightController()
                    startStatusListening()
                    disposables.clear() // Stop polling when connected
                    statusCallback?.onConnectionChanged(true)
                }
            }, { error ->
                Log.e(TAG, "Error in connection polling: ${error.message}")
            })
        
        disposables.add(disposable)
    }

    private fun configureFlightController() {
        flightController?.let { fc ->
            // Set return-to-home altitude
            fc.setGoHomeHeightInMeters(20) { error ->
                if (error != null) {
                    Log.e(TAG, "Failed to set RTH altitude: ${error.description}")
                }
            }

            // Configure connection fail-safe behavior
            fc.connectionFailSafeBehavior = ConnectionFailSafeBehavior.GO_HOME

            // Set home location
            fc.setHomeLocationUsingAircraftCurrentLocation { error ->
                if (error == null) {
                    Log.i(TAG, "Home location set successfully")
                    statusCallback?.onHomeLocationSet(true)
                } else {
                    Log.e(TAG, "Failed to set home location: ${error.description}")
                    statusCallback?.onHomeLocationSet(false)
                }
            }
        }
    }

    private fun startStatusListening() {
        flightController?.let { fc ->
            fc.setStateCallback { state ->
                lastKnownState = state
                val status = DroneStatus(
                    batteryPercent = state.aircraftLocation?.altitude ?: 0.0,
                    altitude = state.aircraftLocation?.altitude ?: 0.0,
                    latitude = state.aircraftLocation?.latitude ?: 0.0,
                    longitude = state.aircraftLocation?.longitude ?: 0.0,
                    isFlying = state.isFlying,
                    flightMode = state.flightMode.name,
                    velocity = Triple(
                        state.velocityX.toDouble(),
                        state.velocityY.toDouble(),
                        state.velocityZ.toDouble()
                    ),
                    homeLatitude = fc.homeLocation?.latitude ?: 0.0,
                    homeLongitude = fc.homeLocation?.longitude ?: 0.0
                )
                
                statusCallback?.onStatusUpdate(status)
                
                // Update home location
                if (fc.homeLocation != null && homeLocation == null) {
                    homeLocation = fc.homeLocation
                    statusCallback?.onHomeLocationSet(true)
                }
            }
        }
    }

    fun takeOff(callback: (DJIError?) -> Unit) {
        flightController?.startTakeoff(callback)
    }

    fun land(callback: (DJIError?) -> Unit) {
        flightController?.startLanding(callback)
    }

    fun returnToHome(callback: (DJIError?) -> Unit) {
        flightController?.startGoHome(callback)
    }

    fun moveGimbal(pitch: Float, roll: Float, yaw: Float, callback: (DJIError?) -> Unit) {
        val aircraft = DJIApplication.getAircraftInstance() ?: return
        val gimbal = aircraft.gimbal ?: return
        
        gimbal.rotation.apply {
            setPitch(pitch)
            setRoll(roll)
            setYaw(yaw)
            
            gimbal.rotate(this) { error ->
                callback(error)
            }
        }
    }

    fun moveAircraft(
        pitchJoystick: Float, // Forward/Backward
        rollJoystick: Float,  // Left/Right
        yawJoystick: Float,   // Rotation
        throttleJoystick: Float // Up/Down
    ) {
        flightController?.let { fc ->
            if (fc.isVirtualStickControlModeAvailable) {
                fc.sendVirtualStickFlightControlData(
                    dji.common.flightcontroller.virtualstick.FlightControlData(
                        pitchJoystick,
                        rollJoystick,
                        yawJoystick,
                        throttleJoystick
                    )
                ) { error ->
                    if (error != null) {
                        Log.e(TAG, "Virtual stick command error: ${error.description}")
                    }
                }
            } else {
                Log.e(TAG, "Virtual stick control mode not available")
            }
        }
    }

    fun getCurrentFlightMode(): FlightMode? {
        return lastKnownState?.flightMode
    }

    fun isFlying(): Boolean {
        return lastKnownState?.isFlying ?: false
    }

    fun dispose() {
        disposables.clear()
        statusCallback = null
        flightController?.setStateCallback(null)
    }
}
