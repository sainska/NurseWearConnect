package com.example.nursewearconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import com.example.nursewearconnect.ui.components.InAppNotification

@Serializable
data class Notification(
    val id: String = "",
    val user_id: String? = null,
    val title: String = "",
    val body: String = "",
    val category: String = "general",
    val priority_level: String = "normal",
    val is_read: Boolean = false,
    val is_archived: Boolean = false,
    val created_at: String? = null
)

@Serializable
data class Message(
    val id: String? = null,
    val sender_id: String = "",
    val receiver_id: String? = null,
    val message: String = "",
    val image_url: String? = null,
    val priority: String = "normal",
    val category: String = "direct",
    val is_read: Boolean = false,
    val is_delivered: Boolean = false,
    val created_at: String? = null
)

@Serializable
data class Conversation(
    val last_message_id: String,
    val other_user_id: String,
    val other_user_name: String,
    val other_user_avatar: String? = null,
    val other_user_email: String? = null,
    val other_user_phone: String? = null,
    val last_message: String,
    val last_message_time: String,
    val last_message_priority: String,
    val unread_count: Int
)

class MessagingViewModel(private val supabase: SupabaseClient) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<Message>>(emptyList())
    val currentMessages: StateFlow<List<Message>> = _currentMessages.asStateFlow()

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    private val _activeTargetUserId = MutableStateFlow<String?>(null)
    val activeTargetUserId: StateFlow<String?> = _activeTargetUserId.asStateFlow()

    private val _newInAppNotification = MutableStateFlow<InAppNotification?>(null)
    val newInAppNotification: StateFlow<InAppNotification?> = _newInAppNotification.asStateFlow()

    fun dismissNotification() {
        _newInAppNotification.value = null
    }

    private var audioRecorder: com.example.nursewearconnect.utils.AndroidAudioRecorder? = null
    private var audioFile: java.io.File? = null

    fun startRecording(context: android.content.Context) {
        audioRecorder = com.example.nursewearconnect.utils.AndroidAudioRecorder(context)
        val file = java.io.File(context.cacheDir, "audio_record_${System.currentTimeMillis()}.mp4")
        audioFile = file
        audioRecorder?.start(file)
    }

    fun stopRecording(receiverId: String, context: android.content.Context) {
        audioRecorder?.stop()
        audioFile?.let { file ->
            uploadAudioFile(file, receiverId)
        }
        audioRecorder = null
    }

    private fun uploadAudioFile(file: java.io.File, receiverId: String) {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            val fileName = "${userId}/audio_${System.currentTimeMillis()}.mp4"
            
            try {
                val bytes = file.readBytes()
                val bucket = supabase.storage["chat-attachments"]
                bucket.upload(fileName, bytes)
                
                val publicUrl = bucket.publicUrl(fileName)
                // Using a special prefix or metadata to identify audio messages
                sendMessage(receiverId, "[Audio Message]", imageUrl = publicUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        loadInitialData()
        subscribeToRealtime()
        markAllAsDelivered()

        // Ensure data is refreshed if session becomes active after init
        viewModelScope.launch {
            supabase.auth.sessionStatus.collect { status ->
                if (status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated) {
                    refresh()
                    subscribeToRealtime() // Restart realtime with new userId
                }
            }
        }
    }

    private fun markAllAsDelivered() {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                supabase.postgrest.rpc("mark_messages_delivered", buildJsonObject {
                    put("receiver_uuid", userId)
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refresh() {
        loadInitialData()
        markAllAsDelivered()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            
            // Load Notifications
            try {
                val notifs = supabase.postgrest["notifications"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }.decodeList<Notification>()
                _notifications.value = notifs.sortedByDescending { it.created_at ?: "" }
                _unreadNotificationCount.value = notifs.count { !it.is_read && !it.is_archived }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Load Conversations
            try {
                val convos = supabase.postgrest["user_conversations"]
                    .select().decodeList<Conversation>()
                _conversations.value = convos.sortedByDescending { it.last_message_time }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var notificationsRealtimeJob: kotlinx.coroutines.Job? = null
    private var messagesRealtimeJob: kotlinx.coroutines.Job? = null

    @OptIn(SupabaseExperimental::class)
    private fun subscribeToRealtime() {
        // Cancel existing jobs
        notificationsRealtimeJob?.cancel()
        messagesRealtimeJob?.cancel()

        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            
            // Realtime Notifications
            notificationsRealtimeJob = launch {
                try {
                    supabase.postgrest["notifications"]
                        .selectAsFlow(Notification::id, filter = FilterOperation("user_id", FilterOperator.EQ, userId))
                        .collect { newList: List<Notification> ->
                            val oldList = _notifications.value
                            if (oldList.isNotEmpty() && newList.size > oldList.size) {
                                val newNotif = newList.maxByOrNull { it.created_at ?: "" }
                                if (newNotif != null) {
                                    _newInAppNotification.value = InAppNotification(
                                        title = newNotif.title,
                                        body = newNotif.body
                                    )
                                    // Use standard android log as fallback if UI isn't listening
                                    android.util.Log.i("MessagingVM", "New Notification: ${newNotif.title}")
                                }
                            }
                            _notifications.value = newList.sortedByDescending { it.created_at ?: "" }
                            _unreadNotificationCount.value = _notifications.value.count { !it.is_read && !it.is_archived }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("MessagingVM", "Notifications realtime error: ${e.message}")
                }
            }

            // Realtime Messages for Inbox updates
            messagesRealtimeJob = launch {
                try {
                    supabase.postgrest["messages"]
                        .selectAsFlow(Message::id, filter = FilterOperation("receiver_id", FilterOperator.EQ, userId))
                        .collect { newList ->
                            loadInitialData()
                            markAllAsDelivered()
                            
                            // If we got new messages, alert the UI
                            if (newList.isNotEmpty()) {
                                _newInAppNotification.value = InAppNotification(
                                    title = "New Message",
                                    body = "You have received a new message."
                                )
                                android.util.Log.i("MessagingVM", "New Message Alert Triggered")
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("MessagingVM", "Messages realtime error: ${e.message}")
                }
            }
        }
    }

    private var messageSubscriptionJob: kotlinx.coroutines.Job? = null

    @OptIn(SupabaseExperimental::class)
    fun loadMessages(otherUserId: String) {
        messageSubscriptionJob?.cancel()
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                // Mark messages as read when loading the conversation
                supabase.postgrest.rpc("mark_messages_read", buildJsonObject {
                    put("sender_uuid", otherUserId)
                    put("receiver_uuid", userId)
                })

                messageSubscriptionJob = launch {
                    supabase.postgrest["messages"]
                        .selectAsFlow(Message::id, filter = FilterOperation("sender_id", FilterOperator.EQ, userId))
                        .collect { fetchMessages(userId, otherUserId) }
                }
                
                launch {
                    supabase.postgrest["messages"]
                        .selectAsFlow(Message::id, filter = FilterOperation("receiver_id", FilterOperator.EQ, userId))
                        .collect { 
                            supabase.postgrest.rpc("mark_messages_read", buildJsonObject {
                                put("sender_uuid", otherUserId)
                                put("receiver_uuid", userId)
                            })
                            fetchMessages(userId, otherUserId) 
                        }
                }
                
                fetchMessages(userId, otherUserId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchMessages(userId: String, otherUserId: String) {
        val messages = supabase.postgrest["messages"]
            .select {
                filter {
                    or {
                        and {
                            eq("sender_id", userId)
                            eq("receiver_id", otherUserId)
                        }
                        and {
                            eq("sender_id", otherUserId)
                            eq("receiver_id", userId)
                        }
                    }
                }
            }.decodeList<Message>()
        _currentMessages.value = messages.sortedBy { it.created_at ?: "" }
    }

    fun sendMessage(receiverId: String, text: String, imageUrl: String? = null, priority: String = "normal") {
        viewModelScope.launch {
            val senderId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            
            // Format current time as ISO 8601 string safely
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val timestamp = sdf.format(java.util.Date())

            val msg = Message(
                sender_id = senderId,
                receiver_id = receiverId,
                message = text,
                image_url = imageUrl,
                priority = priority,
                created_at = timestamp
            )
            try {
                supabase.postgrest["messages"].insert(msg)
                loadInitialData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                supabase.postgrest["notifications"].update(
                    {
                        set("is_read", true)
                    }
                ) {
                    filter {
                        eq("id", notificationId)
                    }
                }
                loadInitialData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun archiveNotification(notificationId: String) {
        viewModelScope.launch {
            try {
                supabase.postgrest["notifications"].update(
                    {
                        set("is_archived", true)
                    }
                ) {
                    filter {
                        eq("id", notificationId)
                    }
                }
                loadInitialData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadChatImage(uri: android.net.Uri, receiverId: String, context: android.content.Context) {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            val fileName = "${userId}/${System.currentTimeMillis()}.jpg"
            
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                
                val bucket = supabase.storage["chat-attachments"]
                bucket.upload(fileName, bytes)
                
                val publicUrl = bucket.publicUrl(fileName)
                sendMessage(receiverId, "", imageUrl = publicUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun bulkAction(ids: List<String>, isRead: Boolean? = null, isArchived: Boolean? = null) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    putJsonArray("notification_ids") {
                        ids.forEach { add(it) }
                    }
                    if (isRead != null) put("new_is_read", isRead)
                    if (isArchived != null) put("new_is_archived", isArchived)
                }
                supabase.postgrest.rpc("bulk_update_notifications", params)
                loadInitialData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun openConversation(userId: String) {
        _activeTargetUserId.value = userId
        loadMessages(userId)
    }

    fun clearActiveTarget() {
        _activeTargetUserId.value = null
    }
}
