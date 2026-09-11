package com.example

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.core.util.Consumer

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    mode: String, // "photo" or "video"
    onMediaCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val permissions = if (mode == "video") {
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    } else {
        listOf(Manifest.permission.CAMERA)
    }
    
    val permissionState = rememberMultiplePermissionsState(permissions = permissions)

    if (permissionState.allPermissionsGranted) {
        CameraPreviewContent(mode, onMediaCaptured, onClose)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Izin Kamera diperlukan untuk mengambil $mode.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Berikan Izin")
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    mode: String,
    onMediaCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var recording: Recording? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()

                        if (mode == "photo") {
                            imageCapture = ImageCapture.Builder().build()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } else {
                            val recorder = Recorder.Builder()
                                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                                .build()
                            videoCapture = VideoCapture.withOutput(recorder)
                            
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                videoCapture
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // UI Controls
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (mode == "photo") {
                FloatingActionButton(
                    onClick = {
                        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LaporanKegiatan")
                            }
                        }

                        val outputOptions = ImageCapture.OutputFileOptions
                            .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            .build()

                        imageCapture?.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    output.savedUri?.let { onMediaCaptured(it) }
                                }
                                override fun onError(e: ImageCaptureException) {
                                    Log.e("CameraScreen", "Photo capture failed: ${e.message}", e)
                                }
                            }
                        )
                    },
                    shape = CircleShape,
                    containerColor = Color.White
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Ambil Foto", tint = Color.Black)
                }
            } else {
                FloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            recording?.stop()
                            recording = null
                        } else {
                            val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LaporanKegiatan")
                                }
                            }
                            
                            val mediaStoreOutputOptions = MediaStoreOutputOptions
                                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                                .setContentValues(contentValues)
                                .build()
                                
                            val activeRecording = videoCapture?.output
                                ?.prepareRecording(context, mediaStoreOutputOptions)
                            
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                activeRecording?.withAudioEnabled()
                            }
                                
                            recording = activeRecording?.start(ContextCompat.getMainExecutor(context)) { event ->
                                when(event) {
                                    is VideoRecordEvent.Start -> {
                                        isRecording = true
                                    }
                                    is VideoRecordEvent.Finalize -> {
                                        if (!event.hasError()) {
                                            isRecording = false
                                            onMediaCaptured(event.outputResults.outputUri)
                                        } else {
                                            recording?.close()
                                            recording = null
                                            isRecording = false
                                            Log.e("CameraScreen", "Video capture failed: ${event.error}")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    shape = CircleShape,
                    containerColor = if (isRecording) Color.Red else Color.White
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Videocam, 
                        contentDescription = if (isRecording) "Berhenti Rekam" else "Rekam Video",
                        tint = if (isRecording) Color.White else Color.Black
                    )
                }
            }
        }
    }
}
