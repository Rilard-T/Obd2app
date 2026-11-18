package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun distributionChannel_buildConfigFlags_areConsistent() {
    assertTrue(BuildConfig.DISTRIBUTION_CHANNEL == "play" || BuildConfig.DISTRIBUTION_CHANNEL == "github")
    assertEquals(BuildConfig.DISTRIBUTION_CHANNEL == "github", BuildConfig.IS_GITHUB_BUILD)
    assertEquals(BuildConfig.IS_GITHUB_BUILD, BuildConfig.ALLOW_AA_NAVIGATION_SURFACE)
  }
}
