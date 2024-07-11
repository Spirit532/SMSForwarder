package com.spirit.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
			val serviceIntent = Intent(context, AllNotificationService::class.java)
			context.startService(serviceIntent)
		}
	}
}