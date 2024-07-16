package com.spirit.smsforwarder

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.spirit.smsforwarder.model.MessageItem
import com.spirit.smsforwarder.model.QueueSingleton

class NotificationListener : NotificationListenerService() {

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		val excludedPackages = setOf("com.google.android.apps.messaging", "com.android.messaging", "com.spirit.smsforwarder", "com.xiaomi.discover") // avoids sms, own notifications, and other useless crud
		val packageName = sbn.packageName
		if (!excludedPackages.contains(packageName) && !getSharedPreferences("smsforwarder_prefs", 0).getBoolean("${packageName}_ignore_enabled", false)) {
			//Log.d("NotificationListener", "${packageName}_ignore_enabled = ${getSharedPreferences("smsforwarder_prefs", 0).getBoolean("${packageName}_ignore_enabled", false)}")
			val notification = sbn.notification
			val extras = notification.extras
			val appName = getAppName(packageName)
			val message = buildString {
				append("Title: ${extras.getCharSequence(Notification.EXTRA_TITLE)}\n")
				extras.getCharSequence(Notification.EXTRA_TEXT)?.let {
					append("Text: $it")
				}
				extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let {
					append("\n$it")
				}
				extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let {
					append("\n$it")
				}
				extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.let {
					append("\n$it")
				}
			}

			val msg = MessageItem(
				content = message,
				sender = appName,
				packageName = packageName,
				timestamp = sbn.postTime
			)

			// Check if the message with the same timestamp already exists
			if (!QueueSingleton.containsMessage(msg))
				QueueSingleton.messageQueue.add(msg)

		}
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification) {
		// Android 14 shenanigans
		if(sbn.packageName == "com.spirit.smsforwarder")
			QueueSingleton.notificationDismissed = true
		//Log.d("NotificationListener", "Notification dismissed: ${sbn.notification.extras.getString("android.text")}")
	}

	private fun getAppName(packageName: String): String {
		return try {
			val packageManager = packageManager
			val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
			packageManager.getApplicationLabel(applicationInfo).toString()
		} catch (e: PackageManager.NameNotFoundException) {
			packageName
		}
	}
}