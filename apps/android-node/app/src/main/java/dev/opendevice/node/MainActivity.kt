package dev.opendevice.node

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendevice.node.ui.AndroidNodeServiceControl
import dev.opendevice.node.ui.ModelSetupScreen
import dev.opendevice.node.ui.NodeApp
import dev.opendevice.node.ui.NodeAppActions
import dev.opendevice.node.ui.NodeDestination
import dev.opendevice.node.ui.NodeViewModel
import dev.opendevice.node.ui.ServerSocketPortAvailabilityProbe
import dev.opendevice.node.ui.shouldShowModelSetup

class MainActivity : ComponentActivity() {
    private val nodeApplication: OpenDeviceNodeApp
        get() = application as OpenDeviceNodeApp

    private val nodeViewModel: NodeViewModel by viewModels {
        viewModelFactoryOverride ?: object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass.isAssignableFrom(NodeViewModel::class.java))
                val app = nodeApplication
                return NodeViewModel(
                    moduleRegistry = app.moduleRegistry,
                    aiModuleId = app.aiModuleId,
                    modelRepository = app.modelDownloadRepository,
                    modelId = app.recommendedModel.id,
                    modelDisplayName = app.recommendedModel.displayName,
                    controller = app.aiNodeController,
                    settingsRepository = app.nodeSettingsRepository,
                    apiKeyStore = app.apiKeyStore,
                    auditStore = app.apiAuditStore,
                    socketHttpServer = app.socketHttpServer,
                    deviceFactsSource = app.deviceFactsSource,
                    serviceControl = AndroidNodeServiceControl(this@MainActivity),
                    addressResolver = app.nodeAddressResolver,
                    portProbe = ServerSocketPortAvailabilityProbe,
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notificationPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> nodeViewModel.startNode(notificationPermissionGranted = granted) }

        setContent {
            val state by nodeViewModel.state.collectAsStateWithLifecycle()
            var destination by rememberSaveable { mutableStateOf(NodeDestination.MODULES) }
            var modelSetupSkipped by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(destination, state.aiModule?.installed, state.modules.safeMode) {
                if (destination.isAi) nodeViewModel.prepareAiModule()
            }

            MaterialTheme {
                if (shouldShowModelSetup(state.modelState, modelSetupSkipped, destination, state.aiModule?.installed == true)) {
                    ModelSetupScreen(
                        state = state,
                        recommendedModel = nodeApplication.recommendedModel,
                        onDownload = nodeViewModel::downloadModel,
                        onCancelDownload = nodeViewModel::cancelDownload,
                        onSkip = { modelSetupSkipped = true; destination = NodeDestination.MODULES },
                    )
                } else NodeApp(
                    state = state,
                    destination = destination,
                    actions = NodeAppActions(
                        enableAiModule = nodeViewModel::enableAiModule,
                        disableAiModule = nodeViewModel::disableAiModule,
                        enableModule = nodeViewModel::enableModule,
                        disableModule = nodeViewModel::disableModule,
                        installModule = nodeViewModel::installModule,
                        uninstallModule = nodeViewModel::uninstallModule,
                        downloadModel = nodeViewModel::downloadModel,
                        cancelDownload = nodeViewModel::cancelDownload,
                        startNode = {
                            if (
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                nodeViewModel.startNode(notificationPermissionGranted = true)
                            } else {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        stopNode = nodeViewModel::stopNode,
                        sendLocalMessage = nodeViewModel::sendLocalMessage,
                        cancelLocalMessage = nodeViewModel::cancelLocalMessage,
                        setPort = nodeViewModel::setPort,
                        setMaxOutputTokens = nodeViewModel::setMaxOutputTokens,
                        setThreads = nodeViewModel::setThreads,
                        setTemperatureLimitC = nodeViewModel::setTemperatureLimitC,
                        setGenerationTimeoutSeconds = nodeViewModel::setGenerationTimeoutSeconds,
                        setShowPerformance = nodeViewModel::setShowPerformance,
                        setPerformancePreset = nodeViewModel::setPerformancePreset,
                        requestLanEnable = nodeViewModel::requestLanEnable,
                        cancelLanEnable = nodeViewModel::cancelLanEnable,
                        confirmLanEnable = nodeViewModel::confirmLanEnable,
                        disableLan = nodeViewModel::disableLan,
                        createClient = nodeViewModel::createClient,
                        dismissOneTimeToken = nodeViewModel::dismissOneTimeToken,
                        revokeClient = nodeViewModel::revokeClient,
                        exitSafeMode = nodeViewModel::exitSafeMode,
                        clearMessages = nodeViewModel::clearMessages,
                    ),
                    onNavigate = { destination = it },
                )
            }
        }
    }

    companion object {
        @Volatile
        internal var viewModelFactoryOverride: ViewModelProvider.Factory? = null
    }
}
