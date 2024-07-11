package com.spirit.smsforwarder

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.spirit.smsforwarder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

	private lateinit var binding: ActivityMainBinding
	private val PERMISSIONS_REQUEST_CODE = 1
	private val permissions = arrayOf(
		android.Manifest.permission.FOREGROUND_SERVICE,
		android.Manifest.permission.READ_EXTERNAL_STORAGE,
		android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
		android.Manifest.permission.RECEIVE_SMS,
		android.Manifest.permission.READ_SMS,
		android.Manifest.permission.POST_NOTIFICATIONS,
		android.Manifest.permission.INTERNET,
		android.Manifest.permission.QUERY_ALL_PACKAGES,
		android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
	)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		if (!isNotificationServiceEnabled()) {
			// Prompt user to enable notification access
			val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
			startActivity(intent)
			Toast.makeText(this,
				"Please manually enable notification access for this app. It cannot be requested automatically.",
				Toast.LENGTH_LONG).show()
		}

		requestPermissions()

		val navView: BottomNavigationView = binding.navView

		val navController = findNavController(R.id.nav_host_fragment_activity_main)
		val appBarConfiguration = AppBarConfiguration(
			setOf(
				R.id.navigation_dashboard, R.id.navigation_configuration
			)
		)
		setupActionBarWithNavController(navController, appBarConfiguration)
		navView.setupWithNavController(navController)

		// Start background service
		startService(Intent(this, AllNotificationService::class.java))
	}

	private fun requestPermissions() {
		val permissionsToRequest = mutableListOf<String>()

		for (permission in permissions) {
			if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
				permissionsToRequest.add(permission)
			}
		}

		if (permissionsToRequest.isNotEmpty()) {
			ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSIONS_REQUEST_CODE)
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == PERMISSIONS_REQUEST_CODE) {
			// Handle the results of the permission requests if necessary
		}
	}

	private fun isNotificationServiceEnabled(): Boolean {
		val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
		val colonSplitter = TextUtils.SimpleStringSplitter(':').apply {setString(enabledListeners)}
		val componentName = ComponentName(this, NotificationListener::class.java)
		return colonSplitter.any { it == componentName.flattenToString() }
	}
}
