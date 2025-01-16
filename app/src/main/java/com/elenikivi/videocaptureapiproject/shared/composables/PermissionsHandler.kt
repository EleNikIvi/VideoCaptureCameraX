@file:OptIn(ExperimentalPermissionsApi::class)

package com.elenikivi.videocaptureapiproject.shared.composables

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumTouchTargetEnforcement
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elenikivi.videocaptureapiproject.R
import com.elenikivi.videocaptureapiproject.ui.theme.alertDialogButtonColors
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class PermissionsHandler @Inject constructor() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun onEvent(event: Event) {
        when (event) {
            Event.PermissionDenied -> onPermissionDenied()
            Event.PermissionDismissTapped -> onPermissionDismissTapped()
            Event.PermissionNeverAskAgain -> onPermissionNeverShowAgain()
            Event.PermissionRationaleOkTapped -> onPermissionRationaleOkTapped()
            Event.PermissionRequired -> onPermissionRequired()
            Event.PermissionSettingsTapped -> onPermissionSettingsTapped()
            Event.PermissionsGranted -> onPermissionGranted()
            is Event.PermissionsStateUpdated -> onPermissionsStateUpdated(event.permissionsState)
        }
    }

    private fun onPermissionsStateUpdated(permissionState: MultiplePermissionsState) {
        _state.update { it.copy(multiplePermissionsState = permissionState) }
    }

    private fun onPermissionGranted() {
        _state.update { it.copy(permissionAction = Action.NO_ACTION) }
    }

    private fun onPermissionDenied() {
        _state.update { it.copy(permissionAction = Action.NO_ACTION) }
    }

    private fun onPermissionNeverShowAgain() {
        _state.update {
            it.copy(permissionAction = Action.SHOW_NEVER_ASK_AGAIN)
        }
    }

    private fun onPermissionRequired() {
        _state.value.multiplePermissionsState?.let {
            val permissionAction =
                if (!it.allPermissionsGranted && !it.shouldShowRationale && !it.permissionRequested) {
                    Action.REQUEST_PERMISSION
                } else if (!it.allPermissionsGranted && it.shouldShowRationale) {
                    Action.SHOW_RATIONALE
                } else {
                    Action.SHOW_NEVER_ASK_AGAIN
                }
            _state.update { it.copy(permissionAction = permissionAction) }
        }
    }

    private fun onPermissionRationaleOkTapped() {
        _state.update { it.copy(permissionAction = Action.REQUEST_PERMISSION) }
    }

    private fun onPermissionDismissTapped() {
        _state.update { it.copy(permissionAction = Action.NO_ACTION) }
    }

    private fun onPermissionSettingsTapped() {
        _state.update { it.copy(permissionAction = Action.NO_ACTION) }
    }

    data class State(
        val multiplePermissionsState: MultiplePermissionsState? = null,
        val permissionAction: Action = Action.NO_ACTION
    )

    sealed class Event {
        object PermissionDenied : Event()
        object PermissionsGranted : Event()
        object PermissionSettingsTapped : Event()
        object PermissionNeverAskAgain : Event()
        object PermissionDismissTapped : Event()
        object PermissionRationaleOkTapped : Event()
        object PermissionRequired : Event()

        data class PermissionsStateUpdated(val permissionsState: MultiplePermissionsState) :
            Event()
    }

    enum class Action {
        REQUEST_PERMISSION, SHOW_RATIONALE, SHOW_NEVER_ASK_AGAIN, NO_ACTION
    }
}

@Composable
fun HandlePermissionsRequest(permissions: List<String>, permissionsHandler: PermissionsHandler) {

    val state by permissionsHandler.state.collectAsState()
    val permissionsState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(permissionsState) {
        permissionsHandler.onEvent(PermissionsHandler.Event.PermissionsStateUpdated(permissionsState))
        when {
            permissionsState.allPermissionsGranted -> {
                permissionsHandler.onEvent(PermissionsHandler.Event.PermissionsGranted)
            }

            permissionsState.permissionRequested && !permissionsState.shouldShowRationale -> {
                permissionsHandler.onEvent(PermissionsHandler.Event.PermissionNeverAskAgain)
            }

            else -> {
                permissionsHandler.onEvent(PermissionsHandler.Event.PermissionDenied)
            }
        }
    }

    HandlePermissionAction(
        action = state.permissionAction,
        permissionStates = state.multiplePermissionsState,
        rationaleText = R.string.permission_rationale,
        neverAskAgainText = R.string.permission_rationale,
        onOkTapped = { permissionsHandler.onEvent(PermissionsHandler.Event.PermissionRationaleOkTapped) },
        onSettingsTapped = { permissionsHandler.onEvent(PermissionsHandler.Event.PermissionSettingsTapped) },
    )
}

@Composable
fun HandlePermissionAction(
    action: PermissionsHandler.Action,
    permissionStates: MultiplePermissionsState?,
    @StringRes rationaleText: Int,
    @StringRes neverAskAgainText: Int,
    onOkTapped: () -> Unit,
    onSettingsTapped: () -> Unit,
) {
    val context = LocalContext.current
    when (action) {
        PermissionsHandler.Action.REQUEST_PERMISSION -> {
            LaunchedEffect(true) {
                permissionStates?.launchMultiplePermissionRequest()
            }
        }

        PermissionsHandler.Action.SHOW_RATIONALE -> {
            PermissionRationaleDialog(
                message = stringResource(rationaleText),
                onOkTapped = onOkTapped
            )
        }

        PermissionsHandler.Action.SHOW_NEVER_ASK_AGAIN -> {
            ShowGotoSettingsDialog(
                title = stringResource(R.string.allow_permission),
                message = stringResource(neverAskAgainText),
                onSettingsTapped = {
                    onSettingsTapped()
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:" + context.packageName)
                        context.startActivity(this)
                    }
                },
            )
        }

        PermissionsHandler.Action.NO_ACTION -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleDialog(
    message: String,
    title: String = stringResource(R.string.allow_permission),
    primaryButtonText: String = stringResource(R.string.ok),
    onOkTapped: () -> Unit
) {

    AlertDialog(
        onDismissRequest = { }) {
        Surface(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(
                    /*modifier = Modifier
                        .padding(horizontal = DialogStyle.contentPadding)
                        .padding(top = DialogStyle.contentPadding),*/
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /*icon?.let { icon ->
                        Icon(
                            modifier = Modifier
                                .padding(start = 4.dp, end = 16.dp)
                                .size(36.dp),
                            imageVector = icon,
                            tint = primaryLight,
                            contentDescription = null,
                        )
                    }*/

                    Text(
                        text = title,
                        //style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = message,
                    //style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold
                )
                /* Box(modifier = Modifier.padding(horizontal = DialogStyle.contentPadding)) {
                     content()
                 }*/
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                    /*.padding(horizontal = DialogStyle.buttonPadding)
                    .padding(bottom = DialogStyle.buttonPadding)*/,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompositionLocalProvider(LocalMinimumTouchTargetEnforcement provides false) {
                        //dismissButtonText?.let { text ->
                        DialogButton(
                            text = primaryButtonText.uppercase(),
                            //capitalize = DialogStyle.capitalizeButton,
                            onClick = onOkTapped,
                        )
                        //}
                        /*DialogButton(
                            text = confirmButtonText,
                            capitalize = DialogStyle.capitalizeButton,
                            onClick = confirmButtonClicked,
                        )*/
                    }
                }
            }
        }
    }
    /*      title = {
              Text(
                  text = title,
                  style = MaterialTheme.typography.subtitle2,
                  fontWeight = FontWeight.Bold
              )
          },
          text = {
              Text(
                  text = message,
                  style = MaterialTheme.typography.body1,
                  color = Color.Black
              )
          },
          buttons = {
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 24.dp, vertical = 12.dp),
                  contentAlignment = Alignment.BottomEnd
              ) {
                  Text(
                      text = primaryButtonText.uppercase(),
                      modifier = Modifier
                          .padding(vertical = 12.dp)
                          .clickable { onOkTapped() },
                      style = MaterialTheme.typography.button,
                      fontWeight = FontWeight.Bold
                  )
              }
          },
          properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
      )*/
}

@Composable
private fun DialogButton(
    text: String,
    capitalize: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors = alertDialogButtonColors
    ) {
        Text(
            text = if (capitalize) text.uppercase() else text,
            //style = RecipesBookTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ShowGotoSettingsDialog(
    title: String,
    message: String,
    onSettingsTapped: () -> Unit
) {
    PermissionRationaleDialog(
        message = message,
        title = title,
        primaryButtonText = stringResource(id = R.string.settings),
        onOkTapped = onSettingsTapped
    )
    /*AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )
        },
        buttons = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(
                    text = stringResource(id = R.string.settings),
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .clickable { onSettingsTapped() },
                    style = MaterialTheme.typography.button,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )*/
}
