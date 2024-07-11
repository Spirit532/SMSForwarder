package com.spirit.smsforwarder.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.content.SharedPreferences

class ConfigurationViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences: SharedPreferences =
        getApplication<Application>().getSharedPreferences("smsforwarder_prefs", 0)

    private val _telegramToken = MutableLiveData<String>().apply {
        value = sharedPreferences.getString("telegram_token", "")
    }
    val telegramToken: LiveData<String> = _telegramToken

    private val _userId = MutableLiveData<String>().apply {
        value = sharedPreferences.getString("telegram_user_id", "")
    }
    val userId: LiveData<String> = _userId

    fun saveTelegramToken(token: String) {
        _telegramToken.value = token
        sharedPreferences.edit().putString("telegram_token", token).apply()
    }

    fun saveUserId(id: String) {
        _userId.value = id
        sharedPreferences.edit().putString("telegram_user_id", id).apply()
    }

    fun getAppEnabled(packageName: String): Boolean {
        return sharedPreferences.getBoolean("${packageName}_ignore_enabled", false)
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        sharedPreferences.edit().putBoolean("${packageName}_ignore_enabled", enabled).apply()
    }
}
