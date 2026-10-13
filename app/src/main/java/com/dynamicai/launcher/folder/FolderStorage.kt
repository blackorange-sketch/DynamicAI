package com.dynamicai.launcher.folder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FolderStorage(context: Context) {

    private val prefs = context.getSharedPreferences("dynamicai_folders", Context.MODE_PRIVATE)

    fun get(): List<Folder> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val pkgsArr = o.optJSONArray("pkgs") ?: JSONArray()
                val pkgs = (0 until pkgsArr.length()).map { pkgsArr.getString(it) }
                Folder(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    packageNames = pkgs
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(folder: Folder) {
        val list = get().toMutableList()
        list.removeAll { it.id == folder.id }
        list.add(folder)
        save(list)
    }

    fun create(name: String, initialPackages: List<String>): Folder {
        val f = Folder(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Папка" },
            packageNames = initialPackages
        )
        add(f)
        return f
    }

    fun remove(id: String) = save(get().filterNot { it.id == id })

    fun update(folder: Folder) = add(folder)

    fun addApp(folderId: String, pkg: String) {
        val list = get().map { f ->
            if (f.id == folderId && pkg !in f.packageNames) {
                f.copy(packageNames = f.packageNames + pkg)
            } else f
        }
        save(list)
    }

    fun removeApp(folderId: String, pkg: String) {
        val list = get().map { f ->
            if (f.id == folderId) f.copy(packageNames = f.packageNames - pkg)
            else f
        }
        // Прибираємо порожні папки
        save(list.filter { it.packageNames.isNotEmpty() })
    }

    fun folderOf(pkg: String): Folder? = get().firstOrNull { pkg in it.packageNames }

    private fun save(list: List<Folder>) {
        val arr = JSONArray()
        list.forEach { f ->
            val o = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                val pkgs = JSONArray()
                f.packageNames.forEach { pkgs.put(it) }
                put("pkgs", pkgs)
            }
            arr.put(o)
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
