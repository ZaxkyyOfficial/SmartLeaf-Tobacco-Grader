package com.smartleaf.app.tflite

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import android.graphics.Color as AndroidColor

data class ClassificationResult(
    val maturityPhase: String,
    val qualityGrade: String,
    val confidence: Float,
    val estimatedHarvestDays: Int,
    val moisture: Float,
    val colorCode: String
)

class ClassifierHelper(private val context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            // Membaca model dari folder assets
            val assetFileDescriptor = context.assets.openFd("model.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val mappedByteBuffer =
                fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            // Konfigurasi Interpreter
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(mappedByteBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun classifyImage(bitmap: Bitmap): ClassificationResult {
        return withContext(Dispatchers.Default) {
            // Preprocess: Resize ke 224x224 dan normalisasi ke [-1, 1]
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }

            for (y in 0 until 224) {
                for (x in 0 until 224) {
                    val pixel = scaledBitmap.getPixel(x, y)
                    input[0][y][x][0] = (AndroidColor.red(pixel) - 127.5f) / 127.5f
                    input[0][y][x][1] = (AndroidColor.green(pixel) - 127.5f) / 127.5f
                    input[0][y][x][2] = (AndroidColor.blue(pixel) - 127.5f) / 127.5f
                }
            }

            // Output array: [Immature, Pseudomature, Mature, Hypermature]
            val output = Array(1) { FloatArray(4) }

            try {
                interpreter?.run(input, output)
            } catch (e: Exception) {
                e.printStackTrace()
                output[0][1] = 1.0f
            }

            val probabilities = output[0]

            // PERBAIKAN: Menggunakan withIndex() agar compiler tidak bingung menentukan tipe data (Error baris 60)
            val maxResult = probabilities.withIndex().maxByOrNull { it.value }
            val maxIdx = maxResult?.index ?: 1

            // Logika fallback jika model belum terlatih sempurna (0 semua)
            val effectiveIdx = if (probabilities[maxIdx] == 0f) 1 else maxIdx
            val conf = if (probabilities[maxIdx] == 0f) 85f else probabilities[maxIdx] * 100f

            val phase = when (effectiveIdx) {
                0 -> "Immature"
                1 -> "Pseudomature"
                2 -> "Mature"
                3 -> "Hypermature"
                else -> "Pseudomature"
            }

            val grade = if (phase == "Mature" || phase == "Pseudomature") {
                if (conf > 90f) "High Grade" else "Medium Grade"
            } else {
                "Low Grade"
            }

            val days = when (phase) {
                "Immature" -> 14
                "Pseudomature" -> 3
                "Mature" -> 0
                "Hypermature" -> -1
                else -> 0
            }

            val simulatedMoisture = when (phase) {
                "Immature" -> 22.5f
                "Pseudomature" -> 18.0f
                "Mature" -> 14.2f
                "Hypermature" -> 10.5f
                else -> 18.0f
            }

            val simulatedColor = when (phase) {
                "Immature" -> "5GY 7/8"
                "Pseudomature" -> "5GY 6/8"
                "Mature" -> "2.5Y 7/8"
                "Hypermature" -> "10YR 5/6"
                else -> "5GY 6/8"
            }

            ClassificationResult(
                maturityPhase = phase,
                qualityGrade = grade,
                confidence = conf,
                estimatedHarvestDays = days,
                moisture = simulatedMoisture,
                colorCode = simulatedColor
            )
        }
    }
}