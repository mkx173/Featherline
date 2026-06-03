package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FlipSlotTest {
    @Test
    fun flipSlotFace_switches_faces_at_halfway() {
        assertEquals(
            FlipSlotFace.FRONT,
            flipSlotFace(progress = 0f)
        )
        assertEquals(
            FlipSlotFace.FRONT,
            flipSlotFace(progress = 0.49f)
        )
        assertEquals(
            FlipSlotFace.BACK,
            flipSlotFace(progress = 0.5f)
        )
        assertEquals(
            FlipSlotFace.BACK,
            flipSlotFace(progress = 1f)
        )
    }

    @Test
    fun flipSlotRotationX_rotates_front_out_and_back_in() {
        assertEquals(
            0f,
            flipSlotRotationX(
                progress = 0f,
                face = FlipSlotFace.FRONT
            ),
            0.001f
        )
        assertEquals(
            45f,
            flipSlotRotationX(
                progress = 0.25f,
                face = FlipSlotFace.FRONT
            ),
            0.001f
        )
        assertEquals(
            -45f,
            flipSlotRotationX(
                progress = 0.75f,
                face = FlipSlotFace.BACK
            ),
            0.001f
        )
        assertEquals(
            0f,
            flipSlotRotationX(
                progress = 1f,
                face = FlipSlotFace.BACK
            ),
            0.001f
        )
    }
}
