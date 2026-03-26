package com.spirit.smsforwarder

import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spirit.smsforwarder.model.MessageItem
import com.spirit.smsforwarder.model.QueueSingleton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class AllNotificationService : Service() {

	private val smsReceiver = SmsReceiver()
	private val handler = Handler(Looper.getMainLooper())
	@Volatile
	private var isProcessingMessage = false

	private var lastHealthCheckTime = System.currentTimeMillis()

	private val processRunnable = object : Runnable {
		override fun run() {
			val now = System.currentTimeMillis()

			checkNotificationServiceHealth()

			if (now < QueueSingleton.pauseSendingUntil) {
				handler.postDelayed(this, 1000)
				return
			}

			val fourteenDays = 14L * 24 * 60 * 60 * 1000
			while (true) {
				val peekMsg = QueueSingleton.messageQueue.peek()
				if (peekMsg != null && (now - peekMsg.timestamp) > fourteenDays) {
					QueueSingleton.messageQueue.poll()
				} else {
					break
				}
			}

			val message = QueueSingleton.messageQueue.peek()
			if (message != null) {
				if (!isProcessingMessage) {
					isProcessingMessage = true
					executorService.execute {
						try {
							val responseCode = sendMessage(message)
							if (responseCode == 200) {
								message.isSent = true
								QueueSingleton.addToHistory(message)
								QueueSingleton.messageQueue.poll()
							} else {
								message.isError = true
								if (responseCode == 429) {
									QueueSingleton.pauseSendingUntil = System.currentTimeMillis() + 3600_000L
									showRateLimitNotification()
								} else {
									QueueSingleton.pauseSendingUntil = System.currentTimeMillis() + 10_000L
								}
							}
							broadcastMessage(message)
						} finally {
							isProcessingMessage = false
						}
					}
				}
			}

			if(QueueSingleton.notificationDismissed)
			{
				QueueSingleton.notificationDismissed = false
				startForeground(1, createNotification())
			}

			handler.postDelayed(this, 1000)
		}
	}

	private val executorService = Executors.newSingleThreadExecutor()

	override fun onCreate() {
		super.onCreate()
		registerReceiver(smsReceiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
		startService(Intent(this, NotificationListener::class.java))
		handler.post(processRunnable)

		createNotificationChannel()
		startForeground(1, createNotification())
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(smsReceiver)
		stopService(Intent(this, NotificationListener::class.java))
		handler.removeCallbacks(processRunnable)
		executorService.shutdown()
	}

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	private fun sendMessage(message: MessageItem): Int {
		val sharedPreferences = getSharedPreferences("smsforwarder_prefs", MODE_PRIVATE)
		val telegramToken = sharedPreferences.getString("telegram_token", null)
		val telegramUserId = sharedPreferences.getString("telegram_user_id", null)

		if (telegramToken.isNullOrEmpty() || telegramUserId.isNullOrEmpty() || !telegramToken.contains(':')) {
			return -1
		}

		val urlString = "https://api.telegram.org/bot$telegramToken/sendMessage"
		val messageText = "<b>${message.sender} ${
			DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss").withZone(ZoneId.systemDefault()).format(
				Instant.ofEpochMilli(message.timestamp)
			)
		}</b>\n<blockquote>${message.content}</blockquote>"
		val params = "chat_id=$telegramUserId&parse_mode=HTML&text=${URLEncoder.encode(messageText, "UTF-8")}"

		return try {
			val url = URL(urlString)
			val connection = url.openConnection() as HttpURLConnection
			connection.requestMethod = "POST"
			connection.doOutput = true
			connection.connectTimeout = 15000
			connection.readTimeout = 15000
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

			val outputStream: OutputStream = connection.outputStream
			outputStream.write(params.toByteArray())
			outputStream.flush()
			outputStream.close()

			val responseCode = connection.responseCode
			val bufferedReader = if (responseCode == 200) {
				BufferedReader(InputStreamReader(connection.inputStream))
			} else {
				BufferedReader(InputStreamReader(connection.errorStream))
			}

			val response = bufferedReader.use(BufferedReader::readText)
			bufferedReader.close()

			Log.d("AllNotificationService", response)

			connection.disconnect()

			responseCode
		} catch (e: Exception) {
			e.printStackTrace()
			-1
		}
	}

	private fun broadcastMessage(message: MessageItem) {
		val intent = Intent("com.spirit.smsforwarder.NEW_MESSAGE")
		intent.setPackage(packageName)
		intent.putExtra("messageItem", message)
		sendBroadcast(intent)
	}

	private fun createNotificationChannel() {
		val channel = NotificationChannel(
			"SMSForwarderNotificationChannel",
			"SMSForwarder Service",
			NotificationManager.IMPORTANCE_HIGH
		).apply {
			description = "Channel for SMS Forwarder service notifications"
			setShowBadge(false)
			lockscreenVisibility = Notification.VISIBILITY_PUBLIC
		}

		val manager = getSystemService(NotificationManager::class.java)
		manager?.createNotificationChannel(channel)
	}

	fun createNotification(): Notification {
		val intent = Intent(this, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
		}
		val pendingIntent = PendingIntent.getActivity(
			this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		return NotificationCompat.Builder(this, "SMSForwarderNotificationChannel")
			.setContentTitle("Listening for notifications")
			.setContentText("The SMSForwarder service is running")
			.setSmallIcon(R.drawable.small_icon)
			.setContentIntent(pendingIntent)  // Set the content intent
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.build()
	}

	private fun showRateLimitNotification() {
		val channelId = "SMSForwarderRateLimitChannel"
		val manager = getSystemService(NotificationManager::class.java)

		val channel = NotificationChannel(
			channelId,
			"Rate Limit Alerts",
			NotificationManager.IMPORTANCE_HIGH
		).apply {
			description = "Alerts for API rate limits"
			lockscreenVisibility = Notification.VISIBILITY_PUBLIC
		}
		manager?.createNotificationChannel(channel)

		val notification = NotificationCompat.Builder(this, channelId)
			.setContentTitle("Telegram Rate Limit Hit!")
			.setContentText("Sending paused for 1 hour.")
			.setSmallIcon(R.drawable.small_icon)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setCategory(NotificationCompat.CATEGORY_ALARM)
			.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
			.build()

		manager?.notify(2, notification)
	}

	private fun checkNotificationServiceHealth() {
		val now = System.currentTimeMillis()
		if (now - lastHealthCheckTime < 60_000L) {
			return
		}
		lastHealthCheckTime = now

		val enabledListeners = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
		val isEnabledInSettings = enabledListeners?.let {
			val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':').apply { setString(it) }
			val componentName = android.content.ComponentName(this, NotificationListener::class.java)
			colonSplitter.any { name -> name == componentName.flattenToString() }
		} ?: false

		if (isEnabledInSettings && !QueueSingleton.isListenerConnected) {
			Log.w("AllNotificationService", "NotificationListenerService dead but enabled. Restarting component.")
			val pm = packageManager
			val componentName = android.content.ComponentName(this, NotificationListener::class.java)

			try {
				pm.setComponentEnabledSetting(
					componentName,
					android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
					android.content.pm.PackageManager.DONT_KILL_APP
				)
				
				pm.setComponentEnabledSetting(
					componentName,
					android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
					android.content.pm.PackageManager.DONT_KILL_APP
				)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}
}
