package com.spirit.smsforwarder.model

import java.util.concurrent.ConcurrentLinkedQueue

object QueueSingleton {
	val messageQueue: ConcurrentLinkedQueue<MessageItem> = ConcurrentLinkedQueue()
	val messageHistory: ConcurrentLinkedQueue<MessageItem> = ConcurrentLinkedQueue()

	fun containsMessage(item: MessageItem): Boolean {
		return messageQueue.any {
			it.content == item.content &&
					it.sender == item.sender &&
					it.timestamp == item.timestamp
		}
	}
}
