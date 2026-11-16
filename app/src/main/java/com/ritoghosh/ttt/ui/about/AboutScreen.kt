/**
 * AboutScreen — app name, one-line description, developer with GitHub link, and version.
 *
 * Depends on: ui/theme, Compose material3.
 * Must not: load anything from the network (the app has no INTERNET permission).
 * The GitHub link only asks Android to open the URL in the user's browser.
 */
package com.ritoghosh.ttt.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.theme.Spacing

/** The developer's GitHub profile. The visible text is in strings.xml (about_github). */
private const val GITHUB_URL = "https://github.com/ritog"

/** Static About page. [versionName] comes from the build (BuildConfig via AppContainer). */
@Composable
fun AboutScreen(versionName: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge)
        Column {
            Text(stringResource(R.string.about_developer), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { openLink(uriHandler, context) }) {
                Text(stringResource(R.string.about_github))
            }
        }
        Text(
            text = stringResource(R.string.about_version, versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.about_licence),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opens the GitHub page, or says so plainly if the phone has no app that can open links. */
private fun openLink(uriHandler: UriHandler, context: Context) {
    try {
        uriHandler.openUri(GITHUB_URL)
    } catch (error: IllegalArgumentException) {
        // Compose reports "no browser" as IllegalArgumentException on recent versions...
        showNoBrowserMessage(context)
    } catch (error: ActivityNotFoundException) {
        // ...and as ActivityNotFoundException on older ones.
        showNoBrowserMessage(context)
    }
}

private fun showNoBrowserMessage(context: Context) {
    Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
}
