package com.nhn.gps.location.phone.tracker.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.leansoft.ads.ui.activity.LeansoftSplashActivity
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.util.LanguageHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : LeansoftSplashActivity() {
    @Inject lateinit var preferences: AppPreferences
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        synchronizeApplicationLocale()
        synchronizeInitialFlowState()
    }

    override fun loadedRemoteConfig(isSuccess: Boolean) = Unit

    override fun finishOnboarding(bundle: Bundle) {
        if (navigated) return
        navigated = true
        lifecycleScope.launch {
            preferences.setOnboardingCompleted()
            val target = when {
                !preferences.isPermissionShown.first() -> "permission"
                preferences.userId.first().isNullOrBlank() -> "setup_profile"
                else -> "home"
            }
            startActivity(Intent(this@SplashActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("TARGET_DESTINATION", target)
            })
            finish()
        }
    }

    override fun setLanguage(languageCode: String) {
        val normalized = LanguageHelper.normalizeSupportedLanguageCode(languageCode)
        lifecycleScope.launch { preferences.saveSelectedLanguage(normalized) }
        LanguageHelper.setAppLanguage(normalized)
    }

    override fun getRemoteConfigDefault(): Int = R.xml.remote_config_defaults

    private fun synchronizeInitialFlowState() {
        lifecycleScope.launch {
            val hasProfile = !preferences.userId.first().isNullOrBlank()
            if (hasProfile) {
                // Migrate users created before the app-owned onboarding flag existed.
                if (!preferences.isOnboardingCompleted.first()) {
                    preferences.setOnboardingCompleted()
                }
            } else if (!preferences.isOnboardingCompleted.first()) {
                // The SDK stores these flags in SharedPreferences. Reset them for an
                // unfinished first run so Language and Onboarding cannot be skipped by
                // stale/restored SDK preferences.
                getSharedPreferences(LEANSOFT_PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_LANGUAGE_ONBOARD, false)
                    .putBoolean(KEY_INTRO_SHOWN, false)
                    .apply()
            }
        }
    }

    private fun synchronizeApplicationLocale() {
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) return
        lifecycleScope.launch {
            val stored = LanguageHelper.normalizeSupportedLanguageCode(preferences.getSelectedLanguage())
            LanguageHelper.setAppLanguage(stored)
        }
    }

    private companion object {
        const val LEANSOFT_PREFERENCES = "leansoft_pref_app"
        const val KEY_LANGUAGE_ONBOARD = "languageOnboard"
        const val KEY_INTRO_SHOWN = "isIntroShow"
    }
}
