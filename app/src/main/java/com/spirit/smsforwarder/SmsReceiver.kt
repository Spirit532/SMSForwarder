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
			val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
			for (message in messages) {
				//Log.d("SmsReceiver", "SMS received from ${message.displayOriginatingAddress}: ${message.displayMessageBody}")
				val msg = MessageItem(
					content = message.displayMessageBody,
					sender = "SMS from ${message.displayOriginatingAddress}",
					packageName = "SMS message",
					timestamp = message.timestampMillis
				)

				if (!QueueSingleton.containsMessage(msg))
					QueueSingleton.messageQueue.add(msg)
			}
		}
	}
}
