package org.duckdns.dorandoran.callaiassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.duckdns.dorandoran.callaiassistant.ui.screens.CallHistoryScreen
import org.duckdns.dorandoran.callaiassistant.ui.screens.DialerScreen
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
    } else {
        arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsState = allGranted
    }

    private var permissionsState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        setContent {
            CallaiassistantTheme {
                if (permissionsState) {
                    PhoneAppContent()
                } else {
                    PermissionRequestScreen(
                        onRequestPermission = { requestPermissions() }
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        permissionsState = allGranted
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }
}

@Composable
private fun PhoneAppContent() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Call, contentDescription = "다이얼러") },
                    label = { Text("다이얼러") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "전화 기록") },
                    label = { Text("전화 기록") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> DialerScreen(
                onCallStarted = { /* 통화 대상 표시는 DialerScreen 내부에서 처리 */ },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> CallHistoryScreen(
                onCallNumber = { },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Button(onClick = onRequestPermission) {
            Text("전화 권한 허용")
        }
    }
}
