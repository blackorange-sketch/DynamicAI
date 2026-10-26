package com.dynamicai.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppAutomationService : AccessibilityService() {
    companion object {
        private const val TAG = "AppAutomation"
        @Volatile var instance: AppAutomationService? = null

        fun isEnabled(): Boolean = instance != null

        fun clickText(text: String): Boolean = instance?.findAndClick(text, false) ?: false
        fun clickDescription(desc: String): Boolean = instance?.findAndClick(desc, true) ?: false
        fun clickPlayInYtMusic(): Boolean = instance?.clickPlayButton() ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun findAndClick(target: String, byDescription: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        return searchAndClick(root, target, byDescription)
    }

    private fun searchAndClick(node: AccessibilityNodeInfo, target: String, byDescription: Boolean): Boolean {
        val nodeText = if (byDescription) node.contentDescription?.toString() else node.text?.toString()
        if (nodeText != null && nodeText.contains(target, ignoreCase = true)) {
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent = parent.parent
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (searchAndClick(child, target, byDescription)) return true
        }
        return false
    }

    fun clickPlayButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val descriptions = listOf("Play", "Відтворити", "Грати", "Play song", "Play video", "Відтворити пісню")
        for (desc in descriptions) {
            if (searchAndClick(root, desc, true)) return true
        }
        val playIds = listOf(
            "com.google.android.apps.youtube.music:id/play_button",
            "com.google.android.apps.youtube.music:id/player_play_pause_button",
            "com.google.android.apps.youtube.music:id/play_pause_button",
            "com.google.android.apps.youtube.music:id/controls_play_pause"
        )
        for (id in playIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (n in nodes) {
                if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
        }
        return false
    }
}
