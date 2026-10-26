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
        
}
