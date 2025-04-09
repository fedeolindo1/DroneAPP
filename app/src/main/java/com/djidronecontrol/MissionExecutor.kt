package com.djidronecontrol

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.djidronecontrol.model.ScannedBarcode
import com.djidronecontrol.utils.ImageUtils
import com.djidronecontrol.utils.LocationUtils
import dji.common.error.DJIError
import dji.common.flightcontroller.LocationCoordinate3D
import dji.common.flightcontroller.VirtualStickFlightControlData
import dji.common.flightcontroller.virtualstick.FlightControlData
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem
import dji.common.flightcontroller.virtualstick.RollPitchControlMode
import dji.common.flightcontroller.virtualstick.VerticalControlMode
import dji.common.flightcontroller.virtualstick.YawControlMode
import dji.common.gimbal.Rotation
import dji.common.gimbal.RotationMode
import dji.common.util.CommonCallbacks
import dji.sdk.flightcontroller.FlightController
import dji.sdk.products.Aircraft
import dji.sdk.camera.Camera
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MissionExecutor(private val context: Context) {
    private val TAG = "MissionExecutor"
    
    // Components
    private val droneConnection = DroneConnectionHelper()
    private val paperDetector = PaperDetector()
    private val barcodeProcessor = BarcodeProcessor()
    
    // State variables
    private val isExecuting = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val disposables = CompositeDisposable()
    private val visitedPositions = mutableListOf<Pair<Double, Double>>()
    private var currentMissionPhase = MissionPhase.IDLE
    private var currentTargetPosition: LocationCoordinate3D? = null
    
    // Mission callback
    private var missionCallback: MissionCallback? = null
    
    // Debugging flags
    private val DEBUG_MODE = false
    
    enum class MissionPhase {
        IDLE,
        TAKEOFF,
        INITIAL_SCAN,
        SEARCHING,
        APPROACHING_TARGET,
        SCANNING_BARCODE,
        RETURNING_HOME,
        LANDING,
        COMPLETED,
        ERROR
    }
    
    interface MissionCallback {
        fun onMissionStatusUpdate(phase: MissionPhase, message: String)
        fun onMissionProgress(completedTargets: Int, totalDetected: Int)
        fun onBarcodeScanned(barcode: ScannedBarcode)
        fun onMissionCompleted(totalScanned: Int)
        fun onMissionError(error: String)
    }
    
    init {
        droneConnection.initialize()
    }
    
    fun setMissionCallback(callback: MissionCallback) {
        missionCallback = callback
    }
    
    fun startMission() {
        if (isExecuting.get()) {
            Log.w(TAG, "Mission already in progress")
            return
        }
        
        isExecuting.set(true)
        isPaused.set(false)
        visitedPositions.clear()
        barcodeProcessor.clearScannedBarcodes()
        updateMissionPhase(MissionPhase.TAKEOFF, "Starting mission")
        
        // Start the mission workflow
        executeMission()
    }
    
    fun pauseMission() {
        if (!isExecuting.get()) {
            return
        }
        
        isPaused.set(true)
        updateMissionPhase(currentMissionPhase, "Mission paused")
    }
    
    fun resumeMission() {
        if (!isExecuting.get() || !isPaused.get()) {
            return
        }
        
        isPaused.set(false)
        updateMissionPhase(currentMissionPhase, "Mission resumed")
    }
    
    fun stopMission() {
        if (!isExecuting.get()) {
            return
        }
        
        disposables.clear()
        
        // Return to home and land if currently flying
        if (droneConnection.isFlying()) {
            updateMissionPhase(MissionPhase.RETURNING_HOME, "Mission stopped, returning home")
            returnToHomeAndLand()
        } else {
            isExecuting.set(false)
            updateMissionPhase(MissionPhase.IDLE, "Mission stopped")
        }
    }
    
    private fun executeMission() {
        val aircraft = DJIApplication.getAircraftInstance()
        if (aircraft == null) {
            updateMissionPhase(MissionPhase.ERROR, "Aircraft not connected")
            isExecuting.set(false)
            return
        }
        
        // Start the mission sequence
        takeOff { takeoffSuccess ->
            if (!takeoffSuccess) {
                updateMissionPhase(MissionPhase.ERROR, "Failed to take off")
                isExecuting.set(false)
                return@takeOff
            }
            
            // Perform initial 360-degree scan
            performInitialScan { scanSuccess ->
                if (!scanSuccess) {
                    updateMissionPhase(MissionPhase.RETURNING_HOME, "Initial scan failed, returning home")
                    returnToHomeAndLand()
                    return@performInitialScan
                }
                
                // Start search and scan process
                searchAndScanPapers()
            }
        }
    }
    
    private fun takeOff(callback: (Boolean) -> Unit) {
        updateMissionPhase(MissionPhase.TAKEOFF, "Taking off")
        
        val aircraft = DJIApplication.getAircraftInstance() ?: run {
            callback(false)
            return
        }
        
        aircraft.flightController?.startTakeoff { error ->
            if (error != null) {
                Log.e(TAG, "Take off error: ${error.description}")
                callback(false)
                return@startTakeoff
            }
            
            // Wait for the aircraft to reach a safe altitude
            val disposable = Observable.interval(1, TimeUnit.SECONDS)
                .takeWhile { isExecuting.get() && !isPaused.get() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { elapsed ->
                        val altitude = aircraft.flightController?.state?.aircraftLocation?.altitude ?: 0.0
                        
                        updateMissionPhase(MissionPhase.TAKEOFF, "Taking off: Altitude ${altitude.toInt()}m")
                        
                        if (altitude >= 2.0 || elapsed >= 15) {
                            Log.i(TAG, "Take off complete at altitude: $altitude meters")
                            callback(true)
                            dispose()
                        }
                    },
                    { error ->
                        Log.e(TAG, "Take off monitoring error: ${error.message}")
                        callback(false)
                    }
                )
            
            disposables.add(disposable)
        }
    }
    
    private fun performInitialScan(callback: (Boolean) -> Unit) {
        updateMissionPhase(MissionPhase.INITIAL_SCAN, "Performing initial 360° scan")
        
        val aircraft = DJIApplication.getAircraftInstance() ?: run {
            callback(false)
            return
        }
        
        val flightController = aircraft.flightController
        val gimbal = aircraft.gimbal
        
        if (flightController == null) {
            callback(false)
            return
        }
        
        // First, tilt gimbal down by 45 degrees to see the ground
        val rotation = Rotation.Builder()
            .mode(RotationMode.ABSOLUTE_ANGLE)
            .pitch(-45f)  // Tilt down 45 degrees
            .roll(0f)
            .yaw(0f)
            .time(1)
            .build()
        
        gimbal?.rotate(rotation) { error ->
            if (error != null) {
                Log.e(TAG, "Gimbal rotation error: ${error.description}")
                // Continue anyway, this is not critical
            }
            
            // Ensure virtual stick mode is enabled
            flightController.setVirtualStickModeEnabled(true) { virtualStickError ->
                if (virtualStickError != null) {
                    Log.e(TAG, "Failed to enable virtual stick mode: ${virtualStickError.description}")
                    callback(false)
                    return@setVirtualStickModeEnabled
                }
                
                // Setup virtual stick flight control parameters
                flightController.rollPitchControlMode = RollPitchControlMode.VELOCITY
                flightController.yawControlMode = YawControlMode.ANGULAR_VELOCITY
                flightController.verticalControlMode = VerticalControlMode.POSITION
                flightController.rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
                
                // Rotate 360 degrees slowly while taking photos
                var currentAngle = 0
                val totalSteps = 8  // Divide the 360 rotation into 8 steps (45 degrees each)
                var successfulPhotos = 0
                
                val rotationDisposable = Observable.interval(3, TimeUnit.SECONDS)
                    .take(totalSteps.toLong())
                    .takeWhile { isExecuting.get() && !isPaused.get() }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { step ->
                            // Calculate yaw rate for smooth rotation
                            val yawRate = 15f  // degrees per second
                            
                            // Apply rotation using virtual stick
                            val flightData = FlightControlData(
                                0f,  // No forward/backward movement
                                0f,  // No left/right movement
                                yawRate,  // Rotate at specified rate
                                0f   // Maintain current height
                            )
                            
                            flightController.sendVirtualStickFlightControlData(flightData, null)
                            
                            currentAngle += 45
                            updateMissionPhase(MissionPhase.INITIAL_SCAN, "Scanning: ${currentAngle}° of 360°")
                            
                            // Take a photo at each step
                            val camera = DJIApplication.getCameraInstance()
                            if (camera != null) {
                                ImageUtils.takePhoto(camera) { photoError ->
                                    if (photoError == null) {
                                        successfulPhotos++
                                        
                                        // Process the photo (we'll do this when we can access the photo)
                                        Log.i(TAG, "Photo taken during scan, step: $step")
                                    } else {
                                        Log.e(TAG, "Failed to take photo: ${photoError.description}")
                                    }
                                }
                            }
                            
                            // On the last step, stop rotation and proceed
                            if (step == totalSteps - 1L) {
                                // Stop rotation
                                val stopData = FlightControlData(0f, 0f, 0f, 0f)
                                flightController.sendVirtualStickFlightControlData(stopData, null)
                                
                                // Disable virtual stick mode
                                flightController.setVirtualStickModeEnabled(false) { disableError ->
                                    if (disableError != null) {
                                        Log.e(TAG, "Failed to disable virtual stick mode: ${disableError.description}")
                                    }
                                    
                                    // Consider scan successful if we got at least some photos
                                    callback(successfulPhotos > 0)
                                }
                            }
                        },
                        { error ->
                            Log.e(TAG, "Error during 360 scan: ${error.message}")
                            callback(false)
                        }
                    )
                
                disposables.add(rotationDisposable)
            }
        }
    }
    
    private fun searchAndScanPapers() {
        updateMissionPhase(MissionPhase.SEARCHING, "Searching for white A4 papers")
        
        // Generate a search pattern from current position
        val aircraft = DJIApplication.getAircraftInstance() ?: run {
            updateMissionPhase(MissionPhase.ERROR, "Aircraft not connected")
            returnToHomeAndLand()
            return
        }
        
        val flightController = aircraft.flightController
        val currentLocation = flightController?.state?.aircraftLocation ?: run {
            updateMissionPhase(MissionPhase.ERROR, "Cannot determine aircraft location")
            returnToHomeAndLand()
            return
        }
        
        // Generate a spiral search pattern around current position
        val searchPoints = LocationUtils.generateSpiralPattern(
            currentLocation.latitude,
            currentLocation.longitude,
            15,  // Generate 15 points
            5.0,  // Start 5 meters from center
            3.0   // Increase radius by 3 meters per full turn
        )
        
        var pointIndex = 0
        val searchDisposable = Observable.interval(5, TimeUnit.SECONDS)
            .takeWhile { isExecuting.get() && !isPaused.get() && pointIndex < searchPoints.size }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    if (pointIndex < searchPoints.size) {
                        // Move to next search point
                        val nextPoint = searchPoints[pointIndex]
                        moveToLocation(
                            nextPoint.first, 
                            nextPoint.second, 
                            currentLocation.altitude
                        ) { moveSuccess ->
                            if (moveSuccess) {
                                // Take a photo at this location
                                captureAndAnalyzeImage { detected ->
                                    if (detected) {
                                        // If paper detected, stop the search pattern and focus on it
                                        dispose()
                                    } else {
                                        // Move to next point
                                        pointIndex++
                                        
                                        // If we've checked all points and found nothing, go home
                                        if (pointIndex >= searchPoints.size) {
                                            updateMissionPhase(MissionPhase.RETURNING_HOME, "Search complete, returning home")
                                            returnToHomeAndLand()
                                        }
                                    }
                                }
                            } else {
                                // If movement failed, try next point
                                pointIndex++
                            }
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "Error during search pattern: ${error.message}")
                    updateMissionPhase(MissionPhase.ERROR, "Search error: ${error.message}")
                    returnToHomeAndLand()
                }
            )
        
        disposables.add(searchDisposable)
    }
    
    private fun captureAndAnalyzeImage(callback: (Boolean) -> Unit) {
        val camera = DJIApplication.getCameraInstance() ?: run {
            callback(false)
            return
        }
        
        // Take a photo
        ImageUtils.takePhoto(camera) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to take photo: ${error.description}")
                callback(false)
                return@takePhoto
            }
            
            // In a real application, we would download and process the photo here
            // For this example, we'll simulate the process with a delay
            disposables.add(
                Observable.timer(2, TimeUnit.SECONDS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            // For demonstration: randomize detection (would be real image processing in production)
                            val detected = if (DEBUG_MODE) {
                                Math.random() > 0.7  // 30% chance of detection in debug mode
                            } else {
                                // In real implementation, process the downloaded image here
                                // 1. Download latest image from camera
                                // 2. Use PaperDetector to detect A4 papers
                                // 3. Return true if papers detected
                                
                                // Simulate successful paper detection
                                // This would be replaced with actual image analysis code
                                Math.random() > 0.7
                            }
                            
                            if (detected) {
                                updateMissionPhase(MissionPhase.APPROACHING_TARGET, "A4 paper detected, approaching")
                                approachAndScanBarcode()
                            }
                            
                            callback(detected)
                        },
                        { error ->
                            Log.e(TAG, "Error in image analysis: ${error.message}")
                            callback(false)
                        }
                    )
            )
        }
    }
    
    private fun approachAndScanBarcode() {
        updateMissionPhase(MissionPhase.APPROACHING_TARGET, "Moving closer to target")
        
        val aircraft = DJIApplication.getAircraftInstance() ?: run {
            updateMissionPhase(MissionPhase.ERROR, "Aircraft not connected")
            returnToHomeAndLand()
            return
        }
        
        // Move closer to the detected paper (simulate approach)
        // In a real implementation, we would calculate the exact approach vector
        disposables.add(
            Observable.timer(3, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        updateMissionPhase(MissionPhase.SCANNING_BARCODE, "Scanning barcode")
                        
                        // Take a photo for barcode scanning
                        val camera = DJIApplication.getCameraInstance()
                        if (camera != null) {
                            ImageUtils.takePhoto(camera) { error ->
                                if (error != null) {
                                    Log.e(TAG, "Failed to take barcode photo: ${error.description}")
                                    searchAndScanPapers()  // Continue searching
                                    return@takePhoto
                                }
                                
                                // Simulate barcode processing
                                // In real implementation, download and process the image here
                                simulateBarcodeProcessing()
                            }
                        } else {
                            updateMissionPhase(MissionPhase.ERROR, "Camera not available")
                            searchAndScanPapers()  // Continue searching
                        }
                    },
                    { error ->
                        Log.e(TAG, "Error approaching target: ${error.message}")
                        searchAndScanPapers()  // Continue searching
                    }
                )
        )
    }
    
    private fun simulateBarcodeProcessing() {
        // For demonstration only - in real implementation, we would:
        // 1. Download the image from the camera
        // 2. Use PaperDetector to isolate the A4 paper regions
        // 3. Use BarcodeProcessor to scan for barcodes in those regions
        
        // Simulate barcode found (would be real barcode scanning in production)
        val barcodeFound = Math.random() > 0.3  // 70% chance of finding a barcode
        
        if (barcodeFound) {
            val currentLocation = DJIApplication.getAircraftInstance()?.flightController?.state?.aircraftLocation
            
            if (currentLocation != null) {
                // Create a simulated barcode result
                val barcode = ScannedBarcode(
                    barcodeValue = "PRODUCT-${(1000..9999).random()}",
                    barcodeFormat = "CODE_128",
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    altitude = currentLocation.altitude,
                    timestamp = System.currentTimeMillis(),
                    imagePath = "simulated_path.jpg"  // In real app, this would be the actual saved image path
                )
                
                // Record the position as visited
                visitedPositions.add(Pair(currentLocation.latitude, currentLocation.longitude))
                
                // Report the scanned barcode
                missionCallback?.onBarcodeScanned(barcode)
                missionCallback?.onMissionProgress(
                    barcodeProcessor.getScannedBarcodes().size + 1,
                    barcodeProcessor.getScannedBarcodes().size + 1
                )
                
                updateMissionPhase(MissionPhase.SEARCHING, "Barcode scanned, continuing search")
                
                // Continue searching for more papers
                searchAndScanPapers()
            } else {
                updateMissionPhase(MissionPhase.ERROR, "Cannot determine current location")
                searchAndScanPapers()
            }
        } else {
            updateMissionPhase(MissionPhase.SEARCHING, "No barcode found, continuing search")
            searchAndScanPapers()
        }
    }
    
    private fun moveToLocation(latitude: Double, longitude: Double, altitude: Double, callback: (Boolean) -> Unit) {
        val aircraft = DJIApplication.getAircraftInstance() ?: run {
            callback(false)
            return
        }
        
        val flightController = aircraft.flightController ?: run {
            callback(false)
            return
        }
        
        updateMissionPhase(currentMissionPhase, "Moving to new location")
        
        // Set the target position
        val targetPosition = LocationCoordinate3D(latitude, longitude, altitude)
        currentTargetPosition = targetPosition
        
        // Move to target position
        flightController.setMissionTargetPoint(targetPosition) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to set target position: ${error.description}")
                callback(false)
                return@setMissionTargetPoint
            }
            
            // Monitor position until we reach the target
            val positionDisposable = Observable.interval(1, TimeUnit.SECONDS)
                .take(30)  // Max 30 seconds to reach the target
                .takeWhile { isExecuting.get() && !isPaused.get() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        val currentLocation = flightController.state.aircraftLocation
                        if (currentLocation != null) {
                            // Calculate distance to target
                            val distance = LocationUtils.calculateDistance(
                                currentLocation.latitude,
                                currentLocation.longitude,
                                latitude,
                                longitude
                            )
                            
                            // If we're close enough to target, we're done
                            if (distance < 2.0) {  // Within 2 meters
                                callback(true)
                                dispose()
                            }
                        }
                    },
                    { error ->
                        Log.e(TAG, "Error monitoring position: ${error.message}")
                        callback(false)
                    },
                    {
                        // Complete called (timeout)
                        callback(false)
                    }
                )
            
            disposables.add(positionDisposable)
        }
    }
    
    private fun returnToHomeAndLand() {
        updateMissionPhase(MissionPhase.RETURNING_HOME, "Returning to home point")
        
        val aircraft = DJIApplication.getAircraftInstance() ?: run {
            finalizeAndReportMission()
            return
        }
        
        val flightController = aircraft.flightController ?: run {
            finalizeAndReportMission()
            return
        }
        
        // Start returning to home
        flightController.startGoHome { error ->
            if (error != null) {
                Log.e(TAG, "Failed to start go home: ${error.description}")
                // Try to land anyway as a fallback
                landDrone()
                return@startGoHome
            }
            
            // Monitor the return to home process
            val rthDisposable = Observable.interval(2, TimeUnit.SECONDS)
                .takeWhile { isExecuting.get() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        val state = flightController.state
                        val isReturningToHome = state.isGoingHome
                        
                        // If no longer returning home, it means we've either arrived or were interrupted
                        if (!isReturningToHome) {
                            // Check if we need to land
                            if (state.isFlying) {
                                landDrone()
                            } else {
                                updateMissionPhase(MissionPhase.COMPLETED, "Mission completed")
                                finalizeAndReportMission()
                            }
                            dispose()
                        }
                    },
                    { error ->
                        Log.e(TAG, "Error monitoring return to home: ${error.message}")
                        landDrone()
                    }
                )
            
            disposables.add(rthDisposable)
        }
    }
    
    private fun landDrone() {
        updateMissionPhase(MissionPhase.LANDING, "Landing the drone")
        
        val aircraft = DJIApplication.getAircraftInstance() ?: run {
            finalizeAndReportMission()
            return
        }
        
        val flightController = aircraft.flightController ?: run {
            finalizeAndReportMission()
            return
        }
        
        // Start landing
        flightController.startLanding { error ->
            if (error != null) {
                Log.e(TAG, "Failed to start landing: ${error.description}")
                updateMissionPhase(MissionPhase.ERROR, "Landing failed: ${error.description}")
                finalizeAndReportMission()
                return@startLanding
            }
            
            // Monitor landing process
            val landingDisposable = Observable.interval(1, TimeUnit.SECONDS)
                .takeWhile { isExecuting.get() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        val state = flightController.state
                        
                        // If no longer flying, landing is complete
                        if (!state.isFlying) {
                            updateMissionPhase(MissionPhase.COMPLETED, "Mission completed successfully")
                            finalizeAndReportMission()
                            dispose()
                        }
                    },
                    { error ->
                        Log.e(TAG, "Error monitoring landing: ${error.message}")
                        updateMissionPhase(MissionPhase.ERROR, "Landing monitoring error: ${error.message}")
                        finalizeAndReportMission()
                    }
                )
            
            disposables.add(landingDisposable)
        }
    }
    
    private fun finalizeAndReportMission() {
        isExecuting.set(false)
        
        // Clean up resources
        disposables.clear()
        
        // Report mission results
        val scannedBarcodes = barcodeProcessor.getScannedBarcodes()
        missionCallback?.onMissionCompleted(scannedBarcodes.size)
    }
    
    private fun updateMissionPhase(phase: MissionPhase, message: String) {
        currentMissionPhase = phase
        missionCallback?.onMissionStatusUpdate(phase, message)
        Log.i(TAG, "Mission phase: $phase - $message")
    }
    
    fun dispose() {
        if (isExecuting.get()) {
            stopMission()
        }
        
        disposables.clear()
        droneConnection.dispose()
    }
}
