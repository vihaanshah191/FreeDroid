package org.freedroid.launcher.ui

import android.app.Application
import android.graphics.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.freedroid.launcher.core.AppCatalog
import org.freedroid.launcher.core.AppEntry
import org.freedroid.launcher.core.LaunchOutcome
import org.freedroid.launcher.core.LaunchRecovery
import org.freedroid.launcher.core.PackageChange
import org.freedroid.launcher.core.RecoveryAction
import org.freedroid.launcher.core.SearchResult
import org.freedroid.launcher.data.AppLauncher
import org.freedroid.launcher.data.IconCache
import org.freedroid.launcher.data.PackageAppSource

/** Everything the launcher UI needs to render, as one value. */
internal data class LauncherUiState(
    val catalog: AppCatalog = AppCatalog.EMPTY,
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = true,
    val message: UserMessage? = null,
)

/** A transient message shown to the user, e.g. after a failed launch. */
internal data class UserMessage(val text: String, val id: Long = System.nanoTime())

/**
 * Holds launcher state and mediates between the Android data layer and the pure
 * policy in `:core`.
 *
 * Note what is *not* here: no layout decisions. Column counts, pane mode and
 * hotseat placement all come from `LayoutPolicy`, derived from the window at
 * composition time. Keeping them out of the ViewModel is what stops layout from
 * quietly acquiring state that outlives a window resize.
 *
 * NOT YET COMPILED OR RUN.
 */
internal class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val source = PackageAppSource(application)
    private val launcher = AppLauncher(application)
    val iconCache = IconCache(application)

    private val catalog = MutableStateFlow(AppCatalog.EMPTY)
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(true)
    private val message = MutableStateFlow<UserMessage?>(null)

    val state: StateFlow<LauncherUiState> =
        combine(catalog, query, loading, message) { catalog, query, loading, message ->
            LauncherUiState(
                catalog = catalog,
                query = query,
                // Filtering runs in core and is cheap; see AppSearcherTest's
                // large-catalogue guard. If it ever stops being cheap this is the
                // line to move onto a background dispatcher.
                results = catalog.search(query),
                isLoading = loading,
                message = message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LauncherUiState(),
        )

    init {
        refreshCatalog()
        observePackageChanges()
    }

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
    }

    fun clearQuery() {
        query.value = ""
    }

    fun consumeMessage() {
        message.value = null
    }

    /**
     * Rebuilds the catalogue from the package manager.
     *
     * Called at startup and whenever a launch proves the snapshot stale. Not
     * called on every package event — those are applied as deltas, which avoids
     * a full re-query per app install.
     */
    fun refreshCatalog() {
        viewModelScope.launch {
            loading.value = true
            catalog.value = source.loadCatalog()
            loading.value = false
        }
    }

    private fun observePackageChanges() {
        viewModelScope.launch {
            source.packageChanges(this).collect { change ->
                // Icons can change without the entry changing, e.g. a themed-icon
                // update, so evict on any change to the package.
                iconCache.evictPackage(change.packageName)
                catalog.value = catalog.value.apply(change)
            }
        }
    }

    fun launch(entry: AppEntry, sourceBounds: Rect?) {
        viewModelScope.launch {
            when (val outcome = launcher.launch(entry, sourceBounds)) {
                is LaunchOutcome.Success -> Unit
                else -> handleFailure(outcome)
            }
        }
    }

    private fun handleFailure(outcome: LaunchOutcome) {
        val context = getApplication<Application>()
        when (LaunchRecovery.actionFor(outcome)) {
            RecoveryAction.REFRESH_CATALOG -> {
                message.value = UserMessage(
                    context.getString(org.freedroid.launcher.R.string.launch_failed_missing),
                )
                refreshCatalog()
            }

            RecoveryAction.NOTIFY_USER ->
                message.value = UserMessage(
                    context.getString(org.freedroid.launcher.R.string.launch_failed_generic),
                )

            RecoveryAction.NONE -> Unit
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
