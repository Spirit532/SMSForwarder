package com.spirit.smsforwarder.ui.dashboard

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
//import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.spirit.smsforwarder.R
import com.spirit.smsforwarder.databinding.FragmentDashboardBinding
import com.spirit.smsforwarder.model.MessageItem
import com.spirit.smsforwarder.model.QueueSingleton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

class DashboardFragment : Fragment() {

	private var _binding: FragmentDashboardBinding? = null
	private val binding get() = _binding!!
	private lateinit var messageContainer: LinearLayout
	private lateinit var dashboardViewModel: DashboardViewModel

	private val messageReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			displayMessages()
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		dashboardViewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)
		_binding = FragmentDashboardBinding.inflate(inflater, container, false)
		val root: View = binding.root
		messageContainer = binding.messageContainer

		return root
	}

	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	override fun onResume() {
		super.onResume()
		val intentFilter = IntentFilter("com.spirit.smsforwarder.NEW_MESSAGE")
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			context?.registerReceiver(messageReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
		} else {
			context?.registerReceiver(messageReceiver, intentFilter)
		}
		displayMessages()
	}

	override fun onPause() {
		super.onPause()
		context?.unregisterReceiver(messageReceiver)
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	private fun displayMessages() {
		messageContainer.removeAllViews()

		fun addMessagesInReverse(messages: ConcurrentLinkedQueue<MessageItem>) {
			val messagesList = messages.toList()
			for (message in messagesList.asReversed()) {
				val messageView = layoutInflater.inflate(R.layout.item_message, messageContainer, false)
				messageView.findViewById<TextView>(R.id.messageContent).text = message.content
				messageView.findViewById<TextView>(R.id.messageSender).text = message.sender
				messageView.findViewById<TextView>(R.id.messageTimestamp).text =
					DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(message.timestamp))

				val backgroundDrawable = when {
					message.isSent -> R.drawable.rounded_background_sent
					message.isError -> R.drawable.rounded_background_error
					else -> R.drawable.rounded_background
				}

				messageView.background = ContextCompat.getDrawable(requireContext(), backgroundDrawable)

				messageView.setOnClickListener {
					Toast.makeText(requireContext(), "Origin:\n${message.packageName}", Toast.LENGTH_LONG).show()
				}

				messageContainer.addView(messageView)
			}
		}

		addMessagesInReverse(QueueSingleton.messageQueue)
		addMessagesInReverse(QueueSingleton.messageHistory)
	}

}