package com.example.nursewearconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nursewearconnect.data.repository.AuthRepository
import com.example.nursewearconnect.utils.AppUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegistrationViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _registrationSuccess = MutableStateFlow<String?>(null) // Stores role on success
    val registrationSuccess: StateFlow<String?> = _registrationSuccess.asStateFlow()

    private val _verificationSent = MutableStateFlow(false)
    val verificationSent: StateFlow<Boolean> = _verificationSent.asStateFlow()

    private val _lastEmail = MutableStateFlow("")
    val lastEmail: StateFlow<String> = _lastEmail.asStateFlow()

    fun register(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String,
        role: String,
        businessName: String? = null,
        location: String? = null,
        businessDescription: String? = null,
        licenseUrl: String? = null,
        referralCode: String? = null
    ) {
        _lastEmail.value = email
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _verificationSent.value = false
            
            // Check if email already exists
            val emailCheck = authRepository.checkEmailExists(email)
            if (emailCheck.getOrDefault(false)) {
                _error.value = "An account with this email already exists."
                _isLoading.value = false
                return@launch
            }

            val result = authRepository.register(
                email = email,
                password = password,
                fullName = fullName,
                phoneNumber = phoneNumber,
                role = role,
                businessName = businessName,
                location = location,
                businessDescription = businessDescription,
                licenseUrl = licenseUrl,
                referralCode = referralCode
            )
            
            _isLoading.value = false
            
            result.onSuccess {
                _registrationSuccess.value = role
                if (authRepository.getUserId() == null) {
                    _verificationSent.value = true
                }
            }
            result.onFailure {
                _error.value = AppUtils.mapThrowable(it)
            }
        }
    }

    fun uploadLicense(bytes: ByteArray, extension: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            
            val processedBytes = if (extension.lowercase() in listOf("jpg", "jpeg", "png")) {
                AppUtils.optimizeImage(bytes)
            } else {
                bytes
            }

            val userId = authRepository.getUserId() ?: "anonymous"
            val result = authRepository.uploadLicense(userId, processedBytes, extension)
            
            _isLoading.value = false

            result.onSuccess { onComplete(it) }
            result.onFailure { _error.value = AppUtils.mapThrowable(it) }
        }
    }

    fun resetRegistrationState() {
        _registrationSuccess.value = null
        _error.value = null
        _verificationSent.value = false
    }
}
