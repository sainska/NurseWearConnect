package com.example.nursewearconnect.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.example.nursewearconnect.ui.theme.Brand600

object CheckoutLauncher {
    private const val TAG = "CheckoutLauncher"

    /**
     * Opens Paystack Checkout in a Chrome Custom Tab.
     * This is the secure "in-app browser" approach recommended for Paystack.
     */
    fun openPaystackCheckout(context: Context, authorizationUrl: String) {
        if (authorizationUrl.isBlank()) {
            Log.e(TAG, "Cannot open checkout: URL is blank")
            return
        }

        val uri = Uri.parse(authorizationUrl)
        Log.d(TAG, "Opening Paystack Checkout URL: $authorizationUrl")

        val colorParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(0xFF0BA4DB.toInt()) // Paystack Primary Blue
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorParams)
            .setShowTitle(true)
            .build()

        try {
            // Standard launch
            customTabsIntent.launchUrl(context, uri)
            Log.i(TAG, "Successfully launched Paystack Checkout")
        } catch (e: Exception) {
            Log.e(TAG, "Custom Tab launch failed, attempting direct browser: ${e.message}")
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Final fallback failed: No browser found to handle URL")
                // If we're here, the device literally has no browser that can open a URL
                // In a real app, you might show a Toast or an error dialog
            }
        }
    }
}
