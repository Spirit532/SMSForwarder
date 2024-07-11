package com.spirit.smsforwarder.model

import java.io.Serializable

data class MessageItem(
	val content: String,
	val sender: String,
	val packageName: String,
	val timestamp: Long,
	var isSent: Boolean = false,
	var isError: Boolean = false
) : Serializable
