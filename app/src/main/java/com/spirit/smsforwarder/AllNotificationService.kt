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
	private val processRunnable = object : Runnable {
		override fun run() {
			val message = QueueSingleton.messageQueue.peek()
			if (message != null) {
				executorService.execute {
					val isSent = sendMessage(message)
					if (isSent) {
						message.isSent = true
						QueueSingleton.messageHistory.add(message)
						QueueSingleton.messageQueue.poll()
					} else {
						message.isError = true
					}
					broadcastMessage(message)
				}
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

	private fun sendMessage(message: MessageItem): Boolean {
		val sharedPreferences = getSharedPreferences("smsforwarder_prefs", MODE_PRIVATE)
		val telegramToken = sharedPreferences.getString("telegram_token", null)
		val telegramUserId = sharedPreferences.getString("telegram_user_id", null)

		if (telegramToken.isNullOrEmpty() || telegramUserId.isNullOrEmpty()) {
			return false
		}

		val urlString = "https://api.telegram.org/bot$telegramToken/sendMessage"
		val messageText = "<b>${message.sender} ${
			DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss").withZone(ZoneId.systemDefault()).format(
				Instant.ofEpochMilli(message.timestamp))}</b>\n<blockquote>${message.content}</blockquote>"
		val params = "chat_id=$telegramUserId&parse_mode=HTML&text=${URLEncoder.encode(messageText, "UTF-8")}"

		return try {
			val url = URL(urlString)
			val connection = url.openConnection() as HttpURLConnection
			connection.requestMethod = "POST"
			connection.doOutput = true
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

			responseCode == 200
		} catch (e: Exception) {
			e.printStackTrace()
			false
		}
	}

	private fun broadcastMessage(message: MessageItem) {
		val intent = Intent("com.spirit.smsforwarder.NEW_MESSAGE")
		intent.putExtra("messageItem", message)
		sendBroadcast(intent)
	}

	private fun createNotificationChannel() {
		val channel = NotificationChannel(
			"SMSForwarderNotificationChannel",
			"SMSForwarder Service",
			NotificationManager.IMPORTANCE_HIGH
		)
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(channel)
	}

	private fun createNotification(): Notification {
		val intent = Intent(this, MainActivity::class.java)
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
		val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

		return NotificationCompat.Builder(this, "SMSForwarderNotificationChannel")
			.setContentTitle("Listening for notifications")
			.setContentText("The SMSForwarder service is running")
			.setSmallIcon(R.drawable.small_icon)
			.setContentIntent(pendingIntent)  // Set the content intent
			.build()
	}
}
