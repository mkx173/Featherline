package com.mkx.hrttracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll as glanceUpdateAll

class HrtWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Placeholder — UI will be implemented in a subsequent task.
    }

    suspend fun updateAll(context: Context) {
        glanceUpdateAll(context)
    }
}

class HrtWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtWidget()
}
