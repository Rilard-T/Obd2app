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

  @Test
  @Suppress("DEPRECATION")
  fun `manifest car app permissions and category match distribution channel`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = context.packageManager
    val pkgInfo = pm.getPackageInfo(
      context.packageName,
      android.content.pm.PackageManager.GET_PERMISSIONS
    )
    val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()

    val navIntent = android.content.Intent("androidx.car.app.CarAppService")
      .addCategory("androidx.car.app.category.NAVIGATION")
      .setPackage(context.packageName)
    val iotIntent = android.content.Intent("androidx.car.app.CarAppService")
      .addCategory("androidx.car.app.category.IOT")
      .setPackage(context.packageName)

    val navResolved = pm.queryIntentServices(navIntent, 0)
    val iotResolved = pm.queryIntentServices(iotIntent, 0)

    if (BuildConfig.IS_GITHUB_BUILD) {
      assertTrue("GitHub build must declare NAVIGATION_TEMPLATES", "androidx.car.app.NAVIGATION_TEMPLATES" in perms)
      assertTrue("GitHub build must declare ACCESS_SURFACE", "androidx.car.app.ACCESS_SURFACE" in perms)
      assertTrue("GitHub build must resolve CarAppService as NAVIGATION", navResolved.isNotEmpty())
      assertTrue("GitHub build uses NAVIGATION instead of IOT", iotResolved.isEmpty())
    } else {
      assertFalse("Google Play build must NOT declare NAVIGATION_TEMPLATES", "androidx.car.app.NAVIGATION_TEMPLATES" in perms)
      assertFalse("Google Play build must NOT declare ACCESS_SURFACE", "androidx.car.app.ACCESS_SURFACE" in perms)
      assertTrue("Google Play build must NOT resolve CarAppService as NAVIGATION", navResolved.isEmpty())
      assertTrue("Google Play build must resolve CarAppService as IOT", iotResolved.isNotEmpty())
    }
  }
}
