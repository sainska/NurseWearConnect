package com.example.nursewearconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nursewearconnect.data.repository.AuthRepository
import com.example.nursewearconnect.utils.AppUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecoveryViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    private val _otpVerified = MutableStateFlow(false)
    val otpVerified: StateFlow<Boolean> = _otpVerified.asStateFlow()

    private val _resendTimer = MutableStateFlow(0)
    val resendTimer: StateFlow<Int> = _resendTimer.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _success.value = false
            
            // Step 1: Check if email exists in our records
            val emailResult = authRepository.checkEmailExists(email)
            if (emailResult.isSuccess && !emailResult.getOrDefault(false)) {
                _error.value = "We couldn't find an account with that email address."
                _isLoading.value = false
                return@launch
            }

            // Step 2: Request reset if email exists
            val result = authRepository.requestPasswordReset(email)
            _isLoading.value = false
            
            result.onSuccess {
                _success.value = true
                startResendTimer()
            }
            result.onFailure {
                _error.value = AppUtils.mapThrowable(it)
            }
        }
    }

    private fun startResendTimer() {
        timerJob?.cancel()
        _resendTimer.value = 60
        timerJob = viewModelScope.launch {
            while (_resendTimer.value > 0) {
                kotlinx.coroutines.delay(1000)
                _resendTimer.value -= 1
            }
        }
    }

    fun verifyOtp(email: String, otp: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _otpVerified.value = false
            val result = authRepository.verifyOtp(email, otp)
            _isLoading.value = false
            
            result.onSuccess {
                _otpVerified.value = true
            }
            result.onFailure {
                _error.value = AppUtils.mapThrowable(it)
            }
        }
    }

    fun updatePassword(email: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _success.value = false
            val result = authRepository.updatePassword(email, otp, newPassword)
            _isLoading.value = false
            
            result.onSuccess {
                _success.value = true
            }
            result.onFailure {
                _error.value = AppUtils.mapThrowable(it)
            }
        }
    }

    fun clearState() {
        _error.value = null
        _success.value = false
        _isLoading.value = false
        _otpVerified.value = false
    }
}
