package com.dynamicai.launcher.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

object WidgetPinner {
    fun requestPin(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val myProvider = ComponentName(context, MusicWidgetReceiver::class.java)
        
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val successCallback = PendingIntent.getActivity(
                context, 0, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
        }
    }
}
