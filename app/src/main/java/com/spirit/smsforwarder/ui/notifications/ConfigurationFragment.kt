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
	private var installedApps: List<AppItem> = emptyList()
	private var filteredApps: List<AppItem> = emptyList()
	private lateinit var appAdapter: AppListAdapter

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		configurationViewModel = ViewModelProvider(this).get(ConfigurationViewModel::class.java)
		_binding = FragmentConfigurationBinding.inflate(inflater, container, false)

		appAdapter = AppListAdapter(emptyList()) { packageName, isChecked ->
			configurationViewModel.setAppEnabled(packageName, isChecked)
		}
		binding.appListContainer.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
		binding.appListContainer.adapter = appAdapter

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
			val pm = requireContext().packageManager
			val newInstalledApps = withContext(Dispatchers.IO) {
				pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { packageInfo ->
					val appInfo = packageInfo.applicationInfo
					if (appInfo != null) {
						AppItem(
							appName = appInfo.loadLabel(pm).toString(),
							packageName = appInfo.packageName,
							isEnabled = configurationViewModel.getAppEnabled(appInfo.packageName)
						)
					} else {
						null
					}
				}
			}
			installedApps = newInstalledApps
			filteredApps = installedApps
			withContext(Dispatchers.Main) {
				populateAppList(filteredApps)
				showLoading(false)
				setupSearchAndSort()
			}
		}
	}

	private fun populateAppList(apps: List<AppItem>) {
		appAdapter.updateData(apps)
	}

	private fun showLoading(isLoading: Boolean) {
		binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
	}

	private fun filterAppListAsync(query: String?) {
		coroutineScope.launch {
			showLoading(true)
			val lowerCaseQuery = query?.lowercase() ?: ""
			filteredApps = withContext(Dispatchers.Default) {
				installedApps.filter { appItem ->
					appItem.appName.lowercase().contains(lowerCaseQuery)
				}
			}
			sortAppListAsync()
		}
	}

	private fun sortAppListAsync() {
		coroutineScope.launch {
			filteredApps = withContext(Dispatchers.Default) {
				when (binding.sortSpinner.selectedItem.toString()) {
					getString(R.string.enabled_first_search) -> filteredApps.sortedByDescending { it.isEnabled }
					getString(R.string.alphabetical_search) -> filteredApps.sortedBy { it.appName }
					getString(R.string.alphabetical_reverse) -> filteredApps.sortedByDescending { it.appName }
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

data class AppItem(
	val appName: String,
	val packageName: String,
	var isEnabled: Boolean
)

class AppListAdapter(
	private var apps: List<AppItem>,
	private val onToggleEnabled: (String, Boolean) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

	class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
		val appName: TextView = view.findViewById(R.id.appName)
		val appPackageName: TextView = view.findViewById(R.id.appPackageName)
		val appCheckbox: CheckBox = view.findViewById(R.id.appCheckbox)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
		return ViewHolder(view)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = apps[position]
		holder.appName.text = item.appName
		holder.appPackageName.text = item.packageName
		holder.appCheckbox.setOnCheckedChangeListener(null)
		holder.appCheckbox.isChecked = item.isEnabled
		holder.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
			item.isEnabled = isChecked
			onToggleEnabled(item.packageName, isChecked)
		}
	}

	override fun getItemCount() = apps.size

	fun updateData(newApps: List<AppItem>) {
		apps = newApps
		notifyDataSetChanged()
	}
}
