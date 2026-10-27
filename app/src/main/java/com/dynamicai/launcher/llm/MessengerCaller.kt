package com.dynamicai.launcher.llm

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.dynamicai.launcher.accessibility.AppAutomationService

class MessengerCaller(private val context: Context) {

    private fun delay(ms: Long, action: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed(action, ms)
    }

    private fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) { false }
    }

    fun callInTelegram(contactName: String): String {
        if (!AppAutomationService.isEnabled()) return "Сервіс доступності вимкнено."
        
        val tgPkg = "org.telegram.messenger"
        if (!launchApp(tgPkg)) return "Telegram не встановлено."
        
        // Чекаємо, поки відкриється
        delay(1500) {
            // 1. Відкриваємо пошук (зазвичай це кнопка з описом "Search" або "Пошук")
            val searchOpened = AppAutomationService.clickDescription("Search") || 
                               AppAutomationService.clickDescription("Пошук") ||
                               AppAutomationService.clickText("Пошук чатів")
            
            if (!searchOpened) return@delay

            delay(800) {
                // 2. Вводимо ім'я
                AppAutomationService.typeText(contactName)
                delay(800) {
                    // 3. Клікаємо на перший результат (зазвичай це текст з ім'ям)
                    val clicked = AppAutomationService.clickText(contactName)
                    
                    if (clicked) {
                        delay(1500) {
                            // 4. Натискаємо на трубку (опис "Call" або "Дзвінок")
                            val called = AppAutomationService.clickDescription("Call") || 
                                         AppAutomationService.clickDescription("Дзвінок")
                            if (!called) {
                                // Якщо не знайшли, шукаємо за ID (в Telegram він часто стабільний)
                                // Але для MVP спробуємо знайти хоч щось схоже на телефон
                            }
                        }
                    }
                }
            }
        }
        
        return "Спроба зателефонувати в Telegram: $contactName"
    }

    fun callInInstagram(contactName: String): String {
        if (!AppAutomationService.isEnabled()) return "Сервіс доступності вимкнено."
        
        val igPkg = "com.instagram.android"
        if (!launchApp(igPkg)) return "Instagram не встановлено."
        
        delay(2000) {
            // 1. Відкриваємо Direct (стрілочка вгорі або внизу)
            val directOpened = AppAutomationService.clickDescription("Direct") || 
                               AppAutomationService.clickDescription("Повідомлення") ||
                               AppAutomationService.clickDescription("Messenger")
            
            if (!directOpened) return@delay

            delay(1500) {
                // 2. Відкриваємо пошук у Direct
                AppAutomationService.clickDescription("Search") || AppAutomationService.clickText("Пошук")
                
                delay(800) {
                    // 3. Вводимо ім'я
                    AppAutomationService.typeText(contactName)
                    
                    delay(1000) {
                        // 4. Клікаємо на контакт
                        val clicked = AppAutomationService.clickText(contactName)
                        
                        if (clicked) {
                            delay(2000) {
                                // 5. Натискаємо на трубку
                                AppAutomationService.clickDescription("Call") || 
                                AppAutomationService.clickDescription("Аудіодзвінок") ||
                                AppAutomationService.clickDescription("Дзвінок")
                            }
                        }
                    }
                }
            }
        }
        
        return "Спроба зателефонувати в Instagram: $contactName"
    }
}
