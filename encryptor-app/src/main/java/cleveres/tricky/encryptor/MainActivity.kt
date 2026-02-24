package cleveres.tricky.encryptor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

// --- Theme Colors ---
val WebUiBackground = Color(0xFF0B0B0C)
val WebUiForeground = Color(0xFFE5E7EB)
val WebUiAccent = Color(0xFFD1D5DB)
val WebUiPanel = Color(0xFF161616)
val WebUiBorder = Color(0xFF333333)
val WebUiInputBackground = Color(0xFF1A1A1A)
val WebUiSuccess = Color(0xFF34D399)
val WebUiDanger = Color(0xFFEF4444)

@Composable
fun WebUiTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        background = WebUiBackground,
        surface = WebUiPanel,
        onBackground = WebUiForeground,
        onSurface = WebUiForeground,
        primary = WebUiAccent,
        onPrimary = WebUiBackground, // Text on primary button should be dark
        secondary = WebUiAccent,
        outline = WebUiBorder,
        error = WebUiDanger,
        surfaceVariant = WebUiInputBackground,
        onSurfaceVariant = WebUiForeground
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// --- Navigation ---
enum class Screen {
    List, Create
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebUiTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    var currentScreen by remember { mutableStateOf(Screen.List) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            Screen.List -> KeyboxListScreen(
                onNavigateToCreate = { currentScreen = Screen.Create }
            )
            Screen.Create -> CreateKeyboxScreen(
                onNavigateBack = { currentScreen = Screen.List }
            )
        }
    }
}

@Composable
fun KeyboxListScreen(onNavigateToCreate: () -> Unit) {
    val context = LocalContext.current
    var keyboxFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        val dir = context.getExternalFilesDir(null)
        keyboxFiles = dir?.listFiles { file -> file.name.endsWith(".cbox") }?.toList() ?: emptyList()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = WebUiAccent,
                contentColor = WebUiBackground
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create New")
            }
        },
        containerColor = WebUiBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                "Keyboxes",
                style = MaterialTheme.typography.headlineMedium,
                color = WebUiForeground,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (keyboxFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No keyboxes found.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(keyboxFiles) { file ->
                        KeyboxItem(file)
                    }
                }
            }
        }
    }
}

@Composable
fun KeyboxItem(file: File) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WebUiPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, WebUiBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                color = WebUiForeground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Size: ${file.length()} bytes",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateKeyboxScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var author by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var xmlContent by remember { mutableStateOf<String?>(null) }
    var xmlFilename by remember { mutableStateOf<String?>(null) }
    var publicKey by remember { mutableStateOf("Generating...") }

    // Load key on start
    LaunchedEffect(Unit) {
        CryptoUtils.generateSigningKey()
        val key = CryptoUtils.getPublicKeyBase64()
        if (key != null) publicKey = key else publicKey = "Error generating key"
    }

    val pickXmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bytes = readBytes(stream)
                    xmlContent = String(bytes, StandardCharsets.UTF_8)
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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Create Keybox", color = WebUiForeground) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WebUiForeground)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = WebUiBackground)
            )
        },
        containerColor = WebUiBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Public Key Section
            OutlinedTextField(
                value = publicKey,
                onValueChange = {},
                label = { Text("Your Public Key") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WebUiInputBackground,
                    unfocusedContainerColor = WebUiInputBackground,
                    focusedBorderColor = WebUiAccent,
                    unfocusedBorderColor = WebUiBorder,
                    focusedTextColor = WebUiForeground,
                    unfocusedTextColor = WebUiForeground,
                    focusedLabelColor = WebUiAccent,
                    unfocusedLabelColor = Color.Gray
                )
            )
            Button(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Public Key", publicKey)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WebUiPanel, contentColor = WebUiAccent),
                border = androidx.compose.foundation.BorderStroke(1.dp, WebUiBorder)
            ) {
                Text("Copy Public Key")
            }

            HorizontalDivider(color = WebUiBorder)

            // Input Fields
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author / Credit Link") },
                modifier = Modifier.fillMaxWidth(),
                 colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WebUiInputBackground,
                    unfocusedContainerColor = WebUiInputBackground,
                    focusedBorderColor = WebUiAccent,
                    unfocusedBorderColor = WebUiBorder,
                    focusedTextColor = WebUiForeground,
                    unfocusedTextColor = WebUiForeground,
                    focusedLabelColor = WebUiAccent,
                    unfocusedLabelColor = Color.Gray
                )
            )

            Button(
                onClick = { pickXmlLauncher.launch("text/xml") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WebUiPanel, contentColor = WebUiForeground),
                border = androidx.compose.foundation.BorderStroke(1.dp, WebUiBorder)
            ) {
                Text(if (xmlFilename == null) "Select Keybox XML" else "Selected: $xmlFilename")
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                 colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WebUiInputBackground,
                    unfocusedContainerColor = WebUiInputBackground,
                    focusedBorderColor = WebUiAccent,
                    unfocusedBorderColor = WebUiBorder,
                    focusedTextColor = WebUiForeground,
                    unfocusedTextColor = WebUiForeground,
                    focusedLabelColor = WebUiAccent,
                    unfocusedLabelColor = Color.Gray
                )
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                 colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WebUiInputBackground,
                    unfocusedContainerColor = WebUiInputBackground,
                    focusedBorderColor = WebUiAccent,
                    unfocusedBorderColor = WebUiBorder,
                    focusedTextColor = WebUiForeground,
                    unfocusedTextColor = WebUiForeground,
                    focusedLabelColor = WebUiAccent,
                    unfocusedLabelColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

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

                    try {
                        val filename = author.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".cbox"
                        val file = File(context.getExternalFilesDir(null), filename)
                        file.outputStream().use { stream ->
                            CryptoUtils.encryptAndWriteCbox(stream, xmlContent!!, author, password)
                        }
                        Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                        onNavigateBack() // Go back to list
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WebUiAccent, contentColor = WebUiBackground),
                enabled = xmlContent != null
            ) {
                Text("Encrypt & Save")
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
