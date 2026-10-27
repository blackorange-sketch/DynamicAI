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
        fun clickFirstSearchResult(): Boolean = instance?.clickFirstSearchResult() ?: false
        fun clickPlayButton(): Boolean = instance?.clickPlayButton() ?: false
        fun getScreenText(): String = instance?.readScreenText() ?: ""
        fun typeText(text: String): Boolean = instance?.inputText(text) ?: false
        fun pressBack(): Boolean = instance?.goBack() ?: false
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

    // --- Базові функції пошуку та кліку ---

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

    // --- Специфічні функції для YT Music ---

    fun clickFirstSearchResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        val listIds = listOf(
            "com.google.android.apps.youtube.music:id/recycler_view",
            "com.google.android.apps.youtube.music:id/item_recycler_view",
            "com.google.android.apps.youtube.music:id/results_recycler_view"
        )
        
        for (id in listIds) {
            val lists = root.findAccessibilityNodeInfosByViewId(id)
            for (list in lists) {
                if (list.childCount > 0) {
                    val firstItem = list.getChild(0)
                    if (clickFirstClickableChild(firstItem)) return true
                }
            }
        }
        return false
    }

    fun clickPlayButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        
        // 1. Шукаємо кнопку Play за описом
        val descriptions = listOf("Play", "Відтворити", "Грати", "Play song", "Відтворити пісню", "Відтворити все", "Resume")
        for (desc in descriptions) {
            if (searchAndClick(root, desc, true)) return true
        }
        
        // 2. Шукаємо за стандартними ID
        val playIds = listOf(
            "com.google.android.apps.youtube.music:id/play_button",
            "com.google.android.apps.youtube.music:id/player_play_pause_button",
            "com.google.android.apps.youtube.music:id/play_pause_button",
            "com.google.android.apps.youtube.music:id/controls_play_pause",
            "com.google.android.apps.youtube.music:id/play_pause"
        )
        for (id in playIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (n in nodes) {
                if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
        }
        return false
    }
    
    private fun clickFirstClickableChild(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickFirstClickableChild(child)) return true
        }
        return false
    }

    private fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        extractAllText(root, sb)
        return sb.toString().trim()
    }

    private fun extractAllText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            sb.append(text).append("\n")
        }
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank() && desc != text) {
            sb.append(desc).append("\n")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractAllText(child, sb)
        }
    }

    private fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editText = findFirstEditText(root) ?: return false
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findFirstEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("EditText") == true && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditText(child)
            if (found != null) return found
        }
        return null
    }

    private fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
