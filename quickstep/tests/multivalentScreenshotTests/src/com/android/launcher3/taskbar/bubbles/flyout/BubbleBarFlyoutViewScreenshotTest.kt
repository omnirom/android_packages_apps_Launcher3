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

package com.android.launcher3.taskbar.bubbles.flyout

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.ViewScreenshotTestRule
import platform.test.screenshot.getEmulatedDevicePathConfig

/** Screenshot tests for [BubbleBarFlyoutView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class BubbleBarFlyoutViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() =
            DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isDarkTheme = false,
                isLandscape = false,
            )
    }

    @get:Rule
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun bubbleBarFlyoutView_noAvatar_onRight() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_noAvatar_onRight") { activity ->
            activity.actionBar?.hide()
            val flyout = BubbleBarFlyoutView(context, onLeft = false)
            flyout.setData(
                BubbleBarFlyoutMessage(
                    senderAvatar = null,
                    senderName = "sender",
                    message = "message",
                    isGroupChat = false,
                )
            )
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_noAvatar_onLeft() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_noAvatar_onLeft") { activity ->
            activity.actionBar?.hide()
            val flyout = BubbleBarFlyoutView(context, onLeft = true)
            flyout.setData(
                BubbleBarFlyoutMessage(
                    senderAvatar = null,
                    senderName = "sender",
                    message = "message",
                    isGroupChat = false,
                )
            )
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_noAvatar_longMessage() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_noAvatar_longMessage") { activity ->
            activity.actionBar?.hide()
            val flyout = BubbleBarFlyoutView(context, onLeft = true)
            flyout.setData(
                BubbleBarFlyoutMessage(
                    senderAvatar = null,
                    senderName = "sender",
                    message = "really, really, really, really, really long message. like really.",
                    isGroupChat = false,
                )
            )
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_avatar_onRight() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_avatar_onRight") { activity ->
            activity.actionBar?.hide()
            val flyout = BubbleBarFlyoutView(context, onLeft = false)
            flyout.setData(
                BubbleBarFlyoutMessage(
                    senderAvatar = ColorDrawable(Color.RED),
                    senderName = "sender",
                    message = "message",
                    isGroupChat = true,
                )
            )
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_avatar_onLeft() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_avatar_onLeft") { activity ->
            activity.actionBar?.hide()
            val flyout = BubbleBarFlyoutView(context, onLeft = true)
            flyout.setData(
                BubbleBarFlyoutMessage(
                    senderAvatar = ColorDrawable(Color.RED),
                    senderName = "sender",
                    message = "message",
                    isGroupChat = true,
                )
            )
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_avatar_longMessage() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_avatar_longMessage") { activity ->
            activity.actionBar?.hide()
            val flyout = BubbleBarFlyoutView(context, onLeft = true)
            flyout.setData(
                BubbleBarFlyoutMessage(
                    senderAvatar = ColorDrawable(Color.RED),
                    senderName = "sender",
                    message = "really, really, really, really, really long message. like really.",
                    isGroupChat = true,
                )
            )
            flyout
        }
    }
}
