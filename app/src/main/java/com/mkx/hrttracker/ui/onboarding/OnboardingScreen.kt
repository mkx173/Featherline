package com.mkx.hrttracker.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Image
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.reminder.rememberReminderCapabilityReconciler
import com.mkx.hrttracker.reminder.shouldShowNotificationPermissionRecoveryToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.rounded.Add
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.WeightDialog
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.navigation.sharedAxisXEnterTransition
import com.mkx.hrttracker.ui.navigation.sharedAxisXExitTransition
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

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
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    var showGroupEditor by rememberSaveable { mutableStateOf(false) }
    var groupEditorOpenCount by rememberSaveable { mutableIntStateOf(0) }

    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val activity = LocalActivity.current
    val reminderCapabilityReconciler = rememberReminderCapabilityReconciler()
    val reminderCapabilityState by reminderCapabilityReconciler.state.collectAsStateWithLifecycle()
    val notificationsGranted = reminderCapabilityState.hasNotificationAccess
    val exactAlarmGranted = reminderCapabilityState.hasExactAlarmAccess
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)

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
        if (notificationsGranted) onCompleteEnabled() else onCompleteDeclined()
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

    BackHandler(enabled = step > 0 && !showGroupEditor) { goPrev() }
    BackHandler(enabled = showGroupEditor) { showGroupEditor = false }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
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
                        0 -> StartStep()
                        1 -> DisclaimerStep(
                            accepted = accepted,
                            onAcceptedChange = { accepted = it },
                            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                        )
                        2 -> NotificationsStep(
                            notificationsGranted = notificationsGranted,
                            exactAlarmGranted = exactAlarmGranted,
                            onAllowNotifications = {
                                when (
                                    resolveOnboardingNotificationPermissionAction(
                                        sdkInt = Build.VERSION.SDK_INT,
                                        hasRuntimePermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED,
                                        areNotificationsEnabled = NotificationManagerCompat
                                            .from(context)
                                            .areNotificationsEnabled(),
                                        hasRequestedPermissionBefore = hasRequestedNotificationPermission,
                                        shouldShowPermissionRationale = activity?.let {
                                            ActivityCompat.shouldShowRequestPermissionRationale(
                                                it,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        } ?: false
                                    )
                                ) {
                                    OnboardingNotificationPermissionAction.REQUEST_PERMISSION -> {
                                        hasRequestedNotificationPermission = true
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        "package:${context.packageName}".toUri()
                                    )
                                    exactAlarmLauncher.launch(intent)
                                }
                            },
                        )
                        3 -> UsefulInfoStep(
                            profile = uiState.userProfile,
                            activeGroupCount = uiState.activeGroupCount,
                            onSetWeightClick = { showWeightDialog = true },
                            onAddGroupClick = {
                                groupEditorOpenCount += 1
                                showGroupEditor = true
                            },
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
                        { goNext() }
                    } else {
                        null
                    },
                    secondaryButtonEnabled = currentStep != 2 || !notificationsGranted,
                    onCta = goNext,
                )
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            MedicationGroupEditorScreen(
                onNavigateBack = { showGroupEditor = false },
                onGroupSaved = { showGroupEditor = false },
                drawBehindNavigationBar = true,
                viewModel = editorViewModel,
            )
        }
    }
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
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
            )
        }
    }
}

@Composable
private fun StartStep() {
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
            AppIconHero()
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

@Composable
private fun AppIconHero() {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().scale(1.5f)
        )

        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().scale(1.5f)
        )
    }
}

@Composable
private fun StepHeader(
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
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
    onOpenPrivacyPolicy: () -> Unit,
) {
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
                    .verticalScroll(rememberScrollState()),
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
            label = stringResource(R.string.onboarding_disclaimer_acknowledge),
            onToggle = { onAcceptedChange(!accepted) },
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
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
            )
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
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
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
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
            AllowButton(granted = granted, onClick = { if (!granted) onAllow() })
        }

    }
}

@Composable
private fun AllowButton(
    granted: Boolean,
    onClick: () -> Unit,
    actionIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward
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
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
        ) {
            FilledTonalIconButton(
                onClick = onClick,
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun UsefulInfoStep(
    profile: UserProfile,
    activeGroupCount: Int,
    onSetWeightClick: () -> Unit,
    onAddGroupClick: () -> Unit,
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
            count = 2,
            actionGranted = profile.weightOriginalValue != null,
            onActionClick = onSetWeightClick,
            actionIcon = Icons.Rounded.Edit
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
            count = 2,
            actionGranted = hasGroup,
            onActionClick = if (hasGroup) null else onAddGroupClick,
            showActionWhenGranted = true,
            actionIcon = Icons.Rounded.Add
        )
    }
}

@Composable
private fun InfoCard(
    iconPainter: androidx.compose.ui.graphics.painter.Painter,
    iconBg: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconFg: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    title: String,
    desc: String,
    index: Int,
    count: Int,
    actionGranted: Boolean = false,
    onActionClick: (() -> Unit)? = null,
    actionIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward,
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
                    modifier = Modifier.size(24.dp),
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
                    actionIcon = actionIcon
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
        StartStep()
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
            onSetWeightClick = {},
            onAddGroupClick = {},
        )
    }
}
