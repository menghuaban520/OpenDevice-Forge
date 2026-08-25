package dev.opendevice.node

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.ui.NodeApp
import dev.opendevice.node.ui.NodeAppState
import dev.opendevice.node.ui.NodeDestination

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nodeApplication = application as OpenDeviceNodeApp
        val deviceFactsSource = nodeApplication.deviceFactsSource
        setContent {
            val modules by nodeApplication.moduleRegistry.snapshot
                .collectAsStateWithLifecycle()
            val factsFlow = remember(deviceFactsSource) { deviceFactsSource.observe() }
            val facts by factsFlow.collectAsStateWithLifecycle(initialValue = DeviceFacts())
            var destination by rememberSaveable { mutableStateOf(NodeDestination.NODE) }

            MaterialTheme {
                NodeApp(
                    state = NodeAppState(
                        destination = destination,
                        modules = modules,
                        facts = facts,
                    ),
                    onNavigate = { destination = it },
                )
            }
        }
    }
}
