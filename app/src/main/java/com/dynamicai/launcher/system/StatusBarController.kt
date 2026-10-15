package com.dynamicai.launcher.system

import android.content.Context
import android.util.Log

class StatusBarController(private val context: Context) {

    companion object {
        private const val TAG = "StatusBarCtrl"
    }

    fun expandNotifications(): Boolean {
        // 1. Стандартний API через reflection
        if (tryReflect("expandNotificationsPanel")) return true

        // 2. Альтернативна назва (старі Android)
        if (tryReflect("expandNotificationPanel")) return true

        // 3. Через Shizuku — StatusBarManagerService (system user)
        if (tryShizuku()) return true

        Log.w(TAG, "all attempts failed")
        return false
    }

    private fun tryReflect(method: String): Boolean {
        return try {
            val sbm = context.getSystemService(Context.STATUS_BAR_SERVICE)
                ?: return false
            val m = sbm.javaClass.getMethod(method)
            m.invoke(sbm)
            Log.d(TAG, "$method() OK")
            true
        } catch (t: Throwable) {
            Log.d(TAG, "$method() failed: ${t.message}")
            false
        }
    }

    private fun tryShizuku(): Boolean {
        return try {
            val clazz = Class.forName("android.app.StatusBarManager")
            // reflection через сервіс
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "statusbar") ?: return false

            val stub = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
            val asInterface = stub.getMethod("asInterface", android.os.IBinder::class.java)
            val service = asInterface.invoke(null, binder) ?: return false

            // виклик expandNotificationsPanel
            val method = service.javaClass.getMethod("expandNotificationsPanel")
            method.invoke(service)
            Log.d(TAG, "shizuku expandNotificationsPanel OK")
            true
        } catch (t: Throwable) {
            Log.d(TAG, "shizuku failed: ${t.message}")
            false
        }
    }
}
