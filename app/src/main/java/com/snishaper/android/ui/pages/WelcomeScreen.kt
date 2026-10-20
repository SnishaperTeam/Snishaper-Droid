package com.snishaper.android.ui.pages

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snishaper.android.R

private data class LanguageOption(val code: String, @StringRes val labelRes: Int)

/**
 * First-run language picker, mirroring `SniShaper-main/frontend/src/pages/Welcome.tsx`.
 * Its result is stored in DataStore; when a language exists the shell is shown instead.
 */
@Composable
fun WelcomeScreen(onStart: (String) -> Unit) {
    val languages = listOf(
        LanguageOption("zh", R.string.language_zh),
        LanguageOption("en", R.string.language_en),
        LanguageOption("ru", R.string.language_ru),
    )
    val systemLanguage = LocalConfiguration.current.locales[0].language
    val initialLanguage = when (systemLanguage) {
        "zh" -> "zh"
        "ru" -> "ru"
        else -> "en"
    }
    var selected by rememberSaveable { mutableStateOf(initialLanguage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_snishaper_logo),
            contentDescription = stringResource(id = R.string.cd_logo),
            modifier = Modifier.size(88.dp),
        )
        Spacer(modifier = Modifier.size(20.dp))
        Text(
            text = stringResource(id = R.string.welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(id = R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(28.dp))
        Text(
            text = stringResource(id = R.string.welcome_pick_language).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(12.dp))
        languages.forEach { language ->
            val active = selected == language.code
            Surface(
                onClick = { selected = language.code },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = androidx.compose.foundation.BorderStroke(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(id = language.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = language.code.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(24.dp))
        Button(
            onClick = { onStart(selected) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = stringResource(id = R.string.welcome_start),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
