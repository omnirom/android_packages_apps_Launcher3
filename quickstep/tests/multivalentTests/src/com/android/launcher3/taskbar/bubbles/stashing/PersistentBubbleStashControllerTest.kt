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

package com.android.launcher3.taskbar.bubbles.stashing

import android.animation.AnimatorTestRule
import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.taskbar.TaskbarInsetsController
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.util.MultiValueAlpha
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Unit tests for [PersistentBubbleStashController]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PersistentBubbleStashControllerTest {

    companion object {
        const val BUBBLE_BAR_HEIGHT = 100f
        const val HOTSEAT_TRANSLATION_Y = -45f
        const val TASK_BAR_TRANSLATION_Y = -5f
    }

    @get:Rule val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var bubbleBarView: BubbleBarView

    @Mock lateinit var bubbleBarViewController: BubbleBarViewController

    @Mock lateinit var taskbarInsetsController: TaskbarInsetsController

    private lateinit var persistentTaskBarStashController: PersistentBubbleStashController
    private lateinit var translationY: AnimatedFloat
    private lateinit var scale: AnimatedFloat
    private lateinit var alpha: MultiValueAlpha

    @Before
    fun setUp() {
        persistentTaskBarStashController =
            PersistentBubbleStashController(DefaultDimensionsProvider())
        setUpBubbleBarView()
        setUpBubbleBarController()
        persistentTaskBarStashController.init(
            taskbarInsetsController,
            bubbleBarViewController,
            null,
            ImmediateAction()
        )
    }

    @Test
    fun setBubblesShowingOnHomeUpdatedToFalse_barPositionYUpdated_controllersNotified() {
        // Given bubble bar is on home and has bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.isBubblesShowingOnHome = true
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        // When switch out of the home screen
        getInstrumentation().runOnMainSync {
            persistentTaskBarStashController.isBubblesShowingOnHome = false
        }

        // Then translation Y is animating and the bubble bar controller is notified
        assertThat(translationY.isAnimating).isTrue()
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)
        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_TRANSLATION_DURATION)
        // Check translation Y is correct and the insets controller is notified
        assertThat(bubbleBarView.translationY).isEqualTo(TASK_BAR_TRANSLATION_Y)
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun setBubblesShowingOnHomeUpdatedToTrue_barPositionYUpdated_controllersNotified() {
        // Given bubble bar has bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        // When switch to home screen
        getInstrumentation().runOnMainSync {
            persistentTaskBarStashController.isBubblesShowingOnHome = true
        }

        // Then translation Y is animating and the bubble bar controller is notified
        assertThat(translationY.isAnimating).isTrue()
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)
        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_TRANSLATION_DURATION)

        // Check translation Y is correct and the insets controller is notified
        assertThat(bubbleBarView.translationY).isEqualTo(HOTSEAT_TRANSLATION_Y)
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun setBubblesShowingOnOverviewUpdatedToFalse_controllersNotified() {
        // Given bubble bar is on overview
        persistentTaskBarStashController.isBubblesShowingOnOverview = true
        clearInvocations(bubbleBarViewController)

        // When switch out of the overview screen
        persistentTaskBarStashController.isBubblesShowingOnOverview = false

        // Then bubble bar controller is notified
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)
    }

    @Test
    fun setBubblesShowingOnOverviewUpdatedToTrue_controllersNotified() {
        // When switch to the overview screen
        persistentTaskBarStashController.isBubblesShowingOnOverview = true

        // Then bubble bar controller is notified
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)
    }

    @Test
    fun isSysuiLockedSwitchedToFalseForOverview_unlockAnimationIsShown() {
        // Given screen is locked and bubble bar has bubbles
        persistentTaskBarStashController.isSysuiLocked = true
        persistentTaskBarStashController.isBubblesShowingOnOverview = true
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        // When switch to the overview screen
        getInstrumentation().runOnMainSync {
            persistentTaskBarStashController.isSysuiLocked = false
        }

        // Then
        assertThat(translationY.isAnimating).isTrue()
        assertThat(scale.isAnimating).isTrue()
        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)

        // Then bubble bar is fully visible at the correct location
        assertThat(bubbleBarView.scaleX).isEqualTo(1f)
        assertThat(bubbleBarView.scaleY).isEqualTo(1f)
        assertThat(bubbleBarView.translationY).isEqualTo(TASK_BAR_TRANSLATION_Y)
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        // Insets controller is notified
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun showBubbleBarImmediateToY() {
        // Given bubble bar is fully transparent and scaled to 0 at 0 y position
        val targetY = 341f
        bubbleBarView.alpha = 0f
        bubbleBarView.scaleX = 0f
        bubbleBarView.scaleY = 0f
        bubbleBarView.translationY = 0f

        // When
        persistentTaskBarStashController.showBubbleBarImmediate(targetY)

        // Then all property values are updated
        assertThat(bubbleBarView.translationY).isEqualTo(targetY)
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        assertThat(bubbleBarView.scaleX).isEqualTo(1f)
        assertThat(bubbleBarView.scaleY).isEqualTo(1f)
    }

    @Test
    fun isTransientTaskbar_false() {
        assertThat(persistentTaskBarStashController.isTransientTaskBar).isFalse()
    }

    @Test
    fun hasHandleView_false() {
        assertThat(persistentTaskBarStashController.hasHandleView).isFalse()
    }

    @Test
    fun isStashed_false() {
        assertThat(persistentTaskBarStashController.isStashed).isFalse()
    }

    @Test
    fun bubbleBarTranslationYForTaskbar() {
        // Give bubble bar is on home
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.isBubblesShowingOnHome = true

        // Then bubbleBarTranslationY would be HOTSEAT_TRANSLATION_Y
        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isEqualTo(HOTSEAT_TRANSLATION_Y)

        // Give bubble bar is not on home
        persistentTaskBarStashController.isBubblesShowingOnHome = false

        // Then bubbleBarTranslationY would be TASK_BAR_TRANSLATION_Y
        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isEqualTo(TASK_BAR_TRANSLATION_Y)
    }

    private fun advanceTimeBy(advanceMs: Long) {
        // Advance animator for on-device tests
        getInstrumentation().runOnMainSync { animatorTestRule.advanceTimeBy(advanceMs) }
    }

    private fun setUpBubbleBarView() {
        getInstrumentation().runOnMainSync {
            bubbleBarView = BubbleBarView(context)
            bubbleBarView.layoutParams = FrameLayout.LayoutParams(0, 0)
        }
    }

    private fun setUpBubbleBarController() {
        translationY = AnimatedFloat(Runnable { bubbleBarView.translationY = translationY.value })
        scale =
            AnimatedFloat(
                Runnable {
                    val scale: Float = scale.value
                    bubbleBarView.scaleX = scale
                    bubbleBarView.scaleY = scale
                }
            )
        alpha = MultiValueAlpha(bubbleBarView, 1 /* num alpha channels */)

        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)
        whenever(bubbleBarViewController.bubbleBarTranslationY).thenReturn(translationY)
        whenever(bubbleBarViewController.bubbleBarScaleY).thenReturn(scale)
        whenever(bubbleBarViewController.bubbleBarAlpha).thenReturn(alpha)
        whenever(bubbleBarViewController.bubbleBarCollapsedHeight).thenReturn(BUBBLE_BAR_HEIGHT)
    }
}
