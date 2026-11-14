package io.github.kemurixd.lurk.featureui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.LurkAvatar
import io.github.kemurixd.lurk.designsystem.component.PlatformBadge
import io.github.kemurixd.lurk.designsystem.component.PlatformLogoLeading
import io.github.kemurixd.lurk.designsystem.component.labelRes
import io.github.kemurixd.lurk.feature.accounts.AccountRow
import io.github.kemurixd.lurk.feature.accounts.AccountStatus
import io.github.kemurixd.lurk.feature.accounts.AccountsIntent

private val RowPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
private val AvatarSize = 40.dp

/**
 * One platform in the Accounts card (Figma AccountRow): who is signed in, and one button. Signed out offers Sign in,
 * signed in offers Sign out, and an expired session offers Sign in again beside Sign out.
 */
@Composable
internal fun AccountRowItem(
    row: AccountRow,
    onIntent: (AccountsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = row.platform
    val content = rowContent(row)
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp).padding(RowPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Leading(platform, row.status)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = content.headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = content.supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = content.supportingColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val action = Modifier.testTag(SettingsTestTags.action(platform))
        when (row.status) {
            AccountStatus.SignedOut -> {
                SignInButton(platform, R.string.sign_in, { onIntent(AccountsIntent.SignIn(platform)) }, action)
            }

            is AccountStatus.Expired -> {
                // The stored session is kept until the user replaces it or removes it here.
                TextButton(
                    onClick = { onIntent(AccountsIntent.SignOut(platform)) },
                    modifier = Modifier.testTag(SettingsTestTags.signOut(platform)),
                ) {
                    Text(stringResource(R.string.sign_out))
                }
                SignInButton(platform, R.string.sign_in_again, { onIntent(AccountsIntent.SignIn(platform)) }, action)
            }

            is AccountStatus.SignedIn -> {
                TextButton(onClick = { onIntent(AccountsIntent.SignOut(platform)) }, modifier = action) {
                    Text(stringResource(R.string.sign_out))
                }
            }
        }
    }
}

/** A tonal button that leads with the platform's logo before its [label]. */
@Composable
private fun SignInButton(
    platform: Platform,
    @StringRes label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        PlatformLogoLeading(platform)
        Text(stringResource(label))
    }
}

private class RowContent(
    val headline: String,
    val supporting: String,
    val supportingColor: Color,
)

@Composable
private fun rowContent(row: AccountRow): RowContent {
    val colors = MaterialTheme.colorScheme
    val platformName = stringResource(row.platform.labelRes)
    return when (val status = row.status) {
        AccountStatus.SignedOut -> {
            RowContent(platformName, stringResource(R.string.status_signed_out), colors.onSurfaceVariant)
        }

        is AccountStatus.SignedIn -> {
            RowContent(
                status.displayName,
                stringResource(R.string.status_signed_in, platformName),
                colors.onSurfaceVariant,
            )
        }

        is AccountStatus.Expired -> {
            // Said in plain colour here: the banner above the accounts carries the warning.
            RowContent(status.displayName, stringResource(R.string.status_expired), colors.onSurfaceVariant)
        }
    }
}

/** The account's avatar with the platform badge on its corner, or the badge alone while signed out. */
@Composable
private fun Leading(
    platform: Platform,
    status: AccountStatus,
) {
    Box(Modifier.size(AvatarSize)) {
        when (status) {
            AccountStatus.SignedOut -> {
                Box(
                    Modifier
                        .size(
                            AvatarSize,
                        ).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { PlatformBadge(platform) }
            }

            is AccountStatus.SignedIn -> {
                LurkAvatar(status.avatarUrl, status.displayName, size = AvatarSize)
                PlatformBadge(platform, Modifier.align(Alignment.BottomEnd))
            }

            is AccountStatus.Expired -> {
                LurkAvatar(null, status.displayName, size = AvatarSize)
                PlatformBadge(platform, Modifier.align(Alignment.BottomEnd))
            }
        }
    }
}
