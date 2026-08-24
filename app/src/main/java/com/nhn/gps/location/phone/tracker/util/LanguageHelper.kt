package com.nhn.gps.location.phone.tracker.util

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.LanguageModel
import java.util.Locale

object LanguageHelper {
    fun setAppLanguage(languageCode: String) {
        val normalized = normalizeSupportedLanguageCode(languageCode)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
    }

    fun wrapContext(context: Context): Context {
        // AppCompat owns application locale configuration. Do not create a second
        // manually localized context, which can diverge from Fragment/View contexts.
        return context
    }

    fun currentLanguageCode(context: Context): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val appTag = appLocales.get(0)?.toLanguageTag().orEmpty()
        return appTag.takeIf(String::isNotBlank)?.let(::normalizeSupportedLanguageCode)
            ?: Locale.ENGLISH.language
    }

    fun getLocalizedString(context: Context, @StringRes resId: Int, languageCode: String): String {
        return context.getString(resId)
    }

    fun languageIndexFromCode(languageCode: String): Int {
        val normalizedCode = normalizeLanguageCode(languageCode)
        val exactIndex = definitions.indexOfFirst {
            normalizeLanguageCode(it.languageCode) == normalizedCode
        }
        if (exactIndex >= 0) return exactIndex

        val baseCode = normalizedCode.substringBefore('-')
        return definitions.indexOfFirst {
            normalizeLanguageCode(it.languageCode).substringBefore('-') == baseCode
        }.takeIf { it >= 0 } ?: 0
    }

    fun getListLanguage(context: Context): MutableList<LanguageModel> = definitions.map { definition ->
        definition.toModel(
            name = context.getString(definition.nameRes),
            nativeName = context.getString(definition.nativeNameRes),
        )
    }.toMutableList()

    fun getSettingsLanguageList(context: Context): MutableList<LanguageModel> = definitions.map { definition ->
        definition.toModel(
            name = context.getString(definition.nameRes),
            nativeName = context.getString(definition.nativeNameRes),
        )
    }.toMutableList()

    fun languageDisplayName(context: Context, languageCode: String): String {
        val index = languageIndexFromCode(languageCode)
        return getSettingsLanguageList(context).getOrNull(index)?.nativeName
            ?: Locale.ENGLISH.displayLanguage
    }

    private fun normalizeLanguageCode(languageCode: String): String =
        toLanguageTag(languageCode).lowercase(Locale.US)

    private fun toLanguageTag(languageCode: String): String = languageCode
        .trim()
        .replace("_", "-")
        .replace("-r", "-")
        .ifBlank { Locale.ENGLISH.language }

    fun normalizeSupportedLanguageCode(languageCode: String): String =
        definitions[languageIndexFromCode(languageCode)].languageCode

    private fun localeFromCode(languageCode: String): Locale =
        Locale.forLanguageTag(toLanguageTag(languageCode))

    private data class LanguageDefinition(
        @param:StringRes val nameRes: Int,
        @param:StringRes val nativeNameRes: Int,
        val languageCode: String,
        @param:DrawableRes val flagIconRes: Int,
    ) {
        fun toModel(name: String, nativeName: String) = LanguageModel(
            name = name,
            nativeName = nativeName,
            languageCode = languageCode,
            flagIconRes = flagIconRes,
        )
    }

    private val definitions = listOf(
        LanguageDefinition(R.string.language_english, R.string.language_native_english, "en", R.drawable.ic_english_menu),
        LanguageDefinition(R.string.language_french, R.string.language_native_french, "fr", R.drawable.ic_french_menu),
        LanguageDefinition(R.string.language_marathi, R.string.language_native_marathi, "mr", R.drawable.ic_marathi_menu),
        LanguageDefinition(R.string.language_spanish, R.string.language_native_spanish, "es", R.drawable.ic_spanish_menu),
        LanguageDefinition(R.string.language_chinese, R.string.language_native_chinese, "zh", R.drawable.ic_chinese_menu),
        LanguageDefinition(R.string.language_portuguese_portugal, R.string.language_native_portuguese_portugal, "pt-PT", R.drawable.ic_portuguese_portugal_menu),
        LanguageDefinition(R.string.language_russian, R.string.language_native_russian, "ru", R.drawable.ic_russian_menu),
        LanguageDefinition(R.string.language_indonesian, R.string.language_native_indonesian, "id", R.drawable.ic_indonesian_menu),
        LanguageDefinition(R.string.language_philippines, R.string.language_native_philippines, "fil", R.drawable.ic_philippines_menu),
        LanguageDefinition(R.string.language_bangla, R.string.language_native_bangla, "bn", R.drawable.ic_bangla_menu),
        LanguageDefinition(R.string.language_portuguese_brazil, R.string.language_native_portuguese_brazil, "pt-BR", R.drawable.ic_portuguese_brazil_menu),
        LanguageDefinition(R.string.language_afrikaans, R.string.language_native_afrikaans, "af", R.drawable.ic_afrikaans_menu),
        LanguageDefinition(R.string.language_german, R.string.language_native_german, "de", R.drawable.ic_german_menu),
        LanguageDefinition(R.string.language_canada, R.string.language_native_canada, "en-CA", R.drawable.ic_canada_menu),
        LanguageDefinition(R.string.language_english_uk, R.string.language_native_english_uk, "en-GB", R.drawable.ic_english_uk_menu),
        LanguageDefinition(R.string.language_korean, R.string.language_native_korean, "ko", R.drawable.ic_korean_menu),
        LanguageDefinition(R.string.language_dutch, R.string.language_native_dutch, "nl", R.drawable.ic_dutch_menu),
        LanguageDefinition(R.string.language_vietnamese, R.string.language_native_vietnamese, "vi", R.drawable.ic_vietnam_menu),
        LanguageDefinition(R.string.language_arabic, R.string.language_native_arabic, "ar", R.drawable.ic_arabic_menu),
    )
}
