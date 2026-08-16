package com.nhn.gps.location.phone.tracker.ui.splash

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
// import com.leansoft.ads.ui.activity.LeansoftSplashActivity
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.util.LanguageHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
class SplashActivity : AppCompatActivity() /* LeansoftSplashActivity() */ {
    @Inject lateinit var preferences: AppPreferences
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        // Immediately trigger navigation logic for now
        finishOnboarding(Bundle())
    }

    // override fun loadedRemoteConfig(isSuccess: Boolean) = Unit

    fun finishOnboarding(bundle: Bundle) {
        if (navigated) return
        navigated = true
        lifecycleScope.launch {
            val target = if (preferences.userId.first().isNullOrBlank() && !preferences.isPermissionShown.first()) {
                "permission"
            } else {
                "home"
            }
            startActivity(Intent(this@SplashActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("TARGET_DESTINATION", target)
            })
            finish()
        }
    }

    fun setLanguage(languageCode: String) {
        runBlocking { preferences.saveSelectedLanguage(languageCode) }
        LanguageHelper.setAppLanguage(this, languageCode)
    }

    // override fun getRemoteConfigDefault(): Int = R.xml.remote_config_defaults

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(newBase))
    }
}
