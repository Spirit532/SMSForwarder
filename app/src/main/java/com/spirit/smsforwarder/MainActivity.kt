package com.spirit.smsforwarder

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

	private val permissions = mutableListOf<String>().apply {
		add(android.Manifest.permission.INTERNET)
		add(android.Manifest.permission.RECEIVE_SMS)
		add(android.Manifest.permission.READ_SMS)
		// Add permissions conditionally based on API levels
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
			add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			add(android.Manifest.permission.FOREGROUND_SERVICE)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			add(android.Manifest.permission.QUERY_ALL_PACKAGES)
		}
		// Foreground service special use is only available on Android 14 and later
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			add(android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			add(android.Manifest.permission.POST_NOTIFICATIONS)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		if (!isNotificationServiceEnabled()) {
			// Prompt user to enable notification access
			val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
			startActivity(intent)
			Toast.makeText(this,
				"Please enable notification access for this app. It must be done manually.",
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
		val permissionsToShowRationale = mutableListOf<String>()

		for (permission in permissions) {
			if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
				permissionsToRequest.add(permission)
				if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
					permissionsToShowRationale.add(permission)
				}
			}
		}

		if (permissionsToRequest.isNotEmpty()) {
			if (permissionsToShowRationale.isNotEmpty()) {
				// Show rationale dialog if needed
				showRationaleDialog(permissionsToRequest.toTypedArray())
			} else {
				// Request permissions directly
				ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSIONS_REQUEST_CODE)
			}
		}
	}

	private fun showRationaleDialog(permissionsToRequest: Array<String>) {
		AlertDialog.Builder(this)
			.setTitle("Permissions Required")
			.setMessage("This app needs the following permissions to function properly: ${permissionsToRequest.joinToString(", ")}")
			.setPositiveButton("OK") { _, _ ->
				// Request permissions after showing rationale
				ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_CODE)
			}
			.setNegativeButton("Cancel") { dialog, _ ->
				dialog.dismiss()
				Toast.makeText(this, "Permissions are required for the app to work properly.", Toast.LENGTH_LONG).show()
			}
			.create()
			.show()
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == PERMISSIONS_REQUEST_CODE) {
			val permissionsToRequest = mutableListOf<String>()
			for ((index, permission) in permissions.withIndex()) {
				if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
					permissionsToRequest.add(permission)
				}
			}
			if (permissionsToRequest.isNotEmpty()) {
				Toast.makeText(this, "Some permissions were not granted. This may affect operation.", Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun isNotificationServiceEnabled(): Boolean {
		val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
		if (enabledListeners.isNullOrEmpty()) {
			return false
		}

		val colonSplitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledListeners) }
		val componentName = ComponentName(this, NotificationListener::class.java)
		return colonSplitter.any { it == componentName.flattenToString() }
	}
}
