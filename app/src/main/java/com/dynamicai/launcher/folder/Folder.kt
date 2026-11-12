package com.dynamicai.launcher.folder

data class Folder(
    val id: String,
    val name: String,
    val packageNames: List<String>,
    /** Розумна папка: "recent" (нові), "frequent" (часті), "cat:<категорія>"; порожньо — звичайна. Вміст оновлюється сам. */
    val smart: String = ""
)
