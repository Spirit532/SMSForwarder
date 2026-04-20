package com.spirit.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
//import android.util.Log
import com.spirit.smsforwarder.model.MessageItem
import com.spirit.smsforwarder.model.QueueSingleton

class SmsReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
			//If android kills the core service, waking up via this manifest receiver should restart it
			try {
				androidx.core.content.ContextCompat.startForegroundService(
					context,
					Intent(context, AllNotificationService::class.java)
				)
			} catch (e: Exception) {
				e.printStackTrace()
			}

			val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
			for (message in messages) {
				//Log.d("SmsReceiver", "SMS received from ${message.displayOriginatingAddress}: ${message.displayMessageBody}")
				val msg = MessageItem(
					content = message.displayMessageBody,
					sender = "SMS from ${message.displayOriginatingAddress}",
					packageName = "SMS message",
					timestamp = message.timestampMillis
				)

				if (!QueueSingleton.containsMessage(msg)) {
					QueueSingleton.messageQueue.add(msg)
					QueueSingleton.wakeUp()
				}
			}
		}
	}
}
