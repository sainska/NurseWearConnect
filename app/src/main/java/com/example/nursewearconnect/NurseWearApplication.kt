package com.example.nursewearconnect

import android.app.Application
import com.example.nursewearconnect.di.AppContainer
import com.example.nursewearconnect.data.repository.*
import com.example.nursewearconnect.data.security.SecurityManager
import io.github.jan.supabase.SupabaseClient

class NurseWearApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // Delegate repository access to the container
    val supabaseClient: SupabaseClient get() = container.supabaseClient
    val authRepository: AuthRepository get() = container.authRepository
    val productRepository: ProductRepository get() = container.productRepository
    val cartRepository: CartRepository get() = container.cartRepository
    val orderRepository: OrderRepository get() = container.orderRepository
    val paymentRepository: PaymentRepository get() = container.paymentRepository
    val userRepository: UserRepository get() = container.userRepository
    val adminRepository: AdminRepository get() = container.adminRepository
    val vendorRepository: VendorRepository get() = container.vendorRepository
    val securityManager: SecurityManager get() = container.securityManager
}
