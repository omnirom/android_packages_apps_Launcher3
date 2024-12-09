/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep

import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.IgnoreLimit
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.launcher3.BuildConfig
import com.android.launcher3.ui.AbstractLauncherUiTest
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test

/** Test Desktop windowing in Overview. */
@AllowedDevices(allowed = [DeviceProduct.CF_TABLET, DeviceProduct.TANGORPRO])
@IgnoreLimit(ignoreLimit = BuildConfig.IS_STUDIO_BUILD)
class TaplTestsOverviewDesktop : AbstractLauncherUiTest<QuickstepLauncher?>() {
    @Before
    fun setup() {
        val overview = mLauncher.goHome().switchToOverview()
        if (overview.hasTasks()) {
            overview.dismissAllTasks()
        }
        startTestAppsWithCheck()
        mLauncher.goHome()
    }

    @Test
    @PortraitLandscape
    fun enterDesktopViaOverviewMenu() {
        // Move last launched TEST_ACTIVITY_2 into Desktop
        mLauncher.workspace
            .switchToOverview()
            .getTestActivityTask(TEST_ACTIVITY_2)
            .tapMenu()
            .tapDesktopMenuItem()
        assertTestAppLaunched(TEST_ACTIVITY_2)

        // Scroll back to TEST_ACTIVITY_1, then move it into Desktop
        mLauncher
            .goHome()
            .switchToOverview()
            .apply { flingForward() }
            .getTestActivityTask(TEST_ACTIVITY_1)
            .tapMenu()
            .tapDesktopMenuItem()
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }

        // Launch static DesktopTaskView
        val desktop =
            mLauncher.goHome().switchToOverview().getTestActivityTask(TEST_ACTIVITIES).open()
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }

        // Launch live-tile DesktopTaskView
        desktop.switchToOverview().getTestActivityTask(TEST_ACTIVITIES).open()
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }
    }

    private fun startTestAppsWithCheck() {
        TEST_ACTIVITIES.forEach {
            startTestActivity(it)
            executeOnLauncher { launcher ->
                assertWithMessage(
                        "Launcher activity is the top activity; expecting TestActivity$it"
                    )
                    .that(isInLaunchedApp(launcher))
                    .isTrue()
            }
        }
    }

    private fun assertTestAppLaunched(index: Int) {
        assertWithMessage("TestActivity$index not opened in Desktop")
            .that(
                mDevice.wait(
                    Until.hasObject(By.pkg(getAppPackageName()).text("TestActivity$index")),
                    DEFAULT_UI_TIMEOUT
                )
            )
            .isTrue()
    }

    companion object {
        const val TEST_ACTIVITY_1 = 2
        const val TEST_ACTIVITY_2 = 3
        val TEST_ACTIVITIES = listOf(TEST_ACTIVITY_1, TEST_ACTIVITY_2)
    }
}
