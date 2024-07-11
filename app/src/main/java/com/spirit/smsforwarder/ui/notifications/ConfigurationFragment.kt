package com.spirit.smsforwarder.ui.notifications

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.spirit.smsforwarder.R
import com.spirit.smsforwarder.databinding.FragmentConfigurationBinding
import kotlinx.coroutines.*

class ConfigurationFragment : Fragment() {

	private var _binding: FragmentConfigurationBinding? = null
	private val binding get() = _binding!!
	private lateinit var configurationViewModel: ConfigurationViewModel
	private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
	private var installedApps: List<PackageInfo> = emptyList()
	private var filteredApps: List<PackageInfo> = emptyList()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		configurationViewModel = ViewModelProvider(this).get(ConfigurationViewModel::class.java)
		_binding = FragmentConfigurationBinding.inflate(inflater, container, false)

		observeViewModel()
		loadApps()
		return binding.root
	}

	private fun setupSearchAndSort() {
		val sortOptions = arrayOf(getString(R.string.enabled_first_search), getString(R.string.alphabetical_search), getString(R.string.alphabetical_reverse))
		binding.sortSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions).apply {
			setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
		}
		binding.sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
				sortAppListAsync()
			}
			override fun onNothingSelected(parent: AdapterView<*>) {}
		}
		binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(query: String?): Boolean {
				filterAppListAsync(query)
				return true
			}
			override fun onQueryTextChange(newText: String?): Boolean {
				filterAppListAsync(newText)
				return true
			}
		})
	}

	private fun observeViewModel() {
		configurationViewModel.telegramToken.observe(viewLifecycleOwner) {
			if (binding.telegramTokenInput.text.toString() != it) binding.telegramTokenInput.setText(it)
		}
		configurationViewModel.userId.observe(viewLifecycleOwner) {
			if (binding.whoToMessageID.text.toString() != it) binding.whoToMessageID.setText(it)
		}
		binding.telegramTokenInput.addTextChangedListener(SimpleTextWatcher { configurationViewModel.saveTelegramToken(it) })
		binding.whoToMessageID.addTextChangedListener(SimpleTextWatcher { configurationViewModel.saveUserId(it) })
	}

	private fun loadApps() {
		showLoading(true)
		coroutineScope.launch {
			withContext(Dispatchers.IO) {
				installedApps = requireContext().packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
			}
			filteredApps = installedApps
			withContext(Dispatchers.Main) {
				populateAppList(filteredApps)
				showLoading(false)
				setupSearchAndSort()
			}
		}
	}

	private fun populateAppList(apps: List<PackageInfo>) {
		binding.appListContainer.apply {
			removeAllViews()
			val packageManager = requireContext().packageManager
			val inflater = LayoutInflater.from(requireContext())
			apps.forEach { packageInfo ->
				val appInfo = packageInfo.applicationInfo
				val view = inflater.inflate(R.layout.item_app, this, false)
				val appName = view.findViewById<TextView>(R.id.appName)
				val appPackageName = view.findViewById<TextView>(R.id.appPackageName)
				val appCheckbox = view.findViewById<CheckBox>(R.id.appCheckbox)
				appName.text = appInfo.loadLabel(packageManager)
				appPackageName.text = appInfo.packageName
				appCheckbox.isChecked = configurationViewModel.getAppEnabled(appInfo.packageName)
				appCheckbox.setOnCheckedChangeListener { _, isChecked ->
					configurationViewModel.setAppEnabled(appInfo.packageName, isChecked)
				}
				addView(view)
			}
		}
	}

	private fun showLoading(isLoading: Boolean) {
		binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
	}

	private fun filterAppListAsync(query: String?) {
		coroutineScope.launch {
			showLoading(true)
			val lowerCaseQuery = query?.lowercase() ?: ""
			filteredApps = withContext(Dispatchers.Default) {
				installedApps.filter { packageInfo ->
					packageInfo.applicationInfo.loadLabel(requireContext().packageManager).toString().lowercase().contains(lowerCaseQuery)
				}
			}
			sortAppListAsync()
		}
	}

	private fun sortAppListAsync() {
		coroutineScope.launch {
			filteredApps = withContext(Dispatchers.Default) {
				when (binding.sortSpinner.selectedItem.toString()) {
					getString(R.string.enabled_first_search) -> filteredApps.sortedByDescending { configurationViewModel.getAppEnabled(it.packageName) }
					getString(R.string.alphabetical_search) -> filteredApps.sortedBy { it.applicationInfo.loadLabel(requireContext().packageManager).toString() }
					getString(R.string.alphabetical_reverse) -> filteredApps.sortedByDescending { it.applicationInfo.loadLabel(requireContext().packageManager).toString() }
					else -> filteredApps
				}
			}
			populateAppList(filteredApps)
			showLoading(false)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
		coroutineScope.cancel()
	}
}

class SimpleTextWatcher(private val onTextChanged: (String) -> Unit) : TextWatcher {
	override fun afterTextChanged(s: Editable?) {
		s?.toString()?.let(onTextChanged)
	}
	override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
	override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}
