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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.taskbar.TaskbarInsetsController
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.Companion.BAR_STASH_DURATION
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.Companion.BAR_TRANSLATION_DURATION
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.ControllersAfterInitAction
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.TaskbarHotseatDimensionsProvider
import com.android.launcher3.util.MultiPropertyFactory
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.shared.bubbles.BubbleBarLocation

class PersistentBubbleStashController(
    private val taskbarHotseatDimensionsProvider: TaskbarHotseatDimensionsProvider,
) : BubbleStashController {

    private lateinit var taskbarInsetsController: TaskbarInsetsController
    private lateinit var bubbleBarViewController: BubbleBarViewController
    private lateinit var bubbleBarTranslationYAnimator: AnimatedFloat
    private lateinit var bubbleBarAlphaAnimator: MultiPropertyFactory<View>.MultiProperty
    private lateinit var bubbleBarScaleAnimator: AnimatedFloat
    private lateinit var controllersAfterInitAction: ControllersAfterInitAction

    override var isBubblesShowingOnHome: Boolean = false
        set(onHome) {
            if (field == onHome) return
            field = onHome
            if (!bubbleBarViewController.hasBubbles()) {
                // if there are no bubbles, there's nothing to show, so just return.
                return
            }
            if (onHome) {
                // When transition to home we should show collapse the bubble bar
                updateExpandedState(expand = false)
            }
            animateBubbleBarY()
            bubbleBarViewController.onBubbleBarConfigurationChanged(/* animate= */ true)
        }

    override var isBubblesShowingOnOverview: Boolean = false
        set(onOverview) {
            if (field == onOverview) return
            field = onOverview
            if (!onOverview) {
                // When transition from overview we should show collapse the bubble bar
                updateExpandedState(expand = false)
            }
            bubbleBarViewController.onBubbleBarConfigurationChanged(/* animate= */ true)
        }

    override var isSysuiLocked: Boolean = false
        set(isLocked) {
            if (field == isLocked) return
            field = isLocked
            if (!isLocked && bubbleBarViewController.hasBubbles()) {
                animateAfterUnlock()
            }
        }

    override var isTransientTaskBar: Boolean = false

    /** When the bubble bar is shown for the persistent task bar, there is no handle view. */
    override val hasHandleView: Boolean = false

    /** For persistent task bar we never stash the bubble bar */
    override val isStashed: Boolean = false

    override val bubbleBarTranslationYForTaskbar: Float
        get() {
            val taskbarBottomMargin = taskbarHotseatDimensionsProvider.getTaskbarBottomSpace()
            val bubbleBarHeight: Float = bubbleBarViewController.bubbleBarCollapsedHeight
            val taskbarHeight = taskbarHotseatDimensionsProvider.getTaskbarHeight()
            return -taskbarBottomMargin - (taskbarHeight - bubbleBarHeight) / 2f
        }

    override val bubbleBarTranslationYForHotseat: Float
        get() {
            val hotseatBottomSpace = taskbarHotseatDimensionsProvider.getHotseatBottomSpace()
            val hotseatCellHeight = taskbarHotseatDimensionsProvider.getHotseatHeight()
            val bubbleBarHeight: Float = bubbleBarViewController.bubbleBarCollapsedHeight
            return -hotseatBottomSpace - (hotseatCellHeight - bubbleBarHeight) / 2
        }

    override fun init(
        taskbarInsetsController: TaskbarInsetsController,
        bubbleBarViewController: BubbleBarViewController,
        bubbleStashedHandleViewController: BubbleStashedHandleViewController?,
        controllersAfterInitAction: ControllersAfterInitAction
    ) {
        this.taskbarInsetsController = taskbarInsetsController
        this.bubbleBarViewController = bubbleBarViewController
        this.controllersAfterInitAction = controllersAfterInitAction
        bubbleBarTranslationYAnimator = bubbleBarViewController.bubbleBarTranslationY
        // bubble bar has only alpha property, getting it at index 0
        bubbleBarAlphaAnimator = bubbleBarViewController.bubbleBarAlpha.get(/* index= */ 0)
        bubbleBarScaleAnimator = bubbleBarViewController.bubbleBarScaleY
    }

    private fun animateAfterUnlock() {
        val animatorSet = AnimatorSet()
        if (isBubblesShowingOnHome || isBubblesShowingOnOverview) {
            animatorSet.playTogether(
                bubbleBarScaleAnimator.animateToValue(1f),
                bubbleBarTranslationYAnimator.animateToValue(bubbleBarTranslationY),
                bubbleBarAlphaAnimator.animateToValue(1f)
            )
        }
        updateTouchRegionOnAnimationEnd(animatorSet)
        animatorSet.setDuration(BAR_STASH_DURATION).start()
    }

    override fun showBubbleBarImmediate() = showBubbleBarImmediate(bubbleBarTranslationY)

    override fun showBubbleBarImmediate(bubbleBarTranslationY: Float) {
        bubbleBarTranslationYAnimator.updateValue(bubbleBarTranslationY)
        bubbleBarAlphaAnimator.setValue(1f)
        bubbleBarScaleAnimator.updateValue(1f)
    }

    override fun setBubbleBarLocation(bubbleBarLocation: BubbleBarLocation) {
        // When the bubble bar is shown for the persistent task bar, there is no handle view, so no
        // operation is performed.
    }

    override fun stashBubbleBar() {
        updateExpandedState(expand = false)
    }

    override fun showBubbleBar(expandBubbles: Boolean) {
        updateExpandedState(expandBubbles)
    }

    override fun stashBubbleBarImmediate() {
        // When the bubble bar is shown for the persistent task bar, there is no handle view, so no
        // operation is performed.
    }

    /** If bubble bar is visible return bubble bar height, 0 otherwise */
    override fun getTouchableHeight() =
        if (isBubbleBarVisible()) {
            bubbleBarViewController.bubbleBarCollapsedHeight.toInt()
        } else {
            0
        }

    override fun isBubbleBarVisible(): Boolean = bubbleBarViewController.hasBubbles()

    override fun onNewBubbleAnimationInterrupted(isStashed: Boolean, bubbleBarTranslationY: Float) {
        showBubbleBarImmediate(bubbleBarTranslationY)
    }

    override fun isEventOverBubbleBarViews(ev: MotionEvent): Boolean =
        bubbleBarViewController.isEventOverAnyItem(ev)

    override fun getDiffBetweenHandleAndBarCenters(): Float {
        // distance from the bottom of the screen and the bubble bar center.
        return -bubbleBarViewController.bubbleBarCollapsedHeight / 2f
    }

    /** When the bubble bar is shown for the persistent task bar, there is no handle view. */
    override fun getStashedHandleTranslationForNewBubbleAnimation(): Float = 0f

    /** When the bubble bar is shown for the persistent task bar, there is no handle view. */
    override fun getStashedHandlePhysicsAnimator(): PhysicsAnimator<View>? = null

    override fun updateTaskbarTouchRegion() {
        taskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    /**
     * When the bubble bar is shown for the persistent task bar the bar does not stash, so no
     * operation is performed
     */
    override fun setHandleTranslationY(translationY: Float) {
        // no op since does not have a handle view
    }

    override fun getHandleTranslationY(): Float? = null

    override fun getHandleBounds(bounds: Rect) {
        // no op since does not have a handle view
    }

    private fun updateExpandedState(expand: Boolean) {
        if (bubbleBarViewController.isHiddenForNoBubbles) {
            // If there are no bubbles the bar is invisible, nothing to do here.
            return
        }
        if (bubbleBarViewController.isExpanded != expand) {
            bubbleBarViewController.isExpanded = expand
        }
    }

    /** Animates bubble bar Y accordingly to the showing mode */
    private fun animateBubbleBarY() {
        val animator =
            bubbleBarViewController.bubbleBarTranslationY.animateToValue(bubbleBarTranslationY)
        updateTouchRegionOnAnimationEnd(animator)
        animator.setDuration(BAR_TRANSLATION_DURATION)
        animator.start()
    }

    private fun updateTouchRegionOnAnimationEnd(animator: Animator) {
        animator.addListener(
            object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {
                    controllersAfterInitAction.runAfterInit {
                        taskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
                    }
                }
            }
        )
    }
}
