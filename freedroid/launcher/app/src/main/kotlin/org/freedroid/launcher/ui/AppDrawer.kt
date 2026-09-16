package org.freedroid.launcher.ui

import android.graphics.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.freedroid.launcher.R
import org.freedroid.launcher.core.AppEntry
import org.freedroid.launcher.core.LauncherLayout
import org.freedroid.launcher.data.IconCache

/**
 * Search field plus a responsive application grid.
 *
 * There is exactly one drawer implementation. The column count comes from
 * [LauncherLayout.appDrawerColumns], which the adaptive policy derived from the
 * window — there is no phone drawer and no tablet drawer to keep in sync.
 *
 * NOT YET COMPILED OR RUN.
 */
@Composable
internal fun AppDrawer(
    state: LauncherUiState,
    layout: LauncherLayout,
    iconCache: IconCache,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onLaunch: (AppEntry, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {

        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
            resultCount = state.results.size,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.isLoading && state.catalog.isEmpty() -> LoadingState()

            // Non-blank query with no matches. AppSearcher returns an empty list
            // for this case specifically so the UI can distinguish it from a
            // drawer that has not loaded yet.
            state.results.isEmpty() && state.query.isNotBlank() ->
                NoResultsState(query = state.query, modifier = Modifier.fillMaxSize())

            state.results.isEmpty() -> EmptyCatalogState(modifier = Modifier.fillMaxSize())

            else -> AppGrid(
                entries = state.results.map { it.entry },
                columns = layout.appDrawerColumns,
                iconCache = iconCache,
                onLaunch = onLaunch,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    resultCount: Int,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.semantics {
            // Screen readers announce how many apps match as the user types,
            // which is the one piece of feedback a sighted user gets for free
            // from watching the grid change.
            contentDescription = "$resultCount results"
        },
        singleLine = true,
        label = { Text(stringResource(R.string.search_label)) },
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                // Decorative: the field's own label already conveys the purpose,
                // and a second announcement would be noise.
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Enter dismisses the keyboard rather than submitting: results are
        // already filtered live, so there is nothing to submit.
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun AppGrid(
    entries: List<AppEntry>,
    columns: Int,
    iconCache: IconCache,
    onLaunch: (AppEntry, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        // Fixed count, supplied by the adaptive policy. Not Adaptive(minSize),
        // because the policy owns this decision and a second sizing rule here
        // could disagree with it.
        columns = GridCells.Fixed(columns),
        modifier = modifier.semantics { isTraversalGroup = true },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = entries,
            // A stable key stops the grid re-creating every tile when the
            // catalogue changes, and keeps scroll position across updates.
            key = { it.key },
        ) { entry ->
            AppTile(
                entry = entry,
                iconCache = iconCache,
                onLaunch = onLaunch,
            )
        }
    }
}

@Composable
private fun NoResultsState(query: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.drawer_no_results, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyCatalogState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.drawer_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
