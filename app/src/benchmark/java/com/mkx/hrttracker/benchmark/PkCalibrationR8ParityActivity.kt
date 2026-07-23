package com.mkx.hrttracker.benchmark

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationParityVectors

/** Manifest-reachable benchmark probe that makes R8 optimize the real numerical call graph. */
class PkCalibrationR8ParityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(intent.action == PkCalibrationParityVectors.ACTION)
        setContentView(
            TextView(this).apply {
                id = R.id.pk_calibration_r8_parity_result
                tag = PkCalibrationParityVectors.RESULT_TAG
                contentDescription = PkCalibrationParityVectors.RESULT_TAG
                gravity = Gravity.CENTER
                text = PkCalibrationParityVectors.run().uiText
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        )
    }
}
