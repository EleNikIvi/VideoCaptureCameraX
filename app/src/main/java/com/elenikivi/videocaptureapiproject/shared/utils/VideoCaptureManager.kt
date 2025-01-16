package com.elenikivi.videocaptureapiproject.shared.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.net.Uri
import android.util.Range
import android.util.Size
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.compositionLocalOf
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.elenikivi.videocaptureapiproject.shared.PreviewState
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class VideoCaptureManager private constructor(private val builder: Builder) :
    LifecycleEventObserver {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var videoCapture: VideoCapture<Recorder>

    private lateinit var activeRecording: Recording

    var listener: Listener? = null

    init {
        getLifecycle().addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                cameraProviderFuture = ProcessCameraProvider.getInstance(getContext())
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    queryCameraInfo(source, cameraProvider)
                }, ContextCompat.getMainExecutor(getContext()))
            }

            else -> Unit
        }
    }

    /**
     * Queries the capabilities of the FRONT and BACK camera lens
     * The result is stored in an array map.
     *
     * With this, we can determine if a camera lens is available or not,
     * and what capabilities the lens can support e.g flash support
     */
    private fun queryCameraInfo(
        lifecycleOwner: LifecycleOwner,
        cameraProvider: ProcessCameraProvider
    ) {
        val cameraLensInfo = HashMap<Int, CameraInfo>()
        arrayOf(CameraSelector.LENS_FACING_BACK, CameraSelector.LENS_FACING_FRONT).forEach { lens ->
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lens).build()
            if (cameraProvider.hasCamera(cameraSelector)) {
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector)
                if (lens == CameraSelector.LENS_FACING_BACK) {
                    cameraLensInfo[CameraSelector.LENS_FACING_BACK] = camera.cameraInfo
                } else if (lens == CameraSelector.LENS_FACING_FRONT) {
                    cameraLensInfo[CameraSelector.LENS_FACING_FRONT] = camera.cameraInfo
                }
            }
        }
        listener?.onInitialised(cameraLensInfo)
    }

    /**
     * Takes a [previewState] argument to determine the camera options
     *
     * Create a Preview.
     * Create Video Capture use case
     * Bind the selected camera and any use cases to the lifecycle.
     * Connect the Preview to the PreviewView.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun showPreview(
        previewState: PreviewState,
        cameraPreview: PreviewView = getCameraPreview(),
    ): View {
        var currentAspectRatio = AspectRatio.RATIO_16_9

        getLifeCycleOwner().lifecycleScope.launchWhenResumed {
            val cameraProvider = cameraProviderFuture.await()
            cameraProvider.unbindAll()

            //Select a camera lens
            val cameraSelector: CameraSelector = CameraSelector.Builder()
                .requireLensFacing(previewState.cameraLens)
                .addCameraFilter {
                    it.filter { camInfo ->
                        val level = Camera2CameraInfo.from(camInfo)
                            .getCameraCharacteristic(
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
                            )
                        level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
                    }
                }
                .build()

            val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)
            val isPreviewStabilizationSupported =
                Preview.getPreviewCapabilities(cameraInfo)
                    .isStabilizationSupported

            //Create Preview use case
            val preview: Preview = Preview.Builder()
                .apply {
                    if (isPreviewStabilizationSupported) {
                        setPreviewStabilizationEnabled(true)
                    }
                }
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1080, 1920),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .setAspectRatioStrategy(
                            if (currentAspectRatio == AspectRatio.RATIO_16_9) {
                            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                            } else {
                                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                            }
                        )
                        .build()
                )
                .build()
                .apply { surfaceProvider = cameraPreview.surfaceProvider }


            val isStabilizationSupported = Recorder.getVideoCapabilities(cameraInfo)
                .isStabilizationSupported
            //Create Video Capture use case
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.UHD,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    ))
                .build()
            videoCapture = VideoCapture.Builder(recorder)
                .apply {
                    if (isStabilizationSupported) {
                        setVideoStabilizationEnabled(true)
                    }
                }
                .build()


            /*val builder = ImageAnalysis.Builder()
            val ext: Camera2Interop.Extender<*> = Camera2Interop.Extender(builder)
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range<Int>(60, 60)
            )
            val imageAnalysis = builder.build()*/

            cameraProvider.bindToLifecycle(
                getLifeCycleOwner(),
                cameraSelector,
                preview,
                videoCapture,
                //imageAnalysis
            ).apply {
                cameraControl.enableTorch(previewState.torchState == TorchState.ON)
            }
        }
        return cameraPreview
    }

    fun updatePreview(previewState: PreviewState, previewView: View) {
        showPreview(previewState, previewView as PreviewView)
    }

    private fun getCameraPreview() = PreviewView(getContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        keepScreenOn = true
    }

    private fun getLifecycle() = builder.lifecycleOwner?.lifecycle!!

    private fun getContext() = builder.context

    private fun getLifeCycleOwner() = builder.lifecycleOwner!!

    @SuppressLint("MissingPermission")
    fun startRecording(filePath: String) {
        val outputOptions = FileOutputOptions.Builder(File(filePath)).build()
        activeRecording = videoCapture.output
            .prepareRecording(getContext(), outputOptions)
            .apply { withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(getContext()), videoRecordingListener)
    }

    fun pauseRecording() {
        activeRecording.pause()
        listener?.recordingPaused()
    }

    fun resumeRecording() {
        activeRecording.resume()
    }

    fun stopRecording() {
        activeRecording.stop()
    }

    private val videoRecordingListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is VideoRecordEvent.Finalize -> if (event.hasError()) {
                listener?.onError(event.cause)
            } else {
                listener?.recordingCompleted(event.outputResults.outputUri)
            }

            is VideoRecordEvent.Pause -> listener?.recordingPaused()
            is VideoRecordEvent.Status -> {
                listener?.onProgress(event.recordingStats.recordedDurationNanos.fromNanoToSeconds())
            }
        }
    }

    interface Listener {
        fun onInitialised(cameraLensInfo: HashMap<Int, CameraInfo>)
        fun onProgress(progress: Int)
        fun recordingPaused()
        fun recordingCompleted(outputUri: Uri)
        fun onError(throwable: Throwable?)
    }

    class Builder(val context: Context) {

        var lifecycleOwner: LifecycleOwner? = null
            private set

        fun registerLifecycleOwner(source: LifecycleOwner): Builder {
            this.lifecycleOwner = source
            return this
        }

        fun create(): VideoCaptureManager {
            requireNotNull(lifecycleOwner) { "Lifecycle owner is not set" }
            return VideoCaptureManager(this)
        }
    }

    private fun Long.fromNanoToSeconds() = (this / (1000 * 1000 * 1000)).toInt()
}

val LocalVideoCaptureManager =
    compositionLocalOf<VideoCaptureManager> { error("No capture manager found!") }
