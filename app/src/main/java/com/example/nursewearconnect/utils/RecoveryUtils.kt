package com.example.nursewearconnect.utils

import java.security.SecureRandom
import java.util.Locale

object RecoveryUtils {
    private val random = SecureRandom()

    /**
     * Generates a 4-digit OTP code for password recovery.
     */
    fun generateOtpCode(): String {
        val code = random.nextInt(10000)
        return String.format(Locale.US, "%04d", code)
    }

    /**
     * Checks if the OTP code is expired (e.g., older than 15 minutes).
     * This is usually better handled by the database/backend, but provided here for reference.
     */
    fun isOtpExpired(createdAtMillis: Long, expiryMinutes: Int = 15): Boolean {
        val now = System.currentTimeMillis()
        val expiryMillis = expiryMinutes * 60 * 1000L
        return (now - createdAtMillis) > expiryMillis
    }
}
