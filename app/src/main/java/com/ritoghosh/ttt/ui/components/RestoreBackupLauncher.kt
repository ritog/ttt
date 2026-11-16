/**
 * RestoreBackupLauncher — the "Restore backup" flow, shared by Settings and the empty Todo screen (SPEC 8.3):
 * check the app is empty → pick a file → RestoreService checks it → restart the app to install it.
 *
 * Depends on: data/backup/RestoreService.kt, MainActivity, Android Activity Result APIs, Compose material3.
 * Must not: install the file itself. The restart hands it to TttApplication.onCreate, which swaps
 * the database file before anything opens it, so no screen ever reads a half-replaced database.
 */
package com.ritoghosh.ttt.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.MainActivity
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.data.backup.RestoreCheck
import com.ritoghosh.ttt.data.backup.RestoreProblem
import com.ritoghosh.ttt.data.backup.RestoreService
import kotlinx.coroutines.launch

/**
 * Returns the action to start a restore. Problems appear as toasts; after a successful restore
 * MainActivity says "Backup restored" once the app has restarted.
 */
@Composable
fun rememberRestoreBackupLauncher(restoreService: RestoreService): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = restoreService.prepare(uri)) {
                is RestoreCheck.Ready -> restartApp(context)
                is RestoreCheck.Refused -> toast(context, problemMessage(result.problem))
            }
        }
    }
    return {
        scope.launch {
            // Say "empty app only" before the picker, not after the user has found the file.
            if (restoreService.isAppEmpty()) {
                picker.launch(arrayOf("*/*"))
            } else {
                toast(context, R.string.restore_not_empty)
            }
        }
    }
}

/** The small "Restore a backup" text link on the empty Todo screen (SPEC 8.3). */
@Composable
fun RestoreBackupLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) { Text(stringResource(R.string.todo_restore_link)) }
}

@StringRes
private fun problemMessage(problem: RestoreProblem): Int = when (problem) {
    RestoreProblem.NOT_EMPTY -> R.string.restore_not_empty
    RestoreProblem.UNREADABLE -> R.string.restore_unreadable
    RestoreProblem.NOT_A_BACKUP -> R.string.restore_not_a_backup
    RestoreProblem.DAMAGED -> R.string.restore_damaged
    RestoreProblem.NEWER_VERSION -> R.string.restore_newer_version
}

private fun toast(context: Context, @StringRes message: Int) {
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
}

/**
 * Starts a fresh MainActivity in a new task and ends this process. On the next start
 * TttApplication installs the checked backup, then everything opens on the restored data.
 */
private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    context.findActivity()?.finishAffinity()
    Runtime.getRuntime().exit(0)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
