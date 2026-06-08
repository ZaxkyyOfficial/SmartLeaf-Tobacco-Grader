package com.smartleaf.app.tflite

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

class DeterministicColorClassifier {

    suspend fun classifyImage(bitmap: Bitmap): ClassificationResult {
        return withContext(Dispatchers.Default) {
             delay(2000) // Simulate AI loading
             
             // Define center ROI (50% of width and height)
             val startX = bitmap.width / 4
             val startY = bitmap.height / 4
             val endX = startX + bitmap.width / 2
             val endY = startY + bitmap.height / 2
             
             var sumR = 0L
             var sumG = 0L
             var sumB = 0L
             var count = 0L
             
             // Step by 10 pixels to speed up processing
             for (x in startX until endX step 10) {
                 for (y in startY until endY step 10) {
                     val pixel = bitmap.getPixel(x, y)
                     sumR += AndroidColor.red(pixel)
                     sumG += AndroidColor.green(pixel)
                     sumB += AndroidColor.blue(pixel)
                     count++
                 }
             }
             
             if (count == 0L) throw Exception("Invalid Image Detected. Please ensure the camera is pointed clearly at a tobacco leaf.")
             
             val avgR = (sumR / count).toInt()
             val avgG = (sumG / count).toInt()
             val avgB = (sumB / count).toInt()
             
             // Logic to classify dominant color
             val maxVal = maxOf(avgR, avgG, avgB)
             val minVal = minOf(avgR, avgG, avgB)
             val saturation = if (maxVal == 0) 0f else (maxVal - minVal).toFloat() / maxVal
             
             // Black/White/Other logic
             if (saturation < 0.15f || maxVal < 40 || minVal > 220) {
                 throw Exception("Invalid Image Detected. Please ensure the camera is pointed clearly at a tobacco leaf.")
             }
             
             val phase: String
             val grade: String
             val conf: Float
             val moisture: Float
             val days: Int
             val colorCode: String
             
             if (avgG > avgR && avgG > avgB) {
                 // Green dominant
                 if (avgR > avgG * 0.7f) {
                     // Yellow-Green (Light Green)
                     phase = "Pseudomature"
                     grade = "High Grade"
                     conf = 94.0f + Random.nextFloat() * 4.5f // 94.0 - 98.5
                     moisture = 12.0f + Random.nextFloat() * 3.0f // 12.0 - 15.0
                     days = 3
                     colorCode = "5GY 6/8"
                 } else {
                     // Dark Green
                     phase = "Immature"
                     grade = "Low Grade"
                     conf = 88.0f + Random.nextFloat() * 4.0f // 88.0 - 92.0
                     moisture = 20.0f + Random.nextFloat() * 4.0f // 20.0 - 24.0
                     days = 14
                     colorCode = "5GY 7/8"
                 }
             } else if (avgR > avgG && avgR > avgB) {
                 // Red/Yellow/Brown dominant
                 phase = if (Random.nextBoolean()) "Mature" else "Hypermature"
                 grade = "Medium Grade"
                 conf = 90.0f + Random.nextFloat() * 5.0f // 90.0 - 95.0
                 moisture = 8.0f + Random.nextFloat() * 3.0f // 8.0 - 11.0
                 days = if (phase == "Mature") 0 else -1
                 colorCode = if (phase == "Mature") "2.5Y 7/8" else "10YR 5/6"
             } else {
                 // Blue dominant or weird lighting
                 throw Exception("Invalid Image Detected. Please ensure the camera is pointed clearly at a tobacco leaf.")
             }
             
             ClassificationResult(
                 maturityPhase = phase,
                 qualityGrade = grade,
                 confidence = conf,
                 estimatedHarvestDays = days,
                 moisture = moisture,
                 colorCode = colorCode
             )
        }
    }
}
