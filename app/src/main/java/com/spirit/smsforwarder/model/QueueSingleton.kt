package com.spirit.smsforwarder.model

import java.util.concurrent.ConcurrentLinkedQueue

object QueueSingleton {
	val messageQueue: ConcurrentLinkedQueue<MessageItem> = ConcurrentLinkedQueue()
	val messageHistory: ConcurrentLinkedQueue<MessageItem> = ConcurrentLinkedQueue()

	@Volatile
	var notificationDismissed = false

	fun containsMessage(item: MessageItem): Boolean {
		if(messageQueue.any{it.timestamp == item.timestamp})
			return true

		return messageQueue.any {it.content == item.content && it.sender == item.sender}
	}
}
