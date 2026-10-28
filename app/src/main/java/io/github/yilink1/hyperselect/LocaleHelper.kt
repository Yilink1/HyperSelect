package io.github.yilink1.hyperselect

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Simple persisted in-app language helper (English / Chinese). */
object LocaleHelper {
    private const val PREFS = "hyper_select_prefs"
    private const val KEY_LANG = "app_language"

    const val EN = "en"
    const val ZH = "zh"

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, EN) ?: EN

    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang).apply()
        Locale.setDefault(Locale(lang))
    }

    /** Wrap a base context so the chosen locale is used for resource resolution. */
    fun wrap(base: Context): Context {
        val locale = Locale(getLanguage(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun displayName(context: Context): String =
        if (getLanguage(context) == ZH) "中文" else "English"
}
