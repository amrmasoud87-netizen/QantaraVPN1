package com.qantara.vpn.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    fun isArabic(): Boolean {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val lang = if (!appLocales.isEmpty) {
            appLocales[0]?.language
        } else {
            Locale.getDefault().language
        }
        return lang == "ar"
    }

    fun toggleLanguage() {
        val next = if (isArabic()) "en" else "ar"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
    }

    fun setLanguage(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }
}
