package io.github.kemurixd.lurk.featureui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.ErrorBanner
import io.github.kemurixd.lurk.designsystem.component.SectionHeader
import io.github.kemurixd.lurk.designsystem.component.labelRes
import io.github.kemurixd.lurk.feature.accounts.AccountStatus
import io.github.kemurixd.lurk.feature.accounts.AccountsEffect
import io.github.kemurixd.lurk.feature.accounts.AccountsIntent
import io.github.kemurixd.lurk.feature.accounts.AccountsUiState
import io.github.kemurixd.lurk.feature.accounts.AccountsViewModel
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel

private val BodyMaxWidth = 640.dp
private val CardShape = RoundedCornerShape(20.dp)
private val ContentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)

/** Gets the [AccountsViewModel] and hands its state and effects to [SettingsScreen]. */
@Composable
fun SettingsEntry(
    onOpenLogin: (Platform) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onOpenLogin = onOpenLogin,
        modifier = modifier,
    )
}

/**
 * Settings, P7a: only the Accounts section (Figma 09 Settings → Accounts). One row per platform in a tonal card, each
 * with its sign-in state and one button. Sign out asks first, in a dialog opened by [AccountsEffect.AskConfirmSignOut].
 *
 * Effects are collected while the screen is started, so an effect sent meanwhile is handled once when it returns.
 */
@Composable
fun SettingsScreen(
    state: AccountsUiState,
    effects: Flow<AccountsEffect>,
    onIntent: (AccountsIntent) -> Unit,
    onOpenLogin: (Platform) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf<Platform?>(null) }
    HandleAccountsEffects(effects, onOpenLogin = onOpenLogin, onAskConfirm = { confirming = it })
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ContentPadding),
    ) {
        state.rows.filter { it.status is AccountStatus.Expired }.forEach { row -> ExpiredBanner(row.platform) }
        Column(Modifier.widthIn(max = BodyMaxWidth).fillMaxWidth().testTag(SettingsTestTags.ACCOUNTS)) {
            SectionHeader(stringResource(R.string.section_accounts), Modifier.padding(bottom = 8.dp))
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    state.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AccountRowItem(row, onIntent)
                    }
                }
            }
        }
    }
    confirming?.let { platform ->
        ConfirmSignOutDialog(
            platform = platform,
            onConfirm = {
                confirming = null
                onIntent(AccountsIntent.ConfirmSignOut(platform))
            },
            onDismiss = { confirming = null },
        )
    }
}

/** An expired session, said once above the accounts, with Sign in again (spec §5.2). */
@Composable
private fun ExpiredBanner(platform: Platform) {
    val name = stringResource(platform.labelRes)
    ErrorBanner(
        title = stringResource(R.string.expired_banner_title, name),
        error = LurkError.AuthExpired,
        onRetry = {},
        platform = platform,
        reason = stringResource(R.string.expired_banner_body, name),
        modifier = Modifier.widthIn(max = BodyMaxWidth).padding(bottom = 8.dp),
    )
}

@Composable
private fun HandleAccountsEffects(
    effects: Flow<AccountsEffect>,
    onOpenLogin: (Platform) -> Unit,
    onAskConfirm: (Platform) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val openLogin by rememberUpdatedState(onOpenLogin)
    val askConfirm by rememberUpdatedState(onAskConfirm)
    LaunchedEffect(effects, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { effect ->
                when (effect) {
                    is AccountsEffect.OpenLogin -> openLogin(effect.platform)
                    is AccountsEffect.AskConfirmSignOut -> askConfirm(effect.platform)
                }
            }
        }
    }
}

@Composable
private fun ConfirmSignOutDialog(
    platform: Platform,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = stringResource(platform.labelRes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_out_title, name)) },
        text = { Text(stringResource(R.string.sign_out_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(SettingsTestTags.CONFIRM_SIGN_OUT)) {
                Text(stringResource(R.string.sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
