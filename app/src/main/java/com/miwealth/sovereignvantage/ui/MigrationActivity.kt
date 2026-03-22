// MigrationActivity.kt
// Android UI for the secure, non-networked data migration feature.

package com.miwealth.sovereignvantage.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.miwealth.sovereignvantage.max.DataMigrationService
import com.miwealth.sovereignvantage.ui.theme.SovereignVantageTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)

class MigrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SovereignVantageTheme {
                MigrationScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationScreen() {
    val context = LocalContext.current
    val migrationService = remember { DataMigrationService(context) }
    var step by remember { mutableStateOf(MigrationStep.START) }
    var otmk by remember { mutableStateOf("") }
    var qrCodeData by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Ready to begin migration.") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sovereign Vantage Migration") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            when (step) {
                MigrationStep.START -> StartMigrationView(onStart = { step = MigrationStep.EXPORT_DATA })
                MigrationStep.EXPORT_DATA -> ExportDataView(
                    migrationService = migrationService,
                    onDataGenerated = { data, key ->
                        qrCodeData = data
                        otmk = key
                        step = MigrationStep.DISPLAY_QR
                    },
                    onFailure = { statusMessage = it }
                )
                MigrationStep.DISPLAY_QR -> DisplayQRView(
                    qrCodeData = qrCodeData,
                    otmk = otmk,
                    onNext = { step = MigrationStep.IMPORT_DATA }
                )
                MigrationStep.IMPORT_DATA -> ImportDataView(
                    migrationService = migrationService,
                    onSuccess = {
                        statusMessage = "Migration Complete! Restarting app..."
                        step = MigrationStep.COMPLETE
                    },
                    onFailure = { statusMessage = it }
                )
                MigrationStep.COMPLETE -> CompleteView(statusMessage)
            }
            
            Text(statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
fun StartMigrationView(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Secure Data Migration", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("This process securely transfers your SRP, learning data, and risk configuration from the original Sovereign Vantage app to Sovereign Vantage via a non-networked QR code transfer.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStart) {
            Text("Start Export (Source App)")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { /* Navigate to QR Scanner */ }) {
            Text("Start Import (Target App)")
        }
    }
}

@Composable
fun ExportDataView(
    migrationService: DataMigrationService,
    onDataGenerated: (String, String) -> Unit,
    onFailure: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        try {
            val (data, key) = migrationService.generateMigrationData()
            onDataGenerated(data, key)
        } catch (e: Exception) {
            onFailure("Export Failed: ${e.message}")
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Generating PQC-Secured Migration Data...")
    }
}

@Composable
fun DisplayQRView(qrCodeData: String, otmk: String, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Scan this QR Code with Sovereign Vantage", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        
        // QR Code Generation
        val bitmap = remember(qrCodeData) {
            try {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(qrCodeData, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val pixels = IntArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        pixels[y * width + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    }
                }
                android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565).apply {
                    setPixels(pixels, 0, width, 0, 0, width, height)
                }
            } catch (e: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Migration QR Code",
                modifier = Modifier.size(256.dp)
            )
        } else {
            Text("Error generating QR code.", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Text("One-Time Migration Key (OTMK):", style = MaterialTheme.typography.titleMedium)
        Text(otmk, style = MaterialTheme.typography.bodyLarge)
        Text("Enter this key manually if scanning fails.", style = MaterialTheme.typography.bodySmall)
        
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext) {
            Text("I have scanned the code (Next Step)")
        }
    }
}

@Composable
fun ImportDataView(
    migrationService: DataMigrationService,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    var encodedPayload by remember { mutableStateOf("") }
    var otmk by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Import Data (Target App)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        
        // Placeholder for QR Scanner integration
        Button(onClick = { /* Launch QR Scanner */ }) {
            Text("Scan QR Code")
        }
        
        Spacer(Modifier.height(16.dp))
        Text("OR Enter Manually:", style = MaterialTheme.typography.titleMedium)
        
        OutlinedTextField(
            value = encodedPayload,
            onValueChange = { encodedPayload = it },
            label = { Text("Encrypted Payload (Base64)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = otmk,
            onValueChange = { otmk = it },
            label = { Text("One-Time Migration Key (OTMK)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (encodedPayload.isNotBlank() && otmk.isNotBlank()) {
                    coroutineScope.launch {
                        try {
                            if (migrationService.consumeMigrationData(encodedPayload, otmk)) {
                                onSuccess()
                            } else {
                                onFailure("Import Failed: Invalid data or key.")
                            }
                        } catch (e: Exception) {
                            onFailure("Import Failed: ${e.message}")
                        }
                    }
                } else {
                    onFailure("Please scan or enter both payload and OTMK.")
                }
            },
            enabled = encodedPayload.isNotBlank() && otmk.isNotBlank()
        ) {
            Text("Complete Migration")
        }
    }
}

@Composable
fun CompleteView(statusMessage: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Migration Status", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(statusMessage, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { /* System.exit(0) or similar */ }) {
            Text("Restart Sovereign Vantage")
        }
    }
}

enum class MigrationStep {
    START, EXPORT_DATA, DISPLAY_QR, IMPORT_DATA, COMPLETE
}

