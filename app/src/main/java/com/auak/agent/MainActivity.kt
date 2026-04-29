package com.auak.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auak.agent.core.service.AuakClawOverlayService
import com.auak.agent.feature.account.AccountScreen
import com.auak.agent.feature.account.LoginScreen
import com.auak.agent.feature.debug.DebugScreen
import com.auak.agent.feature.navigation.MainScreen
import com.auak.agent.feature.settings.AboutScreen
import com.auak.agent.feature.settings.AdvancedSettingsScreen
import com.auak.agent.feature.settings.AppToolsScreen
import com.auak.agent.feature.settings.ServicesScreen
import com.auak.agent.ui.theme.auak agentyTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            AuakClawOverlayService.start(this)
        }
    }

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 权限授予后自动启动语音会话
            AuakClawApp.instance.voiceSessionManager.startSession()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Settings.canDrawOverlays(this)) {
            AuakClawOverlayService.start(this)
        }

        // 监听录音权限请求事件
        lifecycleScope.launch {
            AuakClawApp.instance.recordPermissionEvent.collect {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        setContent {
            auak agentyTheme {
                MainNavigation(
                    onRequestOverlayPermission = { requestOverlayPermission() }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}

// Navigation routes using type-safe navigation
@Serializable object RouteMain
@Serializable object RouteDebug
@Serializable object RouteLogin
@Serializable object RouteAccount
@Serializable object RouteAdvancedSettings
@Serializable object RouteAppTools
@Serializable object RouteServices
@Serializable object RouteAbout

@Composable
fun MainNavigation(onRequestOverlayPermission: () -> Unit) {
    val navController = rememberNavController()
    var showLoginPrompt by remember { mutableStateOf(false) }
    var loginPromptReason by remember { mutableStateOf("") }
    var showSocketAuthDialog by remember { mutableStateOf(false) }
    var hasShownSocketAuthDialog by remember { mutableStateOf(false) }
    var showVoiceErrorDialog by remember { mutableStateOf(false) }
    var voiceErrorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        AuakClawApp.instance.loginRequiredEvent.collect { reason ->
            loginPromptReason = reason
            showLoginPrompt = true
        }
    }

    LaunchedEffect(Unit) {
        AuakClawApp.instance.voiceSessionManager.voiceErrorEvent.collect { message ->
            voiceErrorMessage = message
            showVoiceErrorDialog = true
        }
    }

    LaunchedEffect(Unit) {
        AuakClawApp.instance.socketAuthRequiredEvent.collect {
            if (!hasShownSocketAuthDialog) {
                hasShownSocketAuthDialog = true
                showSocketAuthDialog = true
            }
        }
    }

    if (showSocketAuthDialog) {
        AlertDialog(
            onDismissRequest = { showSocketAuthDialog = false },
            title = { Text(stringResource(R.string.socket_auth_required_title)) },
            text = { Text(stringResource(R.string.socket_auth_required_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSocketAuthDialog = false
                    navController.navigate(RouteLogin)
                }) {
                    Text(stringResource(R.string.btn_go_login))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSocketAuthDialog = false }) {
                    Text(stringResource(R.string.btn_chat_only))
                }
            }
        )
    }

    if (showLoginPrompt) {
        AlertDialog(
            onDismissRequest = { showLoginPrompt = false },
            title = { Text("需要登录") },
            text = { Text(loginPromptReason) },
            confirmButton = {
                TextButton(onClick = {
                    showLoginPrompt = false
                    navController.navigate(RouteLogin)
                }) {
                    Text("去登录")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginPrompt = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showVoiceErrorDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceErrorDialog = false },
            title = { Text("语音助手") },
            text = { Text(voiceErrorMessage) },
            confirmButton = {
                TextButton(onClick = { showVoiceErrorDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = RouteMain,
        enterTransition = { slideInHorizontally(tween(300)) { it } },
        exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
        popExitTransition = { slideOutHorizontally(tween(300)) { it } }
    ) {
        composable<RouteMain> {
            MainScreen(
                onNavigateAdvancedSettings = { navController.navigate(RouteAdvancedSettings) },
                onNavigateAppTools = { navController.navigate(RouteAppTools) },
                onNavigateServices = { navController.navigate(RouteServices) },
                onNavigateAbout = { navController.navigate(RouteAbout) },
                onNavigateLogin = { navController.navigate(RouteLogin) },
                onNavigateAccount = { navController.navigate(RouteAccount) },
                onRequestOverlayPermission = onRequestOverlayPermission
            )
        }
        composable<RouteDebug> {
            DebugScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteLogin> {
            LoginScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteAccount> {
            AccountScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteAdvancedSettings> {
            AdvancedSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteAppTools> {
            AppToolsScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteServices> {
            ServicesScreen(
                onRequestOverlayPermission = onRequestOverlayPermission,
                onNavigateDebug = { navController.navigate(RouteDebug) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<RouteAbout> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
