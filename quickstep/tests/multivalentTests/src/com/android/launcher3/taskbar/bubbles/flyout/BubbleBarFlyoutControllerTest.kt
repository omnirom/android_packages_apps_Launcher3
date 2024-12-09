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
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubbleBarFlyoutController] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarFlyoutControllerTest {

    private lateinit var flyoutController: BubbleBarFlyoutController
    private lateinit var flyoutContainer: FrameLayout
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val flyoutMessage =
        BubbleBarFlyoutMessage(senderAvatar = null, "sender name", "message", isGroupChat = false)
    private var onLeft = true

    @Before
    fun setUp() {
        flyoutContainer = FrameLayout(context)
        val positioner =
            object : BubbleBarFlyoutPositioner {
                override val isOnLeft: Boolean
                    get() = onLeft

                override val targetTy: Float
                    get() = 50f
            }
        flyoutController = BubbleBarFlyoutController(flyoutContainer, positioner)
    }

    @Test
    fun flyoutPosition_left() {
        flyoutController.setUpFlyout(flyoutMessage)
        assertThat(flyoutContainer.childCount).isEqualTo(1)
        val flyout = flyoutContainer.getChildAt(0)
        val lp = flyout.layoutParams as FrameLayout.LayoutParams
        assertThat(lp.gravity).isEqualTo(Gravity.BOTTOM or Gravity.LEFT)
        assertThat(flyout.translationY).isEqualTo(50f)
    }

    @Test
    fun flyoutPosition_right() {
        onLeft = false
        flyoutController.setUpFlyout(flyoutMessage)
        assertThat(flyoutContainer.childCount).isEqualTo(1)
        val flyout = flyoutContainer.getChildAt(0)
        val lp = flyout.layoutParams as FrameLayout.LayoutParams
        assertThat(lp.gravity).isEqualTo(Gravity.BOTTOM or Gravity.RIGHT)
        assertThat(flyout.translationY).isEqualTo(50f)
    }

    @Test
    fun flyoutMessage() {
        flyoutController.setUpFlyout(flyoutMessage)
        assertThat(flyoutContainer.childCount).isEqualTo(1)
        val flyout = flyoutContainer.getChildAt(0)
        val sender = flyout.findViewById<TextView>(R.id.bubble_flyout_name)
        assertThat(sender.text).isEqualTo("sender name")
        val message = flyout.findViewById<TextView>(R.id.bubble_flyout_text)
        assertThat(message.text).isEqualTo("message")
    }

    @Test
    fun hideFlyout_removedFromContainer() {
        flyoutController.setUpFlyout(flyoutMessage)
        assertThat(flyoutContainer.childCount).isEqualTo(1)
        flyoutController.hideFlyout()
        assertThat(flyoutContainer.childCount).isEqualTo(0)
    }
}
