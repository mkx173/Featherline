package com.mkx.hrttracker.ui.pkcalibrationdebug

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** Picker state of the published forced fixture, so a fresh harness ViewModel can replay it. */
@Singleton
class PkCalibrationDebugScenarioStore @Inject constructor() {
    val scenario = MutableStateFlow<PkCalibrationDebugScenario?>(null)
}
