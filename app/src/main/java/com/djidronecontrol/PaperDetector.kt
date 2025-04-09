package com.djidronecontrol

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.ArrayList

class PaperDetector {
    private val TAG = "PaperDetector"
    
    init {
        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed")
        } else {
            Log.i(TAG, "OpenCV initialization successful")
        }
    }
    
    data class DetectionResult(
        val detected: Boolean,
        val rect: Rect? = null,
        val confidence: Double = 0.0
    )
    
    /**
     * Detects A4 paper in portrait orientation within the image
     * @param bitmap Input image
     * @return List of detection results with paper locations
     */
    fun detectA4Papers(bitmap: Bitmap): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        
        try {
            // Convert bitmap to OpenCV Mat
            val rgbaMat = Mat()
            Utils.bitmapToMat(bitmap, rgbaMat)
            
            // Convert to grayscale
            val grayMat = Mat()
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_BGR2GRAY)
            
            // Apply Gaussian blur to reduce noise
            val blurredMat = Mat()
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)
            
            // Apply adaptive threshold
            val thresholdMat = Mat()
            Imgproc.adaptiveThreshold(
                blurredMat, 
                thresholdMat, 
                255.0, 
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, 
                Imgproc.THRESH_BINARY_INV, 
                11, 
                2.0
            )
            
            // Dilate the image to connect components
            val dilatedMat = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(thresholdMat, dilatedMat, kernel)
            
            // Find contours
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                dilatedMat.clone(), 
                contours, 
                hierarchy, 
                Imgproc.RETR_EXTERNAL, 
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            
            // Filter and process contours to find rectangles similar to A4 paper
            for (contour in contours) {
                val contourArea = Imgproc.contourArea(contour)
                
                // Skip small contours
                if (contourArea < 5000) continue
                
                // Approximate the contour to a polygon
                val contour2f = MatOfPoint2f(*contour.toArray())
                val approxCurve = MatOfPoint2f()
                val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
                Imgproc.approxPolyDP(contour2f, approxCurve, epsilon, true)
                
                // Check if the polygon has 4 points (rectangle)
                if (approxCurve.total().toInt() == 4) {
                    val boundingRect = Imgproc.boundingRect(contour)
                    
                    // A4 paper ratio is approximately 1:1.414 (portrait)
                    val aspectRatio = boundingRect.width.toDouble() / boundingRect.height.toDouble()
                    val a4RatioPortrait = 1.0 / 1.414
                    
                    // Allow for some margin of error in the aspect ratio
                    if (Math.abs(aspectRatio - a4RatioPortrait) < 0.15) {
                        // Calculate confidence based on aspect ratio match and contour area
                        val aspectRatioConfidence = 1.0 - Math.abs(aspectRatio - a4RatioPortrait) / a4RatioPortrait
                        val areaConfidence = contourArea / (rgbaMat.width() * rgbaMat.height())
                        val confidence = (aspectRatioConfidence * 0.7) + (areaConfidence * 0.3)
                        
                        // Draw contour for debugging (can be removed in production)
                        Imgproc.drawContours(
                            rgbaMat, 
                            listOf(contour), 
                            0, 
                            Scalar(0.0, 255.0, 0.0, 255.0), 
                            2
                        )
                        
                        // Add to results if confidence is reasonable
                        if (confidence > 0.6) {
                            results.add(DetectionResult(true, boundingRect, confidence))
                        }
                    }
                }
            }
            
            // Clean up
            rgbaMat.release()
            grayMat.release()
            blurredMat.release()
            thresholdMat.release()
            dilatedMat.release()
            hierarchy.release()
            
            for (contour in contours) {
                contour.release()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in paper detection: ${e.message}")
        }
        
        return results
    }
    
    /**
     * Extracts a region from the bitmap based on a detection result
     * @param bitmap Original bitmap
     * @param detectionResult Result containing the rect to extract
     * @return Cropped bitmap of the detected paper area or null if not possible
     */
    fun extractPaperRegion(bitmap: Bitmap, detectionResult: DetectionResult): Bitmap? {
        if (!detectionResult.detected || detectionResult.rect == null) {
            return null
        }
        
        val rect = detectionResult.rect
        
        // Ensure rect is within bounds
        val x = Math.max(0, rect.x)
        val y = Math.max(0, rect.y)
        val width = Math.min(rect.width, bitmap.width - x)
        val height = Math.min(rect.height, bitmap.height - y)
        
        if (width <= 0 || height <= 0) {
            return null
        }
        
        try {
            return Bitmap.createBitmap(bitmap, x, y, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting paper region: ${e.message}")
            return null
        }
    }
}
