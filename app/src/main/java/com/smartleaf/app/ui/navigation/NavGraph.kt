package com.smartleaf.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartleaf.app.ui.AuthState
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.theme.VibrantGreen
import com.smartleaf.app.ui.screens.ScanDetailScreen
import com.smartleaf.app.ui.screens.YieldAnalyticsDetailScreen
import com.smartleaf.app.ui.screens.CaptureScreen
import com.smartleaf.app.ui.screens.HomeScreen
import com.smartleaf.app.ui.screens.ProcessingScreen
import com.smartleaf.app.ui.screens.ResultScreen
import com.smartleaf.app.ui.screens.auth.ForgotPasswordScreen
import com.smartleaf.app.ui.screens.auth.LoginScreen
import com.smartleaf.app.ui.screens.auth.OtpVerificationScreen
import com.smartleaf.app.ui.screens.auth.RegisterScreen
import com.smartleaf.app.ui.screens.auth.ResetPasswordScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

@Composable
fun SmartLeafNavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val authStateState = viewModel.authState.collectAsStateWithLifecycle()
    val authState = authStateState.value

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LaunchedEffect(authState) {
                if (authState is AuthState.Success) {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                } else if (authState is AuthState.AwaitingVerification) {
                    navController.navigate("otp")
                }
            }
            if ((authState is AuthState.Initializing) || (authState is AuthState.Success)) {
                // Return a blank surface to prevent flashing the login screen while checking session
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VibrantGreen,
                ) {}
            } else {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate("register") },
                    onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                    onLoginClick = { email, pass -> viewModel.login(email, pass) },
                    errorMessage = (authState as? AuthState.Error)?.message,
                    isLoading = authState is AuthState.Loading,
                )
            }
        }
        composable("register") {
            LaunchedEffect(authState) {
                if (authState is AuthState.AwaitingVerification) {
                    navController.navigate("otp")
                }
            }
            RegisterScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.navigate("login") { popUpTo(0) } },
                onRegisterClick = { name, email, pass -> viewModel.register(name, email, pass) },
                errorMessage = (authState as? AuthState.Error)?.message,
                isLoading = authState is AuthState.Loading
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            ) { email -> navController.navigate("reset_password/$email") }
        }
        composable("reset_password/{email}") { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            ResetPasswordScreen(
                email = email,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onResetSuccess = { 
                    navController.navigate("login") { 
                        popUpTo("login") { inclusive = true } 
                    } 
                }
            )
        }
        composable("otp") {
            LaunchedEffect(authState) {
                if (authState is AuthState.Success) {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                        popUpTo("register") { inclusive = true }
                        popUpTo("otp") { inclusive = true }
                    }
                }
            }
            val state = authState as? AuthState.AwaitingVerification
            OtpVerificationScreen(
                email = state?.user?.email ?: "",
                actualOtp = state?.otp,
                onNavigateBack = { viewModel.resetAuthState(); navController.popBackStack() },
                onVerifyClick = { inputOtp -> 
                    state?.let {
                        viewModel.verifyOtp(it.user, inputOtp, it.otp)
                    }
                },
                onResendClick = {
                    state?.let {
                        viewModel.resendOtp(it.user)
                    }
                },
                errorMessage = (authState as? AuthState.Error)?.message,
                isLoading = authState is AuthState.Loading
            )
        }
        composable("home") {
            LaunchedEffect(authState) {
                if ((authState !is AuthState.Success) && (authState !is AuthState.Initializing)) {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            }
            HomeScreen(
                viewModel = viewModel, 
                onNavigateToCapture = { navController.navigate("capture") },
                onNavigateToScanDetail = { id -> navController.navigate("scan_detail/$id") },
                onNavigateToYieldAnalytics = { navController.navigate("yield_analytics_detail") }
            )
        }
        composable("capture") {
            CaptureScreen(
                viewModel = viewModel,
                onCaptureSuccess = {
                    navController.navigate("processing") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("processing") {
            ProcessingScreen(
                viewModel = viewModel,
                onProcessingComplete = {
                    navController.navigate("results") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }
        composable("results") {
            ResultScreen(
                viewModel = viewModel,
                onBackToHome = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
        composable("scan_detail/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            ScanDetailScreen(
                recordId = id,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("yield_analytics_detail") {
            YieldAnalyticsDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
