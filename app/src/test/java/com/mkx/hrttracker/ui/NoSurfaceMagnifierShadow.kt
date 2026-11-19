package com.mkx.hrttracker.ui

import android.widget.Magnifier
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

// Robolectric's Magnifier has no Surface: once shown, dismiss() throws a NullPointerException.
// A state-based text field shows and dismisses its magnifier as its activity is destroyed, so
// showing does nothing here. Registered for every test in robolectric.properties.
@Implements(Magnifier::class)
class NoSurfaceMagnifierShadow {
    @Implementation
    fun show(sourceCenterX: Float, sourceCenterY: Float, magnifierCenterX: Float, magnifierCenterY: Float) = Unit
}
