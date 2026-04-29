package com.mkx.hrttracker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

class FakeAppTimeSource(initialMinute: LocalDateTime) : AppTimeSource {
    private val mutableCurrentMinute = MutableStateFlow(initialMinute.withSecond(0).withNano(0))

    override val currentMinute: StateFlow<LocalDateTime> = mutableCurrentMinute

    fun setCurrentMinute(currentMinute: LocalDateTime) {
        mutableCurrentMinute.value = currentMinute.withSecond(0).withNano(0)
    }
}
