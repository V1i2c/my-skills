package com.v1i2c.focusharbor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.v1i2c.focusharbor.platform.PermissionState
import com.v1i2c.focusharbor.ui.FocusHarborApp
import com.v1i2c.focusharbor.ui.FocusHarborTheme
import com.v1i2c.focusharbor.ui.FocusViewModel
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private val focusViewModel: FocusViewModel by lazy {
        val app = application as FocusApplication
        ViewModelProvider(this, FocusViewModel.Factory(app.repository, this))[FocusViewModel::class.java]
    }

    private var permissionRevision by mutableIntStateOf(0)

    private val backupExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { selected ->
            runCatching { focusViewModel.exportBackup() }
                .onSuccess { writeTextDocument(selected, it) }
                .onFailure { Toast.makeText(this, it.message ?: "Could not create the backup.", Toast.LENGTH_LONG).show() }
        }
    }

    private val backupImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readTextDocument(it)?.let(focusViewModel::importBackup) }
    }

    private val websiteCsvExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { selected ->
            runCatching { focusViewModel.exportWebsiteCsv() }
                .onSuccess { writeTextDocument(selected, it) }
                .onFailure { Toast.makeText(this, it.message ?: "Could not create the CSV.", Toast.LENGTH_LONG).show() }
        }
    }

    private val websiteCsvImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readTextDocument(it)?.let(focusViewModel::importWebsiteCsv) }
    }

    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRevision += 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleQuickBlockIntent(intent)
        setContent {
            FocusHarborTheme {
                FocusHarborApp(
                    viewModel = focusViewModel,
                permissionRevision = permissionRevision,
                onOpenAccessibilitySettings = { startActivity(PermissionState.accessibilitySettingsIntent()) },
                onOpenNotificationSettings = { startActivity(PermissionState.notificationSettingsIntent()) },
                onOpenAppSettings = { startActivity(PermissionState.appDetailsIntent(this)) },
                onRequestLocationPermissions = {
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    locationPermissionRequest.launch(permissions.toTypedArray())
                },
                onExportBackup = { backupExport.launch("focus-harbor-backup.json") },
                onImportBackup = { backupImport.launch(arrayOf("application/json", "text/plain")) },
                onExportWebsiteCsv = { websiteCsvExport.launch("focus-harbor-websites.csv") },
                onImportWebsiteCsv = { websiteCsvImport.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickBlockIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        permissionRevision += 1
    }

    private fun handleQuickBlockIntent(intent: Intent?) {
        if (intent?.action != ACTION_WIDGET_QUICK_BLOCK) return
        focusViewModel.handleQuickBlockSurfaceIntent()
        setIntent(Intent(intent).apply { action = null })
    }

    private fun writeTextDocument(uri: Uri, text: String) {
        runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open the selected file")
        }.onSuccess {
            Toast.makeText(this, "Saved locally.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Could not save the file.", Toast.LENGTH_LONG).show()
        }
    }

    private fun readTextDocument(uri: Uri): String? = runCatching {
        val output = ByteArrayOutputStream()
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_IMPORT_BYTES) { "The selected file is larger than 5 MiB." }
                output.write(buffer, 0, count)
            }
        } ?: error("Could not read the selected file")
        output.toByteArray().toString(Charsets.UTF_8)
    }.onFailure {
        Toast.makeText(this, it.message ?: "Could not read the selected file.", Toast.LENGTH_LONG).show()
    }.getOrNull()

    companion object {
        const val ACTION_WIDGET_QUICK_BLOCK = "com.v1i2c.focusharbor.action.WIDGET_QUICK_BLOCK"
        private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    }
}
