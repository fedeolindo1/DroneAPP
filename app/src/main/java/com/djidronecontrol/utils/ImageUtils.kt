package com.djidronecontrol.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import dji.common.error.DJIError
import dji.sdk.camera.Camera
import dji.sdk.media.MediaFile
import dji.sdk.media.MediaManager
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {
    private const val TAG = "ImageUtils"
    
    /**
     * Take a photo using the drone camera
     * @param camera The drone's camera instance
     * @param callback Callback with success or error
     */
    fun takePhoto(camera: Camera?, callback: (DJIError?) -> Unit) {
        if (camera == null) {
            callback(DJIError("Camera is null"))
            return
        }
        
        // Ensure camera is in photo mode
        camera.setMode(Camera.Mode.SHOOT_PHOTO) { error ->
            if (error != null) {
                Log.e(TAG, "Error setting camera mode: ${error.description}")
                callback(error)
                return@setMode
            }
            
            // Set camera to single shot mode
            camera.setShootPhotoMode(Camera.ShootPhotoMode.SINGLE) { modeError ->
                if (modeError != null) {
                    Log.e(TAG, "Error setting photo mode: ${modeError.description}")
                    callback(modeError)
                    return@setShootPhotoMode
                }
                
                // Take the photo
                camera.startShootPhoto { shootError ->
                    Log.i(TAG, "Photo taken, error: ${shootError?.description}")
                    callback(shootError)
                }
            }
        }
    }
    
    /**
     * Download the latest photo from the drone's SD card
     * @param mediaManager The drone's media manager
     * @param callback Callback with the downloaded bitmap or null if failed
     */
    fun downloadLatestPhoto(mediaManager: MediaManager?, callback: (Bitmap?) -> Unit) {
        if (mediaManager == null) {
            Log.e(TAG, "Media manager is null")
            callback(null)
            return
        }
        
        // Refresh the file list
        mediaManager.refreshFileList { error ->
            if (error != null) {
                Log.e(TAG, "Error refreshing file list: ${error.description}")
                callback(null)
                return@refreshFileList
            }
            
            val mediaFileList = mediaManager.sdCardFileListSnapshot
            if (mediaFileList.isEmpty()) {
                Log.e(TAG, "Media file list is empty")
                callback(null)
                return@refreshFileList
            }
            
            // Get the latest photo (first in the list is the most recent)
            val latestPhoto = mediaFileList.firstOrNull { it.mediaType == MediaFile.MediaType.JPEG }
            if (latestPhoto == null) {
                Log.e(TAG, "No photos found")
                callback(null)
                return@refreshFileList
            }
            
            // Create a directory to save photos
            val storageDir = File(com.djidronecontrol.DJIApplication.getContext().getExternalFilesDir(null), "Photos")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            
            val photoFile = File(storageDir, "photo_${UUID.randomUUID()}.jpg")
            
            // Download the full size photo
            mediaManager.fetchMediaData(latestPhoto, photoFile.absolutePath) { _, error ->
                if (error != null) {
                    Log.e(TAG, "Error downloading photo: ${error.description}")
                    callback(null)
                    return@fetchMediaData
                }
                
                // Load the bitmap from the saved file
                try {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    callback(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading bitmap: ${e.message}")
                    callback(null)
                }
            }
        }
    }
    
    /**
     * Save a bitmap to a file
     * @param bitmap The bitmap to save
     * @param directory Directory to save the file in
     * @param fileName Optional file name (random UUID will be used if not provided)
     * @return The absolute path to the saved file, or null if failed
     */
    fun saveBitmap(bitmap: Bitmap, directory: File, fileName: String? = null): String? {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        val file = File(directory, fileName ?: "image_${UUID.randomUUID()}.jpg")
        
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap: ${e.message}")
            null
        }
    }
    
    /**
     * Rotate a bitmap by the specified angle
     * @param bitmap The bitmap to rotate
     * @param angle The angle in degrees
     * @return The rotated bitmap
     */
    fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Resize a bitmap to the specified width and height
     * @param bitmap The bitmap to resize
     * @param width Target width
     * @param height Target height
     * @return The resized bitmap
     */
    fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
    
    /**
     * Crop a bitmap to a specific region
     * @param bitmap The bitmap to crop
     * @param x Starting X coordinate
     * @param y Starting Y coordinate
     * @param width Width of the crop area
     * @param height Height of the crop area
     * @return The cropped bitmap
     */
    fun cropBitmap(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }
}
