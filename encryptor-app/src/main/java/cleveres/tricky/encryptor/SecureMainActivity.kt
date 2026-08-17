package cleveres.tricky.encryptor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class SecureMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SecureEncryptorApp()
            }
        }
    }
}

@Composable
private fun SecureEncryptorApp() {
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (creating) {
                CreateScreen(onBack = { creating = false }, snackbar = snackbar)
            } else {
                VaultScreen(onCreate = { creating = true }, snackbar = snackbar)
            }
        }
    }
}

@Composable
private fun VaultScreen(
    onCreate: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val exportSuccess = stringResource(R.string.export_success)
    val exportFailed = stringResource(R.string.export_failed)
    val deleted = stringResource(R.string.keybox_deleted)
    val deleteFailed = stringResource(R.string.delete_failed)
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var exportTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val source = exportTarget
            exportTarget = null
            if (uri == null || source == null) return@rememberLauncherForActivityResult
            scope.launch {
                val success =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                VaultStore.export(source, output)
                            } ?: throw IOException("output unavailable")
                        }.isSuccess
                    }
                snackbar.showSnackbar(if (success) exportSuccess else exportFailed)
            }
        }

    LaunchedEffect(refresh) {
        files =
            withContext(Dispatchers.IO) {
                runCatching {
                    VaultStore.migrateLegacy(context)
                    VaultStore.list(context)
                }.getOrDefault(emptyList())
            }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vault_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = stringResource(R.string.vault_subtitle))
                }
                Text(text = stringResource(R.string.vault_summary, files.size, formatBytes(files.sumOf { it.length() })))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_encrypted_keybox))
            }
        },
    ) { padding ->
        if (files.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.empty_vault_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.empty_vault_body))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCreate) { Text(stringResource(R.string.create_keybox)) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.name }) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(file.length()), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = {
                                exportTarget = file
                                exportLauncher.launch(file.name)
                            },
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = stringResource(R.string.export_file, file.name))
                        }
                        IconButton(onClick = { deleteTarget = file }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_file, file.name))
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_keybox_title)) },
            text = { Text(stringResource(R.string.delete_keybox_message, file.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            val success = withContext(Dispatchers.IO) { runCatching { VaultStore.delete(file) }.getOrDefault(false) }
                            snackbar.showSnackbar(if (success) deleted else deleteFailed)
                            if (success) refresh++
                        }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateScreen(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val xmlFailed = stringResource(R.string.xml_use_failed)
    val encryptFailed = stringResource(R.string.encryption_failed)
    val encryptSuccess = stringResource(R.string.encrypted_success)
    val signingPublicKey = stringResource(R.string.signing_public_key)
    val publicKeyCopied = stringResource(R.string.public_key_copied)
    var author by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var xml by remember { mutableStateOf<ByteArray?>(null) }
    var xmlName by remember { mutableStateOf<String?>(null) }
    var publicKey by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var overwriteFilename by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            xml?.fill(0)
            password = ""
            confirmation = ""
        }
    }

    LaunchedEffect(Unit) {
        publicKey =
            withContext(Dispatchers.IO) {
                runCatching {
                    MobileCrypto.ensureSigningKey()
                    MobileCrypto.publicKeyBase64()
                }.getOrNull()
            }
    }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val selected =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use(::readBytes)
                                ?: throw IOException("input unavailable")
                        }.getOrNull()
                    }
                if (selected == null || selected.isEmpty()) {
                    selected?.fill(0)
                    snackbar.showSnackbar(xmlFailed)
                } else {
                    xml?.fill(0)
                    xml = selected
                    xmlName = displayName(context, uri) ?: "keybox.xml"
                }
            }
        }

    val authorValid = author.isNotBlank() && author.length <= 1024
    val passwordValid = password.length in 12..1024
    val confirmationValid = confirmation == password && confirmation.isNotEmpty()
    val canSave = authorValid && passwordValid && confirmationValid && xml != null && publicKey != null && !saving

    fun save(replace: Boolean) {
        val selectedXml = xml ?: return
        val filename = VaultStore.filenameFor(author)
        if (!replace && File(VaultStore.directory(context), filename).exists()) {
            overwriteFilename = filename
            return
        }
        saving = true
        val selectedAuthor = author
        val selectedPassword = password
        scope.launch {
            val success =
                withContext(Dispatchers.IO) {
                    runCatching {
                        MobileCrypto.encryptAndSave(
                            noBackupDirectory = context.noBackupFilesDir.absolutePath,
                            filename = filename,
                            author = selectedAuthor,
                            xmlUtf8 = selectedXml,
                            password = selectedPassword,
                        )
                    }.isSuccess
                }
            saving = false
            if (success) {
                selectedXml.fill(0)
                xml = null
                password = ""
                confirmation = ""
                snackbar.showSnackbar(encryptSuccess)
                onBack()
            } else {
                snackbar.showSnackbar(encryptFailed)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_keybox)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.security_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.security_body), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = author,
                onValueChange = { author = it.take(1024) },
                label = { Text(stringResource(R.string.author_label)) },
                supportingText = { if (!authorValid) Text(stringResource(R.string.author_required)) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = publicKey ?: stringResource(R.string.signing_key_unavailable),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        val key = publicKey ?: return@IconButton
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(signingPublicKey, key))
                        scope.launch { snackbar.showSnackbar(publicKeyCopied) }
                    },
                    enabled = publicKey != null,
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_public_key))
                }
            }

            OutlinedButton(
                onClick = { picker.launch(arrayOf("text/xml", "application/xml", "text/plain")) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(xmlName ?: stringResource(R.string.choose_xml))
            }
            Text(stringResource(R.string.xml_limit), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(1024) },
                label = { Text(stringResource(R.string.password)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                        )
                    }
                },
                supportingText = { if (!passwordValid) Text(stringResource(R.string.password_minimum)) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it.take(1024) },
                label = { Text(stringResource(R.string.confirm_password)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = { if (!confirmationValid) Text(stringResource(R.string.passwords_mismatch)) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = { save(false) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text(stringResource(R.string.encrypt_save))
                }
            }
            Text(stringResource(R.string.crypto_summary), style = MaterialTheme.typography.bodySmall)
        }
    }

    overwriteFilename?.let { filename ->
        AlertDialog(
            onDismissRequest = { overwriteFilename = null },
            title = { Text(stringResource(R.string.replace_keybox_title)) },
            text = { Text(stringResource(R.string.replace_keybox_message, filename)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        overwriteFilename = null
                        save(true)
                    },
                ) { Text(stringResource(R.string.replace)) }
            },
            dismissButton = {
                TextButton(onClick = { overwriteFilename = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun displayName(
    context: Context,
    uri: Uri,
): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)?.take(255)
        }
    }
    return null
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
        else -> "${bytes / (1024 * 1024)} MiB"
    }
