package com.dynamicai.launcher.macro

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle

/** Прозора активність: система відкриває її при дотику NFC-мітки, ми запускаємо відповідні макроси і закриваємось. */
class NfcTagActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        finish()
    }

    private fun handle(i: Intent?) {
        try {
            @Suppress("DEPRECATION")
            val tag = i?.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
            MacroDispatcher.fire(this, "nfc", mapOf("id" to hex(tag.id)))
        } catch (_: Throwable) {}
    }

    companion object {
        fun hex(b: ByteArray): String = b.joinToString("") { "%02X".format(it) }
    }
}
