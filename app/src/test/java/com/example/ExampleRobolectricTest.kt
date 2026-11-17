package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.obd.i18n.AppLocalization
import com.example.obd.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  @Config(qualifiers = "ru")
  fun `read string from russian context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OBD2 Авто", appName)
  }

  @Test
  @Config(qualifiers = "en")
  fun `read string from english context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OBD2 Auto", appName)
  }

  @Test
  fun `appLocalization switches resource configuration without activity restart`() {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val ruContext = AppLocalization.localizedContext(base, AppLanguage.RU)
    val enContext = AppLocalization.localizedContext(base, AppLanguage.EN)

    assertEquals("OBD2 Авто", ruContext.getString(R.string.app_name))
    assertEquals("OBD2 Auto", enContext.getString(R.string.app_name))
    assertFalse(AppLocalization.isEnglish(AppLanguage.RU))
    assertTrue(AppLocalization.isEnglish(AppLanguage.EN))
  }
}
