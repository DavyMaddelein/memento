package com.memento.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Lifecycle of a backup export/import operation. The platform file picker/saver lives in the app
 * layer; this screen only renders the outcome.
 */
sealed interface BackupStatus {
    object Idle : BackupStatus
    object Exporting : BackupStatus
    object Importing : BackupStatus
    data class ExportReady(val bytes: ByteArray) : BackupStatus
    data class Imported(val imported: Int, val skipped: Int, val mediaRestored: Int) : BackupStatus
    data class Failed(val message: String) : BackupStatus
}

/**
 * Presentational export/import dialog. File selection is delegated to the app layer through
 * [onImportRequested]; bytes produced by the platform picker are fed back via [onImportBytes].
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ExportImportDialog(
    status: BackupStatus,
    onExport: () -> Unit,
    onImportRequested: () -> Unit,
    onImportBytes: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = status == BackupStatus.Exporting || status == BackupStatus.Importing

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    BackupStatus.Exporting -> {
                        ProgressRow(label = "Exporting backup…")
                    }

                    BackupStatus.Importing -> {
                        ProgressRow(label = "Importing backup…")
                    }

                    is BackupStatus.Imported -> {
                        Text("Import complete.")
                        Text("Imported: ${status.imported}")
                        Text("Skipped: ${status.skipped}")
                        Text("Media restored: ${status.mediaRestored}")
                    }

                    is BackupStatus.Failed -> {
                        Text(
                            text = status.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    BackupStatus.Idle, is BackupStatus.ExportReady -> {
                        Text("Export your mementos to a single .zip archive, or import one.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport, enabled = !busy) {
                Text("Export Backup (.zip)")
            }
        },
        dismissButton = {
            TextButton(onClick = onImportRequested, enabled = !busy) {
                Text("Import Backup (.zip)")
            }
        },
    )
}

@Composable
private fun ProgressRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label)
    }
}
