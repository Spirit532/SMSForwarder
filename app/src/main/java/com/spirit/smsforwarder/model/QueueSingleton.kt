package com.spirit.smsforwarder.model

import java.util.concurrent.ConcurrentLinkedQueue

object QueueSingleton {
	val messageQueue: ConcurrentLinkedQueue<MessageItem> = ConcurrentLinkedQueue()
	val messageHistory: ConcurrentLinkedQueue<MessageItem> = ConcurrentLinkedQueue()

	@Volatile
	var notificationDismissed = false

	@Volatile
	var pauseSendingUntil: Long = 0

	@Volatile
	var isListenerConnected = false

	fun containsMessage(item: MessageItem): Boolean {
		if(messageQueue.any{it.timestamp == item.timestamp})
			return true

		return messageQueue.any {it.content == item.content && it.sender == item.sender}
	}

	fun addToHistory(item: MessageItem) {
		messageHistory.add(item)
		while (messageHistory.size > 200) {
			messageHistory.poll()
		}
	}
}
