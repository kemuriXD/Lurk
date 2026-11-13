package io.github.kemurixd.lurk.featureui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.CategoryTile
import io.github.kemurixd.lurk.designsystem.component.ChannelRow
import io.github.kemurixd.lurk.designsystem.component.EmptyState
import io.github.kemurixd.lurk.designsystem.component.ErrorBanner
import io.github.kemurixd.lurk.designsystem.component.ErrorState
import io.github.kemurixd.lurk.designsystem.component.PlatformFilterChips
import io.github.kemurixd.lurk.designsystem.component.SectionHeader
import io.github.kemurixd.lurk.designsystem.component.StreamCardPlaceholder
import io.github.kemurixd.lurk.designsystem.component.labelRes
import io.github.kemurixd.lurk.designsystem.time.rememberNowState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.key
import io.github.kemurixd.lurk.feature.search.SearchIntent
import io.github.kemurixd.lurk.feature.search.SearchPhase
import io.github.kemurixd.lurk.feature.search.SearchType
import io.github.kemurixd.lurk.feature.search.SearchUiState
import io.github.kemurixd.lurk.feature.search.SearchViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import io.github.kemurixd.lurk.designsystem.R as DesignR

private const val PLACEHOLDER_COUNT = 3
private const val CATEGORY_TILE = "category_tile"
private const val CHANNEL_ROW = "channel_row"
private const val PLACEHOLDER = "placeholder"
private val FieldShape = 28.dp
private val FieldPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
private val CardPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
private val TileWidth = 116.dp

/** The smallest tile in the wrapping grid, as Browse's category grid uses. */
private val TileGridMin = 104.dp

/** Gets the [SearchViewModel] and hands the state to [SearchScreen]. */
@Composable
fun SearchEntry(
    onOpenChannel: (ChannelRef) -> Unit,
    onOpenCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenChannel = onOpenChannel,
        onOpenCategory = onOpenCategory,
        modifier = modifier,
    )
}

/**
 * The Search screen (Figma 04 Search). The state decides which Figma frame shows:
 * `EMPTY` the invitation, `TYPING` the progress bar with skeleton cards below any rows already there, `RESULTS` the
 * Channels and Categories sections with one error banner per failed platform (one full-screen error when all failed), and `NO_RESULTS` the empty state.
 *
 * The field keeps its own text so fast typing never races the state flow; [SearchUiState.query] only seeds it.
 * Results sit under the field with the keyboard's inset applied, so the keyboard never covers them.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onIntent: (SearchIntent) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    onOpenCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    // A new search starts at the top, whatever the previous list was scrolled to.
    val query = state.query.trim()
    LaunchedEffect(query) { gridState.scrollToItem(0) }
    // One ticking clock for every offline row, instead of a state and a timer started by each row that scrolls in. Only
    // the offline rows read it, so a tick redraws their text and leaves the screen and the live rows alone.
    val now = rememberNowState()
    Column(modifier.fillMaxSize().imePadding()) {
        SearchField(initial = state.query, onIntent = onIntent)
        if (state.phase == SearchPhase.EMPTY) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.search_empty_title),
                    body = stringResource(R.string.search_empty_body),
                    action = null,
                    icon = DesignR.drawable.ic_search,
                )
            }
        } else {
            Progress(searching = state.phase == SearchPhase.TYPING)
            TypeSwitch(selected = state.type, onSelect = { onIntent(SearchIntent.SelectType(it)) })
            PlatformFilterChips(
                selected = state.filter,
                onSelect = { onIntent(SearchIntent.SelectFilter(it)) },
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (state.phase == SearchPhase.NO_RESULTS) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(state.type.noResultsTitle(), state.query.trim()),
                        body = stringResource(R.string.no_results_body),
                        action = null,
                        icon = DesignR.drawable.ic_search,
                    )
                }
            } else {
                val categoriesOnly = state.type == SearchType.CATEGORIES
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (categoriesOnly) TileGridMin else 300.dp),
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag(SearchTestTags.LIST),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    results(state, now, onIntent, onOpenChannel, onOpenCategory)
                }
            }
        }
    }
}

@StringRes
private fun SearchType.noResultsTitle(): Int =
    when (this) {
        SearchType.ALL -> R.string.no_results_title
        SearchType.CHANNELS -> R.string.no_channels_title
        SearchType.CATEGORIES -> R.string.no_categories_title
    }

@Composable
private fun SearchField(
    initial: String,
    onIntent: (SearchIntent) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val keyboard = LocalSoftwareKeyboardController.current
    val placeholder = stringResource(R.string.search_placeholder)
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onIntent(SearchIntent.QueryChanged(it))
        },
        modifier = Modifier.fillMaxWidth().padding(FieldPadding).testTag(SearchTestTags.FIELD),
        placeholder = { Text(placeholder) },
        trailingIcon =
            if (text.isNotEmpty()) {
                {
                    IconButton(
                        onClick = {
                            text = ""
                            onIntent(SearchIntent.Clear)
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            painterResource(DesignR.drawable.ic_close),
                            contentDescription = stringResource(R.string.cd_clear_search),
                        )
                    }
                }
            } else {
                null
            },
        singleLine = true,
        shape = RoundedCornerShape(FieldShape),
        // The search already runs while typing, so the keyboard's action only gets out of the way.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
    )
}

/** All / Channels / Categories, in the same segmented style as Browse's Top Live / Categories switch. */
@Composable
private fun TypeSwitch(
    selected: SearchType,
    onSelect: (SearchType) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        val types = SearchType.entries
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                modifier = Modifier.testTag(type.tag()).heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        when (type) {
                            SearchType.ALL -> R.string.type_all
                            SearchType.CHANNELS -> R.string.type_channels
                            SearchType.CATEGORIES -> R.string.type_categories
                        },
                    ),
                )
            }
        }
    }
}

private fun SearchType.tag(): String =
    when (this) {
        SearchType.ALL -> SearchTestTags.TYPE_ALL
        SearchType.CHANNELS -> SearchTestTags.TYPE_CHANNELS
        SearchType.CATEGORIES -> SearchTestTags.TYPE_CATEGORIES
    }

/** The 4 dp strip under the field. It keeps its height when idle so the chips don't jump. */
@Composable
private fun Progress(searching: Boolean) {
    val label = stringResource(R.string.cd_searching)
    Box(Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 16.dp)) {
        if (searching) {
            LinearProgressIndicator(
                modifier =
                    Modifier.fillMaxSize().testTag(SearchTestTags.PROGRESS).semantics {
                        contentDescription =
                            label
                    },
            )
        }
    }
}

private fun LazyGridScope.results(
    state: SearchUiState,
    now: State<Instant>,
    onIntent: (SearchIntent) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    onOpenCategory: (Category) -> Unit,
) {
    // A lazy list holds on to its first visible item when rows land above it. Categories often answer before channels,
    // so without this anchor the new channels would push the list down to the categories. The anchor never moves, so
    // a list the user has not scrolled stays at the top while results land.
    fullWidth("top_anchor") { Spacer(Modifier.height(1.dp)) }
    val failures =
        state.slices.mapNotNull { (platform, slice) ->
            (slice as? PlatformSlice.Failed)?.let {
                platform to
                    it.error
            }
        }
    val nothingFound = state.channels.isEmpty() && state.categories.isEmpty()
    if (failures.isNotEmpty() && failures.size == state.slices.size && nothingFound) {
        // Every platform failed: one full-screen state, whose Retry asks each of them again.
        val (platform, error) = failures.first()
        fullWidth("failed") {
            ErrorState(
                title = stringResource(R.string.search_error_title),
                error = error,
                onRetry = { failures.forEach { (failed, _) -> onIntent(SearchIntent.Retry(failed)) } },
                platform = platform,
            )
        }
    } else {
        failures.forEach { (platform, error) ->
            fullWidth("error:${platform.name}") {
                ErrorBanner(
                    title = stringResource(DesignR.string.error_load_platform, stringResource(platform.labelRes)),
                    error = error,
                    onRetry = { onIntent(SearchIntent.Retry(platform)) },
                    platform = platform,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
    if (state.channels.isNotEmpty()) {
        fullWidth("channels_header") { SectionHeader(stringResource(R.string.section_channels)) }
        items(
            state.channels,
            key = { it.key },
            span = { GridItemSpan(maxLineSpan) },
            contentType = { CHANNEL_ROW },
        ) { channel ->
            // Composed rows only: the lazy grid composes what is on screen, so off-screen rows never cost a lookup, and
            // a row that scrolls away before its turn lets go of it.
            if (channel.live == null && channel.lastStream?.startedAt == null) {
                DisposableEffect(channel.ref) {
                    onIntent(SearchIntent.OfflineRowShown(channel.ref))
                    onDispose { onIntent(SearchIntent.OfflineRowHidden(channel.ref)) }
                }
            }
            ChannelRow(
                channel = channel,
                onClick = { onOpenChannel(channel.ref) },
                showLiveRing = false,
                now = { now.value },
            )
        }
    }
    if (state.categories.isNotEmpty()) {
        fullWidth("categories_header") { SectionHeader(stringResource(R.string.section_categories)) }
        if (state.type == SearchType.CATEGORIES) {
            // Only categories: a wrapping grid of tiles that fills the screen.
            items(
                state.categories,
                key = { "${it.platform.name}:${it.id}" },
                contentType = { CATEGORY_TILE },
            ) { category ->
                CategoryTile(
                    category = category,
                    onClick = { onOpenCategory(category) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        } else {
            fullWidth("categories") { Categories(state.categories, onOpenCategory) }
        }
    }
    if (state.phase == SearchPhase.TYPING) {
        items(PLACEHOLDER_COUNT, key = { "placeholder:$it" }, contentType = { PLACEHOLDER }) {
            StreamCardPlaceholder(Modifier.padding(CardPadding))
        }
    }
}

@Composable
private fun Categories(
    categories: List<Category>,
    onOpen: (Category) -> Unit,
) {
    LazyRow(
        modifier = Modifier.testTag(SearchTestTags.CATEGORY_ROW),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(categories, key = { "${it.platform.name}:${it.id}" }) { category ->
            CategoryTile(category = category, onClick = { onOpen(category) }, modifier = Modifier.width(TileWidth))
        }
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) {
    // The key's kind (before any colon) is the content type, so rows of one kind reuse each other's composition.
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key.substringBefore(':')) { content() }
}
