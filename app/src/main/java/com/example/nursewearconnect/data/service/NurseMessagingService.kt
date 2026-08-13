package com.example.nursewearconnect.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.nursewearconnect.MainActivity
import com.example.nursewearconnect.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NurseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            sendNotification(it.title ?: "NurseWear Connect", it.body ?: "")
        } ?: run {
            // Handle data payload if notification is null
            val title = remoteMessage.data["title"] ?: "NurseWear Connect"
            val body = remoteMessage.data["body"] ?: ""
            if (body.isNotEmpty()) {
                sendNotification(title, body)
            }
        }
    }

    override fun onNewToken(token: String) {
        // Here you would typically upload the token to your backend (Supabase profiles)
        // For now we'll just log it.
        android.util.Log.d("FCM", "New token: $token")
        saveTokenToBackend(token)
    }

    private fun saveTokenToBackend(token: String) {
        // We broadcast this to the MainActivity or any active ViewModel to update the profile
        val intent = Intent("com.example.nursewearconnect.FCM_TOKEN_UPDATE")
        intent.putExtra("token", token)
        intent.setPackage(packageName)
        applicationContext.sendBroadcast(intent)
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Determine channel based on content
        val isMessage = title.contains("New Message", ignoreCase = true) || messageBody.contains("sent you a message", ignoreCase = true)
        val channelId = if (isMessage) "chat_messages_channel" else "order_updates_channel"
        val channelName = if (isMessage) "Chat Messages" else "Order and System Updates"

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF0D9488.toInt())
            .setCategory(if (isMessage) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                if (isMessage) {
                    enableVibration(true)
                    setShowBadge(true)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
