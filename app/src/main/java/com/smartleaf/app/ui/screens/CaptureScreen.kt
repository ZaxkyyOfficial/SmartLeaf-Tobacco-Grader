package com.smartleaf.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartleaf.app.ml.vision.BlackMatteValidatorAnalyzer
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.theme.VibrantGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "CaptureScreen"

@Composable
fun CaptureScreen(
    viewModel: MainViewModel,
    onCaptureSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // ── Permission handling ──────────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required.", color = Color.White)
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    // ── State ────────────────────────────────────────────────────────────
    var isTorchEnabled by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var isSurfaceValid by remember { mutableStateOf(false) }
    var isCountingDown by remember { mutableStateOf(false) }

    val processingState by viewModel.processingState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.resetState()
    }

    LaunchedEffect(processingState) {
        if (processingState is com.smartleaf.app.ui.ProcessingState.Processing) {
            onCaptureSuccess()
        } else if (processingState is com.smartleaf.app.ui.ProcessingState.Error) {
            snackbarHostState.showSnackbar(
                message = (processingState as com.smartleaf.app.ui.ProcessingState.Error).message,
                duration = SnackbarDuration.Short
            )
            viewModel.resetState()
        }
    }

    // ── Gallery launcher ─────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source)
            // Ensure ARGB_8888 for tfLite
            val softBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            viewModel.processImage(softBitmap, uri.toString())
        }
    }

    // ── CameraX setup with ProcessCameraProvider ─────────────────────────
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Camera reference for torch control
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Unbind any prior use-cases to prevent conflicts
            cameraProvider.unbindAll()

            // 1. Preview use-case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 2. ImageAnalysis use-case with BlackMatteValidatorAnalyzer
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(
                        analysisExecutor,
                        BlackMatteValidatorAnalyzer { isValid ->
                            isSurfaceValid = isValid
                        }
                    )
                }

            // 3. Camera selector (back camera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Bind all 3 use-cases to lifecycle
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bindToLifecycle failed", e)
            }
        }, mainExecutor)

        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }

    // ── Capture logic (extracted for readability) ────────────────────────
    fun performCapture() {
        val executor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    image.close()

                    val contentValues = ContentValues().apply {
                        put(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            "SmartLeaf_${System.currentTimeMillis()}.jpg"
                        )
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/SmartLeaf"
                        )
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                        }
                    }

                    viewModel.processImage(rotatedBitmap, uri?.toString())
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                }
            }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // Camera Preview (bound to ProcessCameraProvider)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )

        // Overlay Box logic
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Draw a darkened overlay with a clear center cutout for the leaf
            val boxWidth = canvasWidth * 0.75f
            val boxHeight = canvasHeight * 0.55f
            val left = (canvasWidth - boxWidth) / 2f
            val top = (canvasHeight - boxHeight) / 2f

            // Draw darkened background
            drawRect(color = Color.Black.copy(alpha = 0.6f))

            // Clear out center area
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(16.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Draw bounding box border — green when surface valid, red when invalid
            val borderColor = if (isSurfaceValid) VibrantGreen else Color(0xFFEF5350)
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Center crosshair
            val crosshairSize = 24.dp.toPx()
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f

            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(x = (centerX - (crosshairSize / 2)), y = centerY),
                end = Offset(x = (centerX + (crosshairSize / 2)), y = centerY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(x = centerX, y = (centerY - (crosshairSize / 2))),
                end = Offset(x = centerX, y = (centerY + (crosshairSize / 2))),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                "Scanner",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { /* Menu */ }) {
                Text("•••", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Surface validation status pill
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isSurfaceValid) VibrantGreen else Color(0xFFEF5350),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSurfaceValid) "Surface: Valid" else "Surface: Place on Black Matte",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // Live stats card overlay
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-160).dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = Color(0xFFFFCA28),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "PSEUDOMATURE",
                                color = Color(0xFFFFCA28),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "High Grade",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Est. Harvest: 3 Days",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "92%",
                            color = VibrantGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("CONFIDENCE", color = Color.Gray, fontSize = 8.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("MOISTURE", color = Color.Gray, fontSize = 8.sp)
                            Text(
                                "14.2%",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("CHLOROPHYLL", color = Color.Gray, fontSize = 8.sp)
                            Text(
                                "Low-Mid",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery button
            IconButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.background(Color.DarkGray, CircleShape)
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = Color.White
                )
            }

            // Capture Button — enabled only when surface is valid & not counting down
            Box(
                modifier = Modifier.size(80.dp)
            ) {
                val captureButtonAlpha by animateFloatAsState(
                    targetValue = if (isSurfaceValid && !isCountingDown) 1f else 0.4f,
                    animationSpec = tween(300),
                    label = "captureAlpha"
                )

                Button(
                    onClick = {
                        if (!isSurfaceValid || isCountingDown) return@Button
                        isCountingDown = true
                        coroutineScope.launch {
                            // Countdown: 3 → 2 → 1
                            for (i in 3 downTo 1) {
                                countdown = i
                                delay(1000L)
                            }
                            countdown = 0
                            // Execute capture
                            performCapture()
                            isCountingDown = false
                        }
                    },
                    enabled = isSurfaceValid && !isCountingDown,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = captureButtonAlpha),
                        disabledContainerColor = Color.White.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White, CircleShape)
                            .border(
                                2.dp,
                                if (isSurfaceValid) VibrantGreen else Color.LightGray,
                                CircleShape
                            )
                    )
                }
            }

            // Flash/Torch button
            IconButton(
                onClick = {
                    isTorchEnabled = !isTorchEnabled
                    camera?.cameraControl?.enableTorch(isTorchEnabled)
                },
                modifier = Modifier.background(Color.DarkGray, CircleShape)
            ) {
                Icon(
                    imageVector = if (isTorchEnabled) Icons.Default.FlashOff else Icons.Default.FlashOn,
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }
        }

        // ── Countdown Overlay ────────────────────────────────────────────
        AnimatedVisibility(
            visible = countdown > 0,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(150)) + scaleOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdown.toString(),
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Processing Overlay
        if (processingState is com.smartleaf.app.ui.ProcessingState.Processing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = VibrantGreen,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Analyzing Image...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFFEF5350), // Red
                contentColor = Color.White
            )
        }
    }
}
