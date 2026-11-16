package com.dynamicai.launcher.overlay

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import com.dynamicai.launcher.accessibility.AppAutomationService
import com.dynamicai.launcher.macro.SystemTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Швидкі дії острівця: ліхтарик, мережа, Bluetooth, «Не турбувати», скріншот, камера. */
object IslandActions {
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()
    private var torchId: String? = null
    private var torchRegistered = false

    fun init(ctx: Context) {
        if (torchRegistered) return
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) _torch.value = enabled
                }
            }, Handler(Looper.getMainLooper()))
            torchRegistered = true
        } catch (_: Throwable) {}
    }

    fun toggleTorch(ctx: Context): Boolean {
        val id = torchId ?: return false
        return try {
            (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, !_torch.value)
            true
        } catch (_: Throwable) { false }
    }

    fun dndOn(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** Перемикає «Не турбувати»; без доступу відкриває сторінку дозволу. Повертає новий стан або null. */
    fun toggleDnd(ctx: Context): Boolean? {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            start(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return null
        }
        val on = !dndOn(ctx)
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return on
    }

    fun internetPanel(ctx: Context) {
        start(ctx, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun bluetooth(ctx: Context) = start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun camera(ctx: Context) = start(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    /** Скріншот: через спеціальні можливості, інакше через Shizuku. */
    suspend fun screenshot(ctx: Context): Boolean {
        val a = AppAutomationService.instance
        if (a != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return a.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
        val r = try { SystemTools(ctx).run("screenshot") { "" } } catch (_: Throwable) { "" }
        return r.contains("зроблено")
    }

    fun start(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {}
    }
}
