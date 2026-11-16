/**
 * ReportSection — "Export report" in Settings (SPEC 11): choose a range (All time, This month,
 * Custom) → the system "Save as" picker → the ViewModel writes the .xlsx.
 *
 * Depends on: SettingsViewModel, core/report (ReportRange, thisMonth), ui/components/TimeField.kt (DateField),
 * data/report/ReportService.kt (MIME type), Android Activity Result APIs.
 * Must not: build the report itself; it only collects the range and the destination.
 */
package com.ritoghosh.ttt.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.report.ReportRange
import com.ritoghosh.ttt.core.report.thisMonth
import com.ritoghosh.ttt.data.report.XLSX_MIME_TYPE
import com.ritoghosh.ttt.ui.components.DateField
import com.ritoghosh.ttt.ui.theme.MinTouchTarget
import com.ritoghosh.ttt.ui.theme.Spacing
import java.time.LocalDate

private enum class RangeChoice { ALL_TIME, THIS_MONTH, CUSTOM }

@Composable
fun ReportSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var isChoosingRange by rememberSaveable { mutableStateOf(false) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE)) { uri ->
        uri?.let(viewModel::onReportDestinationChosen)
    }
    OutlinedButton(onClick = { isChoosingRange = true }, enabled = !state.isExportingReport) {
        Text(stringResource(if (state.isExportingReport) R.string.report_exporting else R.string.report_export))
    }
    if (isChoosingRange) {
        ReportRangeDialog(
            today = viewModel.today(),
            onDismiss = { isChoosingRange = false },
            onConfirm = { range ->
                isChoosingRange = false
                viewModel.onReportRangeChosen(range)
                saveAs.launch(viewModel.suggestedReportFileName())
            },
        )
    }
}

@Composable
private fun ReportRangeDialog(today: LocalDate, onDismiss: () -> Unit, onConfirm: (ReportRange) -> Unit) {
    var choice by remember { mutableStateOf(RangeChoice.ALL_TIME) }
    var first by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var last by remember { mutableStateOf(today) }
    val customIsValid = !last.isBefore(first)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_range_title)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    RangeChoice.entries.forEach { option ->
                        RangeRow(option, selected = option == choice, onSelect = { choice = option })
                    }
                }
                if (choice == RangeChoice.CUSTOM) {
                    DateField(stringResource(R.string.report_from), first, onDateChange = { first = it })
                    DateField(stringResource(R.string.report_to), last, onDateChange = { last = it })
                    if (!customIsValid) {
                        Text(stringResource(R.string.report_range_backwards), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = choice != RangeChoice.CUSTOM || customIsValid,
                onClick = {
                    onConfirm(
                        when (choice) {
                            RangeChoice.ALL_TIME -> ReportRange.AllTime
                            RangeChoice.THIS_MONTH -> thisMonth(today)
                            RangeChoice.CUSTOM -> ReportRange.Days(first, last)
                        },
                    )
                },
            ) { Text(stringResource(R.string.report_export_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

/** The whole row is the touch target and is read as one radio button by TalkBack. */
@Composable
private fun RangeRow(option: RangeChoice, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(
                when (option) {
                    RangeChoice.ALL_TIME -> R.string.report_all_time
                    RangeChoice.THIS_MONTH -> R.string.report_this_month
                    RangeChoice.CUSTOM -> R.string.report_custom
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}
