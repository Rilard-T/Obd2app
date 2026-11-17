package com.example.obd.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.obd.model.AppLanguage
import java.util.Locale

val LocalL10nContext = staticCompositionLocalOf<Context?> { null }

@Composable
fun l10nContext(): Context = LocalL10nContext.current ?: LocalContext.current

@Composable
fun l10nStr(@StringRes id: Int): String = l10nContext().getString(id)

@Composable
fun l10nStr(@StringRes id: Int, vararg formatArgs: Any): String =
    l10nContext().getString(id, *formatArgs)

object AppLocalization {

    private val RU_LOCALE: Locale = Locale.forLanguageTag("ru-RU")
    private val EN_LOCALE: Locale = Locale.US

    fun resolveLocale(language: AppLanguage): Locale {
        return when (language) {
            AppLanguage.RU -> RU_LOCALE
            AppLanguage.EN -> EN_LOCALE
            AppLanguage.SYSTEM -> {
                val sysLang = Locale.getDefault().language.lowercase(Locale.ROOT)
                if (sysLang == "ru") RU_LOCALE else EN_LOCALE
            }
        }
    }

    fun isEnglish(language: AppLanguage): Boolean {
        return when (language) {
            AppLanguage.RU -> false
            AppLanguage.EN -> true
            AppLanguage.SYSTEM -> Locale.getDefault().language.lowercase(Locale.ROOT) != "ru"
        }
    }

    fun localizedContext(base: Context, language: AppLanguage): Context {
        val targetLocale = resolveLocale(language)
        val config = Configuration(base.resources.configuration)
        config.setLocale(targetLocale)
        return base.createConfigurationContext(config)
    }
}
