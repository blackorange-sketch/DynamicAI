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
        fun searchAndPlay(query: String) { instance?.searchAndPlay(query) }
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

    fun clickFirstSearchResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        
        // Шукаємо контейнери списків (RecyclerView), які є на екрані пошуку
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
                    if (clickFirstClickableChild(firstItem)) {
                        return true
                    }
                }
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

    fun clickPlayButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        
        // 1. Шукаємо кнопку Play за описом (працює для міні-плеєра та головної)
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

    fun searchAndPlay(query: String) {
        val root = rootInActiveWindow ?: return
        
        // 1. Знаходимо і клікаємо іконку пошуку (якщо ми на головній)
        val searchNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.youtube.music:id/search_button")
        if (searchNodes.isNotEmpty() && searchNodes[0].isClickable) {
            searchNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Якщо ID не знайдено, шукаємо за описом
            searchAndClick(root, "Search", true)
        }
        
        // Даємо час на відкриття поля введення
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val newRoot = rootInActiveWindow ?: return@postDelayed
            
            // 2. Знаходимо поле введення
            val editTexts = newRoot.findAccessibilityNodeInfosByViewId("com.google.android.apps.youtube.music:id/search_edit_text")
            val searchField = if (editTexts.isNotEmpty()) editTexts[0] else {
                // Якщо не знайшли за ID, шукаємо будь-який EditText
                val allEditTexts = mutableListOf<AccessibilityNodeInfo>()
                findNodesByClassName(newRoot, "android.widget.EditText", allEditTexts)
                allEditTexts.firstOrNull()
            }
            
            if (searchField != null) {
                // 3. Вводимо текст
                val arguments = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                }
                searchField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                
                // 4. Натискаємо Enter (імітація)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val finalRoot = rootInActiveWindow ?: return@postDelayed
                    
                    // Шукаємо кнопку або елемент з текстом "Пісня" (щоб уникнути кліку на картку гурту)
                    val songNodes = finalRoot.findAccessibilityNodeInfosByText("Пісня")
                    if (songNodes.isNotEmpty()) {
                        // Клікаємо на батьківський елемент, щоб запустити пісню
                        var parent = songNodes[0].parent
                        while (parent != null) {
                            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                return@postDelayed
                            }
                            parent = parent.parent
                        }
                    }
                    
                    // Якщо "Пісня" не знайдено, просто клікаємо перший результат нижче пошуку
                    clickFirstSearchResult()
                    
                }, 3000) // 3 секунди на завантаження результатів
            }
        }, 1500) // 1.5 секунди на відкриття пошуку
    }
    
    private fun findNodesByClassName(node: AccessibilityNodeInfo, className: String, outList: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.toString() == className) outList.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findNodesByClassName(it, className, outList) }
        }
    }
}
