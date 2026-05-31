package com.mkx.hrttracker.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.reminder.rememberReminderCapabilityReconciler
import com.mkx.hrttracker.reminder.shouldShowNotificationPermissionRecoveryToast
import com.mkx.hrttracker.ui.catalog.MedicineManagerLaunchMode
import com.mkx.hrttracker.ui.catalog.MedicinesScreen
import com.mkx.hrttracker.ui.catalog.CreateMedicineThenDoseSheet
import com.mkx.hrttracker.ui.catalog.CreateMedicineThenDoseSheetMode
import com.mkx.hrttracker.ui.catalog.NewMedicineSlotViewModel
import com.mkx.hrttracker.ui.catalog.canHideCreateMedicineThenDoseSheet
import com.mkx.hrttracker.ui.components.AppContentMaxWidth
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.WeightDialog
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.shortLabelRes
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.navigation.sharedAxisXEnterTransition
import com.mkx.hrttracker.ui.navigation.sharedAxisXExitTransition
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class OnboardingNotificationPermissionAction {
    REQUEST_PERMISSION,
    OPEN_NOTIFICATION_SETTINGS,
    SHOW_UNAVAILABLE_TOAST
}

internal fun resolveOnboardingProgressAlpha(showProgress: Boolean): Float =
    if (showProgress) 1f else 0f

internal fun resolveOnboardingNotificationPermissionAction(
    sdkInt: Int,
    hasRuntimePermission: Boolean,
    areNotificationsEnabled: Boolean,
    hasRequestedPermissionBefore: Boolean,
    shouldShowPermissionRationale: Boolean
): OnboardingNotificationPermissionAction {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        return OnboardingNotificationPermissionAction.OPEN_NOTIFICATION_SETTINGS
    }

    if (!hasRuntimePermission) {
        return if (
            shouldShowNotificationPermissionRecoveryToast(
                sdkInt = sdkInt,
                hasRequestedPermissionBefore = hasRequestedPermissionBefore,
                shouldShowPermissionRationale = shouldShowPermissionRationale
            )
        ) {
            OnboardingNotificationPermissionAction.SHOW_UNAVAILABLE_TOAST
        } else {
            OnboardingNotificationPermissionAction.REQUEST_PERMISSION
        }
    }

    return if (areNotificationsEnabled) {
        OnboardingNotificationPermissionAction.OPEN_NOTIFICATION_SETTINGS
    } else {
        OnboardingNotificationPermissionAction.SHOW_UNAVAILABLE_TOAST
    }
}

@Composable
fun OnboardingScreen(
    onOpenPrivacyPolicy: () -> Unit,
    onCompleteEnabled: () -> Unit,
    onCompleteDeclined: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val totalSteps = 4
    val progressSteps = totalSteps - 1
    var step by rememberSaveable { mutableIntStateOf(0) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var disclaimerReadToBottom by rememberSaveable { mutableStateOf(false) }
    var startAnimationPlayed by rememberSaveable { mutableStateOf(false) }
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    var showGroupEditor by rememberSaveable { mutableStateOf(false) }
    var showStockOnboarding by rememberSaveable { mutableStateOf(false) }
    var groupEditorOpenCount by rememberSaveable { mutableIntStateOf(0) }
    var remindersAcceptedDuringOnboarding by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    val reminderCapabilityReconciler = rememberReminderCapabilityReconciler()
    val reminderCapabilityState by reminderCapabilityReconciler.state.collectAsStateWithLifecycle()
    val notificationsGranted = reminderCapabilityState.hasNotificationAccess
    val exactAlarmGranted = reminderCapabilityState.hasExactAlarmAccess
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)
    val onboardingUpdateFailedMessage = stringResource(R.string.onboarding_update_failed)

    LaunchedEffect(viewModel) {
        viewModel.onboardingMutationEvents.collect { event ->
            when (event) {
                is OnboardingMutationEvent.Failure -> {
                    Toast.makeText(
                        context,
                        onboardingUpdateFailedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            viewModel.consumeOnboardingMutationEvent()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        reminderCapabilityReconciler.requestReconcile("onboarding_notification_permission_result")
        if (!isGranted) {
            Toast.makeText(
                context,
                notificationsUnavailableMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        reminderCapabilityReconciler.requestReconcile("onboarding_exact_alarm_result")
    }

    val finishOnboarding = {
        if (remindersAcceptedDuringOnboarding) onCompleteEnabled() else onCompleteDeclined()
    }

    val goNext = {
        if (step < totalSteps - 1) {
            step += 1
        } else {
            finishOnboarding()
        }
    }
    val goPrev = {
        if (step > 0) step -= 1
    }
    val acceptRemindersAndGoNext: () -> Unit = {
        remindersAcceptedDuringOnboarding = true
        coroutineScope.launch {
            if (viewModel.setRemindersEnabledDuringOnboarding(true)) {
                goNext()
            }
        }
    }
    val declineRemindersAndGoNext: () -> Unit = {
        remindersAcceptedDuringOnboarding = false
        coroutineScope.launch {
            if (viewModel.setRemindersEnabledDuringOnboarding(false)) {
                goNext()
            }
        }
    }

    BackHandler(enabled = step > 0 && !showGroupEditor && !showStockOnboarding) { goPrev() }
    BackHandler(enabled = showGroupEditor) { showGroupEditor = false }
    BackHandler(enabled = showStockOnboarding) { showStockOnboarding = false }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        OnboardingContentFrame {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState > initialState
                    sharedAxisXEnterTransition(
                        density = density,
                        layoutDirection = layoutDirection,
                        forward = forward,
                    ) togetherWith sharedAxisXExitTransition(
                        density = density,
                        layoutDirection = layoutDirection,
                        forward = forward,
                    )
                },
                label = "onboarding-step",
            ) {
                val currentStep = it
                val showProgress = currentStep >= 1

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                ) {
                    OnboardingTopChrome(
                        showProgress = showProgress,
                        progressTotal = progressSteps,
                        progressCurrent = currentStep,
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when (currentStep) {
                            0 -> StartStep(
                                animationPlayed = startAnimationPlayed,
                                onAnimationPlayed = { startAnimationPlayed = true },
                            )
                            1 -> DisclaimerStep(
                                accepted = accepted,
                                onAcceptedChange = { accepted = it },
                                readToBottom = disclaimerReadToBottom,
                                onReadToBottom = { disclaimerReadToBottom = true },
                                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                            )
                            2 -> NotificationsStep(
                                notificationsGranted = notificationsGranted,
                                exactAlarmGranted = exactAlarmGranted,
                                onAllowNotifications = {
                                    val hasRuntimePerm: Boolean
                                    val shouldShowRationale: Boolean
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        hasRuntimePerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        shouldShowRationale = activity?.let {
                                            ActivityCompat.shouldShowRequestPermissionRationale(
                                                it,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        } ?: false
                                    } else {
                                        hasRuntimePerm = false
                                        shouldShowRationale = false
                                    }
                                    when (
                                        resolveOnboardingNotificationPermissionAction(
                                            sdkInt = Build.VERSION.SDK_INT,
                                            hasRuntimePermission = hasRuntimePerm,
                                            areNotificationsEnabled = NotificationManagerCompat
                                                .from(context)
                                                .areNotificationsEnabled(),
                                            hasRequestedPermissionBefore = hasRequestedNotificationPermission,
                                            shouldShowPermissionRationale = shouldShowRationale,
                                        )
                                    ) {
                                        OnboardingNotificationPermissionAction.REQUEST_PERMISSION -> {
                                            hasRequestedNotificationPermission = true
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                        OnboardingNotificationPermissionAction.OPEN_NOTIFICATION_SETTINGS -> {
                                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            context.startActivity(intent)
                                        }
                                        OnboardingNotificationPermissionAction.SHOW_UNAVAILABLE_TOAST -> {
                                            Toast.makeText(
                                                context,
                                                notificationsUnavailableMessage,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onAllowExactAlarm = {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        "package:${context.packageName}".toUri()
                                    )
                                    exactAlarmLauncher.launch(intent)
                                },
                            )
                            3 -> UsefulInfoStep(
                                profile = uiState.userProfile,
                                activeGroupCount = uiState.activeGroupCount,
                                trackedMedicineCount = uiState.trackedMedicineCount,
                                onSetWeightClick = { showWeightDialog = true },
                                onAddGroupClick = {
                                    groupEditorOpenCount += 1
                                    showGroupEditor = true
                                },
                                onEnableStockClick = { showStockOnboarding = true },
                            )
                        }
                    }

                    OnboardingBottomChrome(
                        ctaLabel = when (currentStep) {
                            0 -> stringResource(R.string.onboarding_start_cta)
                            totalSteps - 1 -> stringResource(R.string.onboarding_open_app)
                            else -> stringResource(R.string.onboarding_continue)
                        },
                        ctaEnabled = when (currentStep) {
                            1 -> accepted
                            2 -> notificationsGranted
                            else -> true
                        },
                        secondaryButtonLabel = if (currentStep == 2) {
                            stringResource(R.string.onboarding_skip_notifications)
                        } else {
                            null
                        },
                        onSecondaryButtonClick = if (currentStep == 2) {
                            declineRemindersAndGoNext
                        } else {
                            null
                        },
                        secondaryButtonEnabled = currentStep != 2 || !notificationsGranted,
                        onCta = if (currentStep == 2) {
                            acceptRemindersAndGoNext
                        } else {
                            goNext
                        },
                    )
                }
            }
        }
    }

    if (showWeightDialog) {
        WeightDialog(
            profile = uiState.userProfile,
            onSave = { value, unit ->
                viewModel.setWeight(value, unit)
                showWeightDialog = false
            },
            onClear = {
                viewModel.clearWeight()
                showWeightDialog = false
            },
            onDismiss = { showWeightDialog = false },
        )
    }

    AnimatedVisibility(
        visible = showGroupEditor,
        modifier = Modifier.fillMaxSize(),
        enter = sharedAxisXEnterTransition(
            density = density,
            layoutDirection = layoutDirection,
            forward = true,
        ),
        exit = sharedAxisXExitTransition(
            density = density,
            layoutDirection = layoutDirection,
            forward = false,
        ),
        label = "onboarding-group-editor",
    ) {
        val editorViewModel: MedicationGroupEditorViewModel =
            hiltViewModel(key = "onboarding-group-editor-$groupEditorOpenCount")
        var pendingNewMedicineSlotLocalId by rememberSaveable {
            mutableStateOf<String?>(null)
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            MedicationGroupEditorScreen(
                onNavigateBack = { showGroupEditor = false },
                onGroupSaved = { showGroupEditor = false },
                drawBehindNavigationBar = true,
                viewModel = editorViewModel,
                // Onboarding has no medicine catalog yet, so we skip the
                // (necessarily empty) medicine manager and open the
                // create-medicine-and-dose sheet directly.
                onOpenMedicinePicker = { localId ->
                    pendingNewMedicineSlotLocalId = localId
                },
            )
        }
        OnboardingNewMedicineSlotHost(
            pendingLocalId = pendingNewMedicineSlotLocalId,
            slotInstanceKey = "onboarding-new-medicine-slot-$groupEditorOpenCount",
            onDismiss = { pendingNewMedicineSlotLocalId = null },
            onSlotResolved = { localId, slot ->
                editorViewModel.addCompletedMedicationSlot(
                    localId = localId,
                    slot = slot,
                )
            },
        )
    }

    AnimatedVisibility(
        visible = showStockOnboarding,
        modifier = Modifier.fillMaxSize(),
        enter = sharedAxisXEnterTransition(
            density = density,
            layoutDirection = layoutDirection,
            forward = true,
        ),
        exit = sharedAxisXExitTransition(
            density = density,
            layoutDirection = layoutDirection,
            forward = false,
        ),
        label = "onboarding-stock-medicine-manager",
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            MedicinesScreen(
                onNavigateBack = { showStockOnboarding = false },
                onMedicineClick = { /* unreachable in OnboardingStockOptIn mode */ },
                launchMode = MedicineManagerLaunchMode.OnboardingStockOptIn,
            )
        }
    }
}

@Composable
internal fun OnboardingContentFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val contentWidth = minOf(maxWidth, AppContentMaxWidth)
        Box(
            modifier = modifier
                .width(contentWidth)
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingNewMedicineSlotHost(
    pendingLocalId: String?,
    slotInstanceKey: String,
    onDismiss: () -> Unit,
    onSlotResolved: (
        localId: String,
        slot: com.mkx.hrttracker.ui.catalog.MedicineSlotResult,
    ) -> Unit,
) {
    if (pendingLocalId == null) return
    val viewModel: NewMedicineSlotViewModel = hiltViewModel(key = slotInstanceKey)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSlotLocked = uiState.isSaving || uiState.isSaved
    val isSlotLockedState = rememberUpdatedState(isSlotLocked)
    val allowCompletionHideState = remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            canHideCreateMedicineThenDoseSheet(
                value = value,
                isSlotLocked = isSlotLockedState.value,
                allowCompletionHide = allowCompletionHideState.value,
            )
        },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pendingLocalId) { viewModel.reset() }

    CreateMedicineThenDoseSheet(
        sheetState = sheetState,
        onDismissRequest = {
            if (!isSlotLockedState.value) onDismiss()
        },
        onCloseClick = {
            if (!isSlotLockedState.value) {
                hideBottomSheet(scope, sheetState) { onDismiss() }
            }
        },
        onGroupSlotResolved = { slotResult, consumeSavedState ->
            // Append the medication synchronously so the editor underneath
            // reflects it while the sheet is still sliding away, instead of
            // popping in after the dismiss animation completes.
            onSlotResolved(pendingLocalId, slotResult)
            allowCompletionHideState.value = true
            hideBottomSheet(scope, sheetState) {
                allowCompletionHideState.value = false
                consumeSavedState()
                onDismiss()
            }
        },
        mode = CreateMedicineThenDoseSheetMode.GROUP_SLOT,
        viewModel = viewModel,
    )
}

@Composable
private fun OnboardingTopChrome(
    showProgress: Boolean,
    progressTotal: Int,
    progressCurrent: Int,
) {
    SegmentedProgress(
        total = progressTotal,
        current = (progressCurrent - 1).coerceIn(0, progressTotal - 1),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(resolveOnboardingProgressAlpha(showProgress))
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun SegmentedProgress(
    total: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val pending = MaterialTheme.colorScheme.surfaceContainerHighest
    val notchInner = MaterialTheme.colorScheme.surfaceContainerLowest
    Row(
        modifier = modifier.height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            val isDone = index < current
            val isActive = index == current
            val color = if (isDone || isActive) primary else pending
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(color),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(notchInner),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingBottomChrome(
    ctaLabel: String,
    ctaEnabled: Boolean,
    secondaryButtonLabel: String? = null,
    onSecondaryButtonClick: (() -> Unit)? = null,
    secondaryButtonEnabled: Boolean = true,
    onCta: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
    ) {
        if (secondaryButtonLabel != null && onSecondaryButtonClick != null) {
            FilledTonalButton(
                onClick = onSecondaryButtonClick,
                enabled = secondaryButtonEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = secondaryButtonLabel,
                    modifier = Modifier.cjkTextOffset(secondaryButtonLabel)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = onCta,
            enabled = ctaEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = ctaLabel,
                modifier = Modifier.cjkTextOffset(ctaLabel)
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
            )
        }
    }
}

@Composable
private fun StartStep(
    animationPlayed: Boolean,
    onAnimationPlayed: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AppIconHero(
                animationPlayed = animationPlayed,
                onAnimationPlayed = onAnimationPlayed,
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_start_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

private const val FEATHER_VIEWPORT = 512f
private const val FEATHER_ICON_SCALE = 1.5f
private const val FEATHER_PATH_DATA =
    "M335.48,358.35c3.04,1.42,6.33-2.47,4.23-5.21h0c-.32-.53-.84-.95-1.47-1.23-20.93-9.13-38.58-19.78-55.88-33.19-18.88-14.84-35.57-32.01-49.93-51.13-23.66-30.27-39.41-68.32-40.35-72.67-2.48-4.65,4.4-8.12,6.33-3.11-.41.46,29.44,85.8,107.7,135.33.24-5.14,1.92-15.21,10.28-21.4.84-.59.62-2.01-.38-2.27-1.05-.35-8.71-2.69-13.81-1.33,3.84-11.23-8.8-22.1-5-33.08.46-1.17-1.04-2.25-2.03-1.43-.8.63-6.29,5.11-8.32,9.86-2.06-4.58-4.27-11.47-2.03-15.53.74-1.34-1.15-2.67-2.13-1.5-.07.1-4.65,5.42-6.92,12.38-2.76-12.76-10.56-39.9-29.76-60.81-.66-.77-2.06-.42-2.24.59l-.87,3.88c-2.94-8.11-10.32-25.32-23.43-36.75-.81-.69-2.2-.14-2.2.98l-.14,7.06c-5.84-11.15-21.26-37.49-35.46-34.16-16.24,5.11-8.73,41.54-4.96,55.57-5.95,27.48,11.29,50.74,26.16,65.01l-12.1,1.96c-1.08.11-1.52,1.6-.66,2.27.14.14,15,12.8,17.9,16.86,1.82,2.52,12.73,11.09,25.7,16.68-6.4,2.2-10.21,8.64-10.42,8.99-.35.63-.14,1.4.46,1.78,24.2,14.44,43.96,9.13,51.02,6.33-1.01,4.06-1.96,11.51,2.8,17.45.27.34.7.52,1.12.49,1.53-.04,4.87-8.05,12.97-12.9,13.11,8.99,27.7,17.24,43.85,24.23"

@Composable
private fun AppIconHero(
    animationPlayed: Boolean,
    onAnimationPlayed: () -> Unit,
) {
    val featherAndroidPath = remember {
        PathParser().parsePathString(FEATHER_PATH_DATA).toPath().asAndroidPath()
    }
    val pathMeasure = remember { android.graphics.PathMeasure(featherAndroidPath, false) }
    val pathLength = remember { pathMeasure.length }
    val strokeColor = MaterialTheme.colorScheme.outlineVariant

    val drawProgress = remember { Animatable(if (animationPlayed) 1f else 0f) }
    val iconAlpha = remember { Animatable(if (animationPlayed) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (animationPlayed) return@LaunchedEffect
        delay(150)
        drawProgress.animateTo(1f, animationSpec = tween(durationMillis = 1100, easing = EaseInOut))
        iconAlpha.animateTo(1f, animationSpec = tween(durationMillis = 450))
        onAnimationPlayed()
    }

    Box(modifier = Modifier.size(160.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(1f - iconAlpha.value)
        ) {
            val scale = (size.minDimension / FEATHER_VIEWPORT) * FEATHER_ICON_SCALE
            val segment = android.graphics.Path()
            pathMeasure.getSegment((1f - drawProgress.value) * pathLength, pathLength, segment, true)
            withTransform({
                translate(size.width / 2f, size.height / 2f)
                scale(scale, scale, pivot = Offset.Zero)
                translate(-FEATHER_VIEWPORT / 2f, -FEATHER_VIEWPORT / 2f)
            }) {
                drawPath(
                    path = segment.asComposePath(),
                    color = strokeColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .alpha(iconAlpha.value)
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().scale(FEATHER_ICON_SCALE)
            )

            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(FEATHER_ICON_SCALE)
            )
        }
    }
}

@Composable
private fun StepHeader(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    iconBg: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconFg: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    title: String,
    desc: String,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.large)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconFg,
                    modifier = Modifier.size(36.dp),
                )
            } else if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = iconFg,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DisclaimerStep(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    readToBottom: Boolean,
    onReadToBottom: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // canScrollForward starts true (maxValue is Int.MAX_VALUE pre-layout) and
    // becomes false either at the bottom or when the text already fits, so this
    // also unlocks the checkbox immediately when there's nothing to scroll.
    LaunchedEffect(scrollState.canScrollForward) {
        if (!scrollState.canScrollForward) onReadToBottom()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        StepHeader(
            iconPainter = painterResource(R.drawable.ic_privacy_tip),
            title = stringResource(R.string.onboarding_disclaimer_title),
            desc = stringResource(R.string.onboarding_disclaimer_subtitle),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                DisclaimerSection(
                    heading = stringResource(R.string.onboarding_disclaimer_data_heading),
                    body = stringResource(R.string.onboarding_disclaimer_data_body),
                )
                DisclaimerSection(
                    heading = stringResource(R.string.onboarding_disclaimer_estimates_heading),
                    body = stringResource(R.string.onboarding_disclaimer_estimates_body),
                )
                DisclaimerSection(
                    heading = stringResource(R.string.onboarding_disclaimer_advice_heading),
                    body = stringResource(R.string.onboarding_disclaimer_advice_body),
                )
                DisclaimerSection(
                    heading = stringResource(R.string.onboarding_disclaimer_reminders_heading),
                    body = stringResource(R.string.onboarding_disclaimer_reminders_body),
                    isLast = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                            )
                        ) {
                            append(stringResource(R.string.onboarding_disclaimer_view_full_policy))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable(onClick = onOpenPrivacyPolicy)
                        .padding(vertical = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AcceptanceCheckbox(
            checked = accepted,
            locked = !readToBottom,
            label = stringResource(R.string.onboarding_disclaimer_acknowledge_read),
            onToggle = {
                if (readToBottom) {
                    onAcceptedChange(!accepted)
                } else {
                    // Nudge the user to actually read it: tapping before
                    // reaching the bottom scrolls there, which unlocks the
                    // checkbox via the canScrollForward effect above.
                    scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                }
            },
            index = 0,
            count = 1,
        )
    }
}

@Composable
private fun DisclaimerSection(
    heading: String,
    body: String,
    isLast: Boolean = false,
) {
    Text(
        text = heading,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = if (isLast) 0.dp else 12.dp),
    )
}

@Composable
private fun AcceptanceCheckbox(
    checked: Boolean,
    locked: Boolean,
    label: String,
    onToggle: () -> Unit,
    index: Int,
    count: Int,
) {
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val textColor = if (checked) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        trailingContent = {
            Crossfade(targetState = locked, label = "acceptanceTrailing") { isLocked ->
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLocked) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_downward),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggle() },
                        )
                    }
                }
            }
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(start = 8.dp).cjkTextOffset(label),
        )
    }
}

@Composable
private fun NotificationsStep(
    notificationsGranted: Boolean,
    exactAlarmGranted: Boolean,
    onAllowNotifications: () -> Unit,
    onAllowExactAlarm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        StepHeader(
            iconPainter = painterResource(R.drawable.ic_notifications),
            title = stringResource(R.string.onboarding_notifications_title),
            desc = stringResource(R.string.onboarding_notifications_subtitle),
        )

        PermissionCard(
            iconPainter = painterResource(R.drawable.ic_notifications),
            title = stringResource(R.string.onboarding_notifications_app_title),
            desc = stringResource(R.string.onboarding_notifications_app_desc),
            granted = notificationsGranted,
            optional = false,
            onAllow = onAllowNotifications,
            index = 0,
            count = 2,
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
        PermissionCard(
            iconPainter = painterResource(R.drawable.ic_alarm_filled),
            title = stringResource(R.string.onboarding_notifications_alarm_title),
            desc = stringResource(R.string.onboarding_notifications_alarm_desc),
            granted = exactAlarmGranted,
            optional = true,
            onAllow = onAllowExactAlarm,
            index = 1,
            count = 2,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    title: String,
    desc: String,
    granted: Boolean,
    optional: Boolean,
    onAllow: () -> Unit,
    index: Int,
    count: Int,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = { if (!granted) onAllow() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                } else if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alignByBaseline().cjkTextOffset(title)
                    )
                    if (optional) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_notifications_optional),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline().cjkTextOffset(stringResource(R.string.onboarding_notifications_optional))
                        )
                    }
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(desc)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            AllowButton(
                granted = granted,
                onClick = { if (!granted) onAllow() },
                actionIconPainter = painterResource(R.drawable.ic_arrow_forward)
            )
        }

    }
}

@Composable
private fun AllowButton(
    granted: Boolean,
    onClick: () -> Unit,
    actionIconPainter: Painter,
    actionIconSize: Dp = 18.dp,
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        if (granted) {
            IconButton(
                onClick = {},
                enabled = false,
                colors = IconButtonDefaults.iconButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            FilledTonalIconButton(
                onClick = onClick,
            ) {
                Icon(
                    painter = actionIconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(actionIconSize),
                )
            }
        }
    }
}

@Composable
private fun UsefulInfoStep(
    profile: UserProfile,
    activeGroupCount: Int,
    trackedMedicineCount: Int,
    onSetWeightClick: () -> Unit,
    onAddGroupClick: () -> Unit,
    onEnableStockClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        StepHeader(
            icon = Icons.Rounded.Checklist,
            title = stringResource(R.string.onboarding_useful_title),
            desc = stringResource(R.string.onboarding_useful_subtitle),
        )

        InfoCard(
            iconPainter = painterResource(R.drawable.ic_monitor_weight),
            title = stringResource(R.string.onboarding_useful_weight_title),
            desc = if (profile.weightOriginalValue == null) {
                stringResource(R.string.onboarding_useful_weight_desc)
            } else {
                formatWeightSummary(profile)
            },
            index = 0,
            count = 3,
            actionGranted = profile.weightOriginalValue != null,
            onActionClick = onSetWeightClick,
            actionIconPainter = painterResource(R.drawable.ic_edit),
            actionIconSize = 16.dp
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
        val hasGroup = activeGroupCount > 0
        InfoCard(
            iconPainter = painterResource(R.drawable.ic_medication),
            title = stringResource(R.string.onboarding_useful_plan_title),
            desc = if (hasGroup) {
                stringResource(R.string.onboarding_useful_plan_added)
            } else {
                stringResource(R.string.onboarding_useful_plan_desc)
            },
            index = 1,
            count = 3,
            actionGranted = hasGroup,
            onActionClick = if (hasGroup) null else onAddGroupClick,
            showActionWhenGranted = true,
            actionIconPainter = painterResource(R.drawable.ic_add),
            actionIconSize = 22.dp
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
        val hasTrackedStock = trackedMedicineCount > 0
        InfoCard(
            iconPainter = painterResource(R.drawable.ic_box),
            title = stringResource(R.string.onboarding_useful_stock_title),
            desc = if (hasTrackedStock) {
                stringResource(R.string.onboarding_useful_stock_added)
            } else {
                stringResource(R.string.onboarding_useful_stock_desc)
            },
            index = 2,
            count = 3,
            actionGranted = hasTrackedStock,
            onActionClick = if (hasTrackedStock) null else onEnableStockClick,
            showActionWhenGranted = true,
            actionIconPainter = painterResource(R.drawable.ic_arrow_forward),
            actionIconSize = 18.dp,
            iconSize = 22.dp
        )
    }
}

@Composable
private fun InfoCard(
    iconPainter: Painter,
    iconSize: Dp = 24.dp,
    iconBg: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconFg: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    title: String,
    desc: String,
    index: Int,
    count: Int,
    actionGranted: Boolean = false,
    onActionClick: (() -> Unit)? = null,
    actionIconPainter: Painter,
    actionIconSize: Dp,
    showActionWhenGranted: Boolean = false,
) {
    val showAction = onActionClick != null || (actionGranted && showActionWhenGranted)
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = onActionClick ?: {},
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = iconFg,
                    modifier = Modifier.size(iconSize),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.cjkTextOffset(title)
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(desc)
                )
            }
            if (showAction) {
                Spacer(modifier = Modifier.width(12.dp))
                AllowButton(
                    granted = actionGranted,
                    onClick = onActionClick ?: {},
                    actionIconPainter = actionIconPainter,
                    actionIconSize = actionIconSize
                )
            }
        }
    }
}

@Composable
private fun formatWeightSummary(profile: UserProfile): String {
    val value = profile.weightOriginalValue
    val unit = profile.weightOriginalUnit
    return if (value == null) {
        stringResource(R.string.personalization_weight_not_set)
    } else {
        val formatted = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        stringResource(
            R.string.personalization_weight_display,
            formatted,
            stringResource(unit.shortLabelRes)
        )
    }
}

@Composable
private fun OnboardingStepPreviewFrame(
    step: Int,
    ctaLabel: String,
    ctaEnabled: Boolean = true,
    secondaryButtonLabel: String? = null,
    showSkip: Boolean = false,
    content: @Composable () -> Unit,
) {
    HrtTrackerTheme(dynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            OnboardingContentFrame {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                ) {
                    OnboardingTopChrome(
                        showProgress = step >= 1,
                        progressTotal = 3,
                        progressCurrent = step,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        content()
                    }
                    OnboardingBottomChrome(
                        ctaLabel = ctaLabel,
                        ctaEnabled = ctaEnabled,
                        secondaryButtonLabel = secondaryButtonLabel,
                        onSecondaryButtonClick = if (secondaryButtonLabel != null) {
                            {}
                        } else {
                            null
                        },
                        secondaryButtonEnabled = true,
                        onCta = { },
                    )
                }
            }
        }
    }
}

@Preview(
    name = "Onboarding Start",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun OnboardingStartPreview() {
    OnboardingStepPreviewFrame(
        step = 0,
        ctaLabel = stringResource(R.string.onboarding_start_cta),
    ) {
        StartStep(animationPlayed = false, onAnimationPlayed = { })
    }
}

@Preview(
    name = "Onboarding Disclaimer",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun OnboardingDisclaimerPreview() {
    OnboardingStepPreviewFrame(
        step = 1,
        ctaLabel = stringResource(R.string.onboarding_continue),
        ctaEnabled = false,
    ) {
        DisclaimerStep(
            accepted = false,
            onAcceptedChange = { },
            readToBottom = false,
            onReadToBottom = { },
            onOpenPrivacyPolicy = { },
        )
    }
}

@Preview(
    name = "Onboarding Notifications",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun OnboardingNotificationsPreview() {
    OnboardingStepPreviewFrame(
        step = 2,
        ctaLabel = stringResource(R.string.onboarding_continue),
        ctaEnabled = false,
        secondaryButtonLabel = stringResource(R.string.onboarding_skip_notifications),
        showSkip = true,
    ) {
        NotificationsStep(
            notificationsGranted = false,
            exactAlarmGranted = false,
            onAllowNotifications = { },
            onAllowExactAlarm = { },
        )
    }
}

@Preview(
    name = "Onboarding Useful Info",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun OnboardingUsefulInfoPreview() {
    OnboardingStepPreviewFrame(
        step = 3,
        ctaLabel = stringResource(R.string.onboarding_open_app),
        showSkip = true,
    ) {
        UsefulInfoStep(
            profile = UserProfile(),
            activeGroupCount = 0,
            trackedMedicineCount = 0,
            onSetWeightClick = {},
            onAddGroupClick = {},
            onEnableStockClick = {},
        )
    }
}
