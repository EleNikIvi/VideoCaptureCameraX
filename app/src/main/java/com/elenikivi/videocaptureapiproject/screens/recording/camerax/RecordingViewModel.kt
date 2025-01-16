@file:OptIn(ExperimentalPermissionsApi::class)

package com.elenikivi.videocaptureapiproject.screens.recording.camerax

import android.net.Uri
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elenikivi.videocaptureapiproject.R
import com.elenikivi.videocaptureapiproject.ScreenDestinations
import com.elenikivi.videocaptureapiproject.shared.composables.PermissionsHandler
import com.elenikivi.videocaptureapiproject.shared.utils.FileManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val fileManager: FileManager,
    val permissionsHandler: PermissionsHandler
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val _effect = MutableSharedFlow<Effect>()
    val effect: SharedFlow<Effect> = _effect

    init {
        permissionsHandler
            .state
            .onEach { handlerState ->
                _state.update { it.copy(multiplePermissionsState = handlerState.multiplePermissionsState) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.FlashTapped -> onFlashTapped()
            Event.FlipTapped -> onFlipTapped()

            Event.RecordTapped -> onRecordTapped()
            Event.PauseTapped -> onPauseTapped()
            Event.ResumeTapped -> onResumeTapped()
            Event.StopTapped -> onStopTapped()

            is Event.CameraInitialized -> onCameraInitialized(event.cameraLensInfo)
            is Event.OnProgress -> onProgress(event.progress)
            is Event.RecordingPaused -> onPaused()
            is Event.RecordingEnded -> onRecordingFinished(event.outputUri)
            is Event.Error -> onError()

            Event.PermissionRequired -> onPermissionRequired()
        }
    }

    private fun onFlashTapped() {
        _state.update {
            when (_state.value.torchState) {
                TorchState.OFF -> it.copy(torchState = TorchState.ON)
                TorchState.ON -> it.copy(torchState = TorchState.OFF)
                else -> it.copy(torchState = TorchState.OFF)
            }
        }
    }

    private fun onFlipTapped() {
        val lens = if (_state.value.lens == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        //Check if the lens has flash unit
        val flashMode = if (_state.value.lensInfo[lens]?.hasFlashUnit() == true) {
            _state.value.flashMode
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        if (_state.value.lensInfo[lens] != null) {
            _state.update { it.copy(lens = lens, flashMode = flashMode) }
        }
    }

    private fun onPermissionRequired() {
        permissionsHandler.onEvent(PermissionsHandler.Event.PermissionRequired)
    }

    private fun onPauseTapped() {
        viewModelScope.launch {
            _effect.emit(Effect.PauseRecording)
        }
    }

    private fun onResumeTapped() {
        viewModelScope.launch {
            _effect.emit(Effect.ResumeRecording)
        }
    }

    private fun onStopTapped() {
        viewModelScope.launch {
            _effect.emit(Effect.StopRecording)
        }
    }

    private fun onRecordTapped() {
        viewModelScope.launch {
            try {
                val filePath = fileManager.createFile("mp4")
                _effect.emit(Effect.RecordVideo(filePath))
            } catch (exception: IllegalArgumentException) {
                _effect.emit(Effect.ShowMessage())
            }
        }
    }

    private fun onRecordingFinished(uri: Uri) {
        viewModelScope.launch {
            _effect.emit(Effect.NavigateTo(ScreenDestinations.Playback.createRoute(uri.encodedPath!!)))
        }
        _state.update { it.copy(recordingStatus = RecordingStatus.Idle, recordedLength = 0) }
    }

    private fun onError() {
        _state.update { it.copy(recordedLength = 0, recordingStatus = RecordingStatus.Idle) }
        viewModelScope.launch {
            _effect.emit(Effect.ShowMessage())
        }
    }

    private fun onPaused() {
        _state.update { it.copy(recordingStatus = RecordingStatus.Paused) }
    }

    private fun onProgress(progress: Int) {
        _state.update {
            it.copy(
                recordedLength = progress,
                recordingStatus = RecordingStatus.InProgress
            )
        }
    }

    private fun onCameraInitialized(cameraLensInfo: HashMap<Int, CameraInfo>) {
        if (cameraLensInfo.isNotEmpty()) {
            val defaultLens = if (cameraLensInfo[CameraSelector.LENS_FACING_BACK] != null) {
                CameraSelector.LENS_FACING_BACK
            } else if (cameraLensInfo[CameraSelector.LENS_FACING_BACK] != null) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                null
            }
            _state.update {
                it.copy(
                    lens = it.lens ?: defaultLens,
                    lensInfo = cameraLensInfo
                )
            }
        }
    }

    data class State(
        val lens: Int? = null,
        @TorchState.State val torchState: Int = TorchState.OFF,
        @ImageCapture.FlashMode val flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
        val multiplePermissionsState: MultiplePermissionsState? = null,
        val lensInfo: MutableMap<Int, CameraInfo> = mutableMapOf(),
        val recordedLength: Int = 0,
        val recordingStatus: RecordingStatus = RecordingStatus.Idle,
        val permissionRequestInFlight: Boolean = false,
        val hasCameraPermission: Boolean = false,
        val permissionState: PermissionState? = null,
        val permissionAction: PermissionsHandler.Action = PermissionsHandler.Action.NO_ACTION
    )

    sealed class Event {
        data class CameraInitialized(val cameraLensInfo: HashMap<Int, CameraInfo>) :
            Event()

        data class OnProgress(val progress: Int) : Event()
        data object RecordingPaused : Event()
        data class RecordingEnded(val outputUri: Uri) : Event()
        data class Error(val throwable: Throwable?) : Event()

        data object FlashTapped : Event()
        data object FlipTapped : Event()

        data object RecordTapped : Event()
        data object PauseTapped : Event()
        data object ResumeTapped : Event()
        data object StopTapped : Event()
        data object PermissionRequired : Event()

    }

    sealed class Effect {

        data class ShowMessage(val message: Int = R.string.something_went_wrong) : Effect()
        data class RecordVideo(val filePath: String) : Effect()
        data class NavigateTo(val route: String) : Effect()

        data object PauseRecording : Effect()
        data object ResumeRecording : Effect()
        data object StopRecording : Effect()
    }

    sealed class RecordingStatus {
        data object Idle : RecordingStatus()
        data object InProgress : RecordingStatus()
        data object Paused : RecordingStatus()
    }
}