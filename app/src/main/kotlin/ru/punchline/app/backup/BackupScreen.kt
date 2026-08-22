package ru.punchline.app.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.punchline.app.R

/**
 * Перенос на другой телефон. Экспорт — один файл, который можно положить
 * куда угодно; импорт — с обязательным осмотром архива перед применением.
 */
@Composable
fun BackupScreen(viewModel: BackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_ZIP)
    ) { uri -> uri?.let(viewModel::export) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::inspect) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.backup_explainer),
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = { createDocument.launch(defaultFileName()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is BackupState.Working,
        ) { Text(stringResource(R.string.backup_export)) }

        OutlinedButton(
            onClick = { openDocument.launch(arrayOf(MIME_ZIP, MIME_ANY)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is BackupState.Working,
        ) { Text(stringResource(R.string.backup_import)) }

        when (val current = state) {
            BackupState.Idle -> Unit
            BackupState.Working -> CircularProgressIndicator()

            is BackupState.Exported -> Info(stringResource(R.string.backup_exported))

            is BackupState.Inspected -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.backup_preview,
                            current.preview.blobsInArchive,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.backup_import_warning),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { viewModel.confirmImport(current.source) },
                        enabled = current.preview.readable,
                    ) { Text(stringResource(R.string.backup_import_confirm)) }
                    OutlinedButton(onClick = viewModel::dismiss) {
                        Text(stringResource(R.string.backup_cancel))
                    }
                }
            }

            is BackupState.Imported -> Info(stringResource(R.string.backup_imported))
            is BackupState.Failed -> Info(stringResource(R.string.backup_failed, current.reason))
        }
    }
}

@Composable
private fun Info(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text = text, modifier = Modifier.padding(16.dp))
    }
}

private const val MIME_ZIP = "application/zip"
private const val MIME_ANY = "application/octet-stream"

private fun defaultFileName(): String =
    "punchline-" + java.time.LocalDate.now() + ".zip"
