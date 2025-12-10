package com.example.phonepam

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.phonepam.ui.theme.PhonePamTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var udpListener: UdpBroadcastListener
    private lateinit var rsaCrypto: RsaCrypto

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startUdpListener()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        rsaCrypto = RsaCrypto(this)
        udpListener = UdpBroadcastListener(this, rsaCrypto, 4200)

        setContent {
            PhonePamTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PublicKeyScreen(
                        publicKey = rsaCrypto.getPublicKeyBase64(),
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        checkNotificationPermissionAndStart()
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startUdpListener()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startUdpListener()
        }
    }

    private fun startUdpListener() {
        lifecycleScope.launch {
            udpListener.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        udpListener.stop()
    }
}

@Composable
fun PublicKeyScreen(publicKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Phone PAM",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Public Key",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Copy this base64-encoded public key and add it to your PAM configuration on your PC:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = publicKey,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }

        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Public Key", publicKey)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Public key copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy Public Key")
        }

        Text(
            text = "The app is listening for authentication requests on UDP port 4200.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PublicKeyScreenPreview() {
    PhonePamTheme {
        PublicKeyScreen("PREVIEW_KEY_NOT_REAL_abcdefghijklmnopqrstuvwxyz1234567890")
    }
}