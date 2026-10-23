package com.dynamicai.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action ?: return
            if (action == Intent.ACTION_PACKAGE_ADDED
                || action == Intent.ACTION_PACKAGE_REMOVED
                || action == Intent.ACTION_PACKAGE_REPLACED
                || action == Intent.ACTION_PACKAGE_CHANGED) {
                
                val pkg = intent.data?.schemeSpecificPart ?: ""
                Log.e("PkgChange", "$action: $pkg")
                
                context.getSharedPreferences("dynamicai_runtime", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("apps_changed_at", System.currentTimeMillis())
                    .putString("apps_changed_pkg", pkg)
                    .apply()
            }
        } catch (t: Throwable) {
            Log.e("PkgChange", "error: ${t.message}", t)
        }
    }
}
