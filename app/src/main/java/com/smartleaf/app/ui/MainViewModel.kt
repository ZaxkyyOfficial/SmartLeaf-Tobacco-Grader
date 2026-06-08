package com.smartleaf.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartleaf.app.data.local.AppDatabase
import com.smartleaf.app.data.local.SmartLeafResult
import com.smartleaf.app.data.local.UserEntity
import com.smartleaf.app.location.LocationTracker
import com.smartleaf.app.repository.LeafRepository
import com.smartleaf.app.repository.UserRepository
import com.smartleaf.app.data.local.SessionManager
import com.smartleaf.app.tflite.ClassifierHelper
import com.smartleaf.app.tflite.DeterministicColorClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

sealed class AuthState {
    object Initializing : AuthState()
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: UserEntity) : AuthState()
    data class AwaitingVerification(val user: UserEntity, val otp: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Capturing : ProcessingState()
    object Processing : ProcessingState()
    data class Success(val result: SmartLeafResult) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LeafRepository
    private val userRepository: UserRepository
    private val classifierHelper = DeterministicColorClassifier()
    private val locationTracker = LocationTracker(application)
    private val sessionManager = SessionManager(application)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = LeafRepository(database.resultDao())
        userRepository = UserRepository(database.userDao())

        // Check active session and sync
        viewModelScope.launch {
            val userId = sessionManager.userIdFlow.first()
            if (userId != null) {
                val user = userRepository.getUserById(userId)
                if (user != null) {
                    _authState.value = AuthState.Success(user)
                    repository.syncPendingResults(userId)
                    return@launch
                }
            }
            _authState.value = AuthState.Idle
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardStats: StateFlow<List<SmartLeafResult>> = sessionManager.userIdFlow
        .flatMapLatest { id -> if (id != null) repository.getAllResults(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<SmartLeafResult>> = combine(dashboardStats, _searchQuery) { stats, query ->
        if (query.isBlank()) {
            stats
        } else {
            stats.filter { it.scanName.contains(query, ignoreCase = true) || it.id.toString().contains(query) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notificationsEnabled: StateFlow<Boolean> = sessionManager.notificationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    fun resetState() {
        _processingState.value = ProcessingState.Idle
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    fun register(fullName: String, email: String, passwordHash: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val existingUser = userRepository.getUserByEmail(email)
                if (existingUser != null) {
                    _authState.value = AuthState.Error("Email already registered")
                    return@launch
                }
                val newUser = UserEntity(fullName = fullName, email = email, passwordHash = passwordHash)
                val id = userRepository.registerUser(newUser)
                val registeredUser = newUser.copy(id = id)
                // Generate simulated OTP
                val otp = (1000..9999).random().toString()
                _authState.value = AuthState.AwaitingVerification(registeredUser, otp)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun login(email: String, passwordHash: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val user = userRepository.getUserByEmail(email)
                if (user == null || user.passwordHash != passwordHash) {
                    _authState.value = AuthState.Error("Invalid email or password")
                    return@launch
                }
                if (!user.isVerified) {
                    val otp = (1000..9999).random().toString()
                    _authState.value = AuthState.AwaitingVerification(user, otp)
                    return@launch
                }
                _authState.value = AuthState.Success(user)
                sessionManager.saveSession(user.id, user.email)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun verifyOtp(user: UserEntity, inputOtp: String, actualOtp: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                if (inputOtp == actualOtp) {
                    val verifiedUser = user.copy(isVerified = true)
                    userRepository.updateUserVerification(user)
                    sessionManager.saveSession(verifiedUser.id, verifiedUser.email)
                    _authState.value = AuthState.Success(verifiedUser)
                } else {
                    _authState.value = AuthState.Error("Invalid Verification Code")
                    delay(1500) // Show error briefly
                    _authState.value = AuthState.AwaitingVerification(user, actualOtp)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Verification failed")
            }
        }
    }

    fun resendOtp(user: UserEntity) {
        val newOtp = (1000..9999).random().toString()
        _authState.value = AuthState.AwaitingVerification(user, newOtp)
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _authState.value = AuthState.Idle
        }
    }

    suspend fun checkEmailExists(email: String): Boolean {
        return userRepository.getUserByEmail(email) != null
    }

    fun resetPassword(email: String, newPasswordHash: String) {
        viewModelScope.launch {
            userRepository.updatePassword(email, newPasswordHash)
        }
    }

    fun saveResult(result: SmartLeafResult) {
        viewModelScope.launch {
            val userId = sessionManager.userIdFlow.first() ?: return@launch
            repository.saveResult(result.copy(userId = userId))
            repository.syncPendingResults(userId)
        }
    }

    fun deleteScanRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteResult(id)
        }
    }

    fun updateScanName(id: Long, newName: String) {
        viewModelScope.launch {
            repository.updateScanName(id, newName)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.setNotificationsEnabled(enabled)
        }
    }

    fun processImage(bitmap: Bitmap, imageUri: String?) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.Processing
            
            try {
                // Simulate 2-second processing delay
                delay(2000)
                
                // 1. Classify image using Dual-AI logic
                val classification = classifierHelper.classifyImage(bitmap)
                
                // 2. Fetch location
                val location = locationTracker.getCurrentLocation()
                
                // 3. Create Result Record
                val result = SmartLeafResult(
                    maturityPhase = classification.maturityPhase,
                    qualityGrade = classification.qualityGrade,
                    confidence = classification.confidence,
                    timestamp = System.currentTimeMillis(),
                    latitude = location?.latitude ?: 0.0,
                    longitude = location?.longitude ?: 0.0,
                    estimatedHarvestDays = classification.estimatedHarvestDays,
                    isCloudSynced = false,
                    imageUri = imageUri,
                    moisture = classification.moisture,
                    colorCode = classification.colorCode
                )
                
                // 4. Update UI without saving to DB yet (save happens in ResultScreen)
                _processingState.value = ProcessingState.Success(result)

            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

}
