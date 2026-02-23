package cleveres.tricky.encryptor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EncryptorApp()
        }
    }
}

@Composable
fun EncryptorApp() {
    val context = LocalContext.current
    var author by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var xmlContent by remember { mutableStateOf<String?>(null) }
    var xmlFilename by remember { mutableStateOf<String?>(null) }
    var publicKey by remember { mutableStateOf("No key generated") }

    // Load key on start
    LaunchedEffect(Unit) {
        CryptoUtils.generateSigningKey()
        val key = CryptoUtils.getPublicKeyBase64()
        if (key != null) publicKey = key
    }

    val pickXmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bytes = readBytes(stream)
                    xmlContent = String(bytes, StandardCharsets.UTF_8)
                    // Try to get filename
                    val cursor = context.contentResolver.query(it, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex("_display_name")
                            if (idx != -1) xmlFilename = c.getString(idx)
                        }
                    }
                    if (xmlFilename == null) xmlFilename = "keybox.xml"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveCboxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    CryptoUtils.encryptAndWriteCbox(stream, xmlContent!!, author, password)
                    Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cleveres Encryptor", style = MaterialTheme.typography.headlineMedium)

            // Public Key Section
            OutlinedTextField(
                value = publicKey,
                onValueChange = {},
                label = { Text("Your Public Key (Share this)") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Public Key", publicKey)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }) {
                Text("Copy Public Key")
            }

            HorizontalDivider()

            // Encryption Section
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author / Credit Link") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = { pickXmlLauncher.launch("text/xml") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (xmlFilename == null) "Select Keybox XML" else "Selected: $xmlFilename")
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (author.isBlank()) {
                        Toast.makeText(context, "Author required", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (password.isBlank() || password != confirmPassword) {
                        Toast.makeText(context, "Passwords do not match or empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (xmlContent == null) {
                        Toast.makeText(context, "Select an XML file", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val filename = author.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".cbox"
                    saveCboxLauncher.launch(filename)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = xmlContent != null
            ) {
                Text("Encrypt & Sign")
            }
        }
    }
}

fun readBytes(inputStream: InputStream): ByteArray {
    val buffer = ByteArrayOutputStream()
    val data = ByteArray(1024)
    var nRead: Int
    while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
        buffer.write(data, 0, nRead)
    }
    return buffer.toByteArray()
}
