package com.example.nursewearconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.nursewearconnect.NurseWearApplication

class ViewModelFactory(private val application: NurseWearApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(application.authRepository) as T
            }
            modelClass.isAssignableFrom(RegistrationViewModel::class.java) -> {
                RegistrationViewModel(application.authRepository) as T
            }
            modelClass.isAssignableFrom(RecoveryViewModel::class.java) -> {
                RecoveryViewModel(application.authRepository) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    application.productRepository, 
                    application.cartRepository,
                    application.orderRepository,
                    application.paymentRepository,
                    application.userRepository,
                    application.vendorRepository,
                    application.adminRepository,
                    application.authRepository
                ) as T
            }
            modelClass.isAssignableFrom(MessagingViewModel::class.java) -> {
                MessagingViewModel(application.supabaseClient) as T
            }
            // Add more ViewModels as needed
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
