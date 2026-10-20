package com.snishaper.android.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snishaper.android.BuildConfig
import com.snishaper.android.R
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.SectionHeader
import com.snishaper.android.ui.components.SettingRow
import com.snishaper.android.ui.components.SkeletonNote
import com.snishaper.android.ui.components.StatusPill
import com.snishaper.android.ui.components.rememberToast

/**
 * About skeleton (`About.tsx:206-358`): hero (logo/version/channel), description, feature rows,
 * community rows and the copyright footer. App-update UI is removed (store-managed updates).
 */
@Composable
fun AboutScreen(contentPadding: PaddingValues) {
    val comingSoon = stringResource(id = R.string.common_coming_soon)
    val toast = rememberToast()

    PageColumn(contentPadding = contentPadding) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_snishaper_logo),
                    contentDescription = stringResource(id = R.string.cd_logo),
                    modifier = Modifier.size(72.dp),
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(id = R.string.about_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(
                        text = "${stringResource(id = R.string.about_version)} ${BuildConfig.VERSION_NAME}",
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    StatusPill(
                        text = stringResource(id = R.string.about_channel_stable),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Text(
                text = stringResource(id = R.string.about_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }

        SectionHeader(titleRes = R.string.about_features, icon = Icons.Outlined.Bolt)
        SettingRow(titleRes = R.string.about_feature_ech, icon = Icons.Outlined.Lock)
        SettingRow(titleRes = R.string.about_feature_tlsfrag, icon = Icons.Outlined.Settings)
        SettingRow(titleRes = R.string.about_feature_dns, icon = Icons.Outlined.Public)

        SectionHeader(titleRes = R.string.about_community, icon = Icons.Outlined.Link)
        SettingRow(
            titleRes = R.string.about_website,
            icon = Icons.Outlined.Public,
            trailing = {
                TextButton(onClick = { toast(comingSoon) }) {
                    Text(stringResource(id = R.string.about_website))
                }
            },
        )
        SettingRow(
            titleRes = R.string.about_github,
            icon = Icons.Outlined.Link,
            trailing = {
                TextButton(onClick = { toast(comingSoon) }) {
                    Text("GitHub")
                }
            },
        )

        SkeletonNote()
        Text(
            text = stringResource(id = R.string.about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
