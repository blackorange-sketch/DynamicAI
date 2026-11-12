package com.dynamicai.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.dynamicai.launcher.llm.ImageInput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Поділитися → DynamicAI»: приймає зображення (фото, знімок екрана) з інших програм і відкриває чат із ним.
 * Окрема експортована активність без інтерфейсу, щоб сам чат лишався закритим для сторонніх програм.
 */
class ShareImageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uri: Uri? = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra(Intent.EXTRA_STREAM) else null
        if (uri == null) { finish(); return }
        MainScope().launch {
            val jpeg = ImageInput.loadJpeg(this@ShareImageActivity, uri)
            if (jpeg != null) {
                val file = File(cacheDir, "shared_image.jpg")
                withContext(Dispatchers.IO) { file.writeBytes(jpeg) }
                startActivity(
                    Intent(this@ShareImageActivity, VoiceChatActivity::class.java)
                        .putExtra("image_path", file.absolutePath)
                )
            } else {
                Toast.makeText(this@ShareImageActivity, "Не вдалося прочитати зображення", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
