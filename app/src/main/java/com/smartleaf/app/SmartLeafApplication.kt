package com.smartleaf.app

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * SmartLeafApplication
 *
 * Custom [Application] class responsible for initializing OpenCV
 * at the earliest possible lifecycle point — before any Activity
 * or Service is created.
 *
 * OpenCV must be initialized exactly once per process. Calling
 * [OpenCVLoader.initDebug] in [onCreate] guarantees that all native
 * libraries (libopencv_java4.so) are loaded before any `Mat` or
 * `Imgproc` operations are attempted elsewhere in the app.
 */
class SmartLeafApplication : Application() {

    companion object {
        private const val TAG = "SmartLeafApplication"

        /**
         * Observable flag that other components can check to verify
         * OpenCV was loaded successfully before using CV operations.
         */
        @Volatile
        var isOpenCvInitialized: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        initializeOpenCv()
    }

    /**
     * Initializes OpenCV via the static initializer.
     *
     * [OpenCVLoader.initDebug] loads the native OpenCV libraries
     * bundled in the APK (via the Gradle dependency). It returns
     * `true` on success. On failure, the app can still run but
     * CV-based preprocessing will be unavailable — the
     * [TobaccoImagePreprocessor] checks [isOpenCvInitialized]
     * and falls back to a Bitmap-only pipeline if needed.
     */
    private fun initializeOpenCv() {
        isOpenCvInitialized = OpenCVLoader.initDebug()
        if (isOpenCvInitialized) {
            Log.i(TAG, "OpenCV ${OpenCVLoader.OPENCV_VERSION} initialized successfully.")
        } else {
            Log.e(TAG, "OpenCV initialization FAILED. Native libraries could not be loaded.")
        }
    }
}
