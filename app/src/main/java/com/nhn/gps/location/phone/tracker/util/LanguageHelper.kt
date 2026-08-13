package com.nhn.gps.location.phone.tracker.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.LanguageModel
import java.util.Locale
import kotlinx.coroutines.runBlocking

object LanguageHelper {
    private data class Definition(
        val name: String,
        val nativeName: String,
        val code: String,
        @param:DrawableRes val flag: Int,
    )

    private val definitions = listOf(
        Definition("English", "English", "en", R.drawable.ic_english_menu),
        Definition("French", "Français", "fr", R.drawable.ic_french_menu),
        Definition("Marathi", "मराठी", "mr", R.drawable.ic_marathi_menu),
        Definition("Spanish", "Español", "es", R.drawable.ic_spanish_menu),
        Definition("Chinese", "中文", "zh", R.drawable.ic_chinese_menu),
        Definition("Portuguese (Portugal)", "Português", "pt-PT", R.drawable.ic_portuguese_portugal_menu),
        Definition("Russian", "Русский", "ru", R.drawable.ic_russian_menu),
        Definition("Indonesian", "Bahasa Indonesia", "id", R.drawable.ic_indonesian_menu),
        Definition("Filipino", "Filipino", "fil", R.drawable.ic_philippines_menu),
        Definition("Bangla", "বাংলা", "bn", R.drawable.ic_bangla_menu),
        Definition("Portuguese (Brazil)", "Português (Brasil)", "pt-BR", R.drawable.ic_portuguese_brazil_menu),
        Definition("Afrikaans", "Afrikaans", "af", R.drawable.ic_afrikaans_menu),
        Definition("German", "Deutsch", "de", R.drawable.ic_german_menu),
        Definition("English (Canada)", "English (Canada)", "en-CA", R.drawable.ic_canada_menu),
        Definition("English (UK)", "English (UK)", "en-GB", R.drawable.ic_english_uk_menu),
        Definition("Korean", "한국어", "ko", R.drawable.ic_korean_menu),
        Definition("Dutch", "Nederlands", "nl", R.drawable.ic_dutch_menu),
        Definition("Vietnamese", "Tiếng Việt", "vi", R.drawable.ic_vietnam_menu),
        Definition("Arabic", "العربية", "ar", R.drawable.ic_arabic_menu),
    )

    fun languages(): MutableList<LanguageModel> = definitions.map {
        LanguageModel(it.name, it.nativeName, it.code, it.flag)
    }.toMutableList()

    fun languageIndexFromCode(code: String): Int {
        val normalized = code.replace('_', '-').lowercase(Locale.US)
        val exact = definitions.indexOfFirst { it.code.lowercase(Locale.US) == normalized }
        if (exact >= 0) return exact
        val base = normalized.substringBefore('-')
        return definitions.indexOfFirst { it.code.substringBefore('-').lowercase(Locale.US) == base }
            .takeIf { it >= 0 } ?: 0
    }

    fun currentLanguageCode(context: Context): String = runBlocking {
        AppPreferences(context.applicationContext).getSelectedLanguage()
    }

    fun setAppLanguage(context: Context, code: String) {
        val locale = Locale.forLanguageTag(code.replace('_', '-'))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun wrapContext(context: Context): Context {
        val locale = Locale.forLanguageTag(currentLanguageCode(context).replace('_', '-'))
        Locale.setDefault(locale)
        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) },
        )
    }
}
