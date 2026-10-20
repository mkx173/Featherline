package com.mkx.hrttracker.e2e

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.widget.HrtWidgetMediumReceiver

/** A real Android widget host, avoiding dependencies on a particular launcher's gestures. */
internal class E2eWidgetHost(private val context: Context) : AutoCloseable {
    private val host = AppWidgetHost(context, 7319)
    private var view: AppWidgetHostView? = null
    private var widgetId: Int? = null

    fun attach(scenario: ActivityScenario<MainActivity>) {
        val user = EmulatorDeviceState.shell("am get-current-user").trim().toInt()
        EmulatorDeviceState.shell("appwidget grantbind --package ${context.packageName} --user $user")
        val manager = AppWidgetManager.getInstance(context)
        val id = host.allocateAppWidgetId().also { widgetId = it }
        val component = ComponentName(context, HrtWidgetMediumReceiver::class.java)
        check(manager.bindAppWidgetIdIfAllowed(id, component)) { "Cannot bind E2E widget" }
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 380)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 380)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 230)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 230)
        })
        host.startListening()
        scenario.onActivity { activity ->
            // RemoteViews must inflate platform widgets, without the activity's AppCompat factory.
            view = host.createView(context, id, manager.getAppWidgetInfo(id)).also { hostView ->
                val density = activity.resources.displayMetrics.density
                activity.addContentView(hostView, ViewGroup.LayoutParams((380 * density).toInt(), (230 * density).toInt()))
            }
        }
    }

    fun visibleTexts(): List<String> {
        val texts = mutableListOf<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun collect(view: View) {
                if (view.visibility != View.VISIBLE) return
                if (view is TextView) texts += view.text.toString()
                if (view is ViewGroup) repeat(view.childCount) { collect(view.getChildAt(it)) }
            }
            view?.let(::collect)
        }
        return texts
    }

    override fun close() {
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view?.let { (it.parent as? ViewGroup)?.removeView(it) }
            }
            widgetId?.let(host::deleteAppWidgetId)
            host.stopListening()
        } finally {
            val user = EmulatorDeviceState.shell("am get-current-user").trim().toInt()
            EmulatorDeviceState.shell("appwidget revokebind --package ${context.packageName} --user $user")
        }
    }
}
