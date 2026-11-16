/**
 * SettingsScreen — pomodoro lengths (− value +), theme (system / light / dark), backup and the
 * report export (SPEC 9.5). "Nothing else."
 *
 * Depends on: SettingsViewModel, BackupSection, ReportSection, data/backup/RestoreService.kt, core/settings, ui/theme.
 * Must not: hold settings itself; every change goes straight to SettingsRepository.
 */
package com.ritoghosh.ttt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.settings.NumberSetting
import com.ritoghosh.ttt.core.settings.SettingGroup
import com.ritoghosh.ttt.core.settings.ThemeChoice
import com.ritoghosh.ttt.data.backup.RestoreService
import com.ritoghosh.ttt.ui.theme.MinTouchTarget
import com.ritoghosh.ttt.ui.theme.Spacing
import com.ritoghosh.ttt.ui.theme.TimeLabelTextStyle

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, restoreService: RestoreService) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }
    if (!state.isLoaded) return
    Box(Modifier.fillMaxSize()) {
        SettingsContent(state, viewModel, restoreService)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SettingsContent(state: SettingsUiState, viewModel: SettingsViewModel, restoreService: RestoreService) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SectionHeader(stringResource(R.string.settings_pomodoro))
        NumberSetting.entries.filter { it.group == SettingGroup.POMODORO }.forEach { setting ->
            StepperRow(
                label = numberSettingLabel(setting),
                value = state.valueOf(setting),
                range = setting.range,
                onStep = { delta -> viewModel.onStep(setting, delta) },
            )
        }
        SectionHeader(stringResource(R.string.settings_theme))
        Column(Modifier.selectableGroup()) {
            ThemeChoice.entries.forEach { choice ->
                ThemeRow(choice, selected = choice == state.theme, onSelect = { viewModel.onThemeSelected(choice) })
            }
        }
        SectionHeader(stringResource(R.string.settings_backup))
        BackupSection(state, viewModel, restoreService)
        SectionHeader(stringResource(R.string.settings_report))
        ReportSection(state, viewModel)
    }
}

@Composable
private fun messageText(message: SettingsMessage): String = when (message) {
    SettingsMessage.BACKUP_SAVED -> stringResource(R.string.backup_saved)
    SettingsMessage.BACKUP_FAILED -> stringResource(R.string.backup_failed)
    SettingsMessage.REPORT_SAVED -> stringResource(R.string.report_saved)
    SettingsMessage.REPORT_FAILED -> stringResource(R.string.report_failed)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xs),
    )
}

@Composable
internal fun StepperRow(label: String, value: Int, range: IntRange, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouchTarget), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onStep(-1) }, enabled = value > range.first) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.cd_decrease, label))
        }
        Text(
            text = value.toString(),
            style = TimeLabelTextStyle,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp),
        )
        IconButton(onClick = { onStep(1) }, enabled = value < range.last) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.cd_increase, label))
        }
    }
}

/** The whole row is the touch target and is read as one radio button by TalkBack. */
@Composable
private fun ThemeRow(choice: ThemeChoice, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = themeLabel(choice),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

@Composable
private fun numberSettingLabel(setting: NumberSetting): String = when (setting) {
    NumberSetting.WORK_MINUTES -> stringResource(R.string.setting_work_minutes)
    NumberSetting.SHORT_BREAK_MINUTES -> stringResource(R.string.setting_short_break_minutes)
    NumberSetting.LONG_BREAK_MINUTES -> stringResource(R.string.setting_long_break_minutes)
    NumberSetting.WORK_PHASES_BEFORE_LONG_BREAK -> stringResource(R.string.setting_work_phases)
    NumberSetting.AUTO_BACKUP_KEEP -> stringResource(R.string.setting_auto_backup_keep)
}

@Composable
private fun themeLabel(choice: ThemeChoice): String = when (choice) {
    ThemeChoice.SYSTEM -> stringResource(R.string.theme_system)
    ThemeChoice.LIGHT -> stringResource(R.string.theme_light)
    ThemeChoice.DARK -> stringResource(R.string.theme_dark)
}
