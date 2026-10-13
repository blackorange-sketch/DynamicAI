package com.dynamicai.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.data.DockStorage
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val dockStorage = DockStorage(app)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _dockPkgs = MutableStateFlow<List<String>>(emptyList())
    val dockPkgs: StateFlow<List<String>> = _dockPkgs.asStateFlow()

    init {
        _dockPkgs.value = dockStorage.getDockApps()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _apps.value = repo.loadInstalledApps()
        }
    }

    fun launch(pkg: String) {
        repo.launchApp(pkg)
    }

    fun toggleDock(pkg: String) {
        dockStorage.toggle(pkg)
        _dockPkgs.value = dockStorage.getDockApps()
    }
}
