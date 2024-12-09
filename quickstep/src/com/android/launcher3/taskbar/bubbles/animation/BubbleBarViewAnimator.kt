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

package com.android.launcher3.taskbar.bubbles.animation

import android.view.View
import android.view.View.VISIBLE
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.ObjectAnimator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.R
import com.android.launcher3.taskbar.bubbles.BubbleBarBubble
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleView
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.wm.shell.shared.animation.PhysicsAnimator

/** Handles animations for bubble bar bubbles. */
class BubbleBarViewAnimator
@JvmOverloads
constructor(
    private val bubbleBarView: BubbleBarView,
    private val bubbleStashController: BubbleStashController,
    private val onExpanded: Runnable,
    private val scheduler: Scheduler = HandlerScheduler(bubbleBarView)
) {

    private var animatingBubble: AnimatingBubble? = null
    private val bubbleBarBounceDistanceInPx =
        bubbleBarView.resources.getDimensionPixelSize(R.dimen.bubblebar_bounce_distance)

    fun hasAnimation() = animatingBubble != null

    val isAnimating: Boolean
        get() {
            val animatingBubble = animatingBubble ?: return false
            return animatingBubble.state != AnimatingBubble.State.CREATED
        }

    private companion object {
        /** The time to show the flyout. */
        const val FLYOUT_DELAY_MS: Long = 2500
        /** The initial scale Y value that the new bubble is set to before the animation starts. */
        const val BUBBLE_ANIMATION_INITIAL_SCALE_Y = 0.3f
        /** The minimum alpha value to make the bubble bar touchable. */
        const val MIN_ALPHA_FOR_TOUCHABLE = 0.5f
        /** The duration of the bounce animation. */
        const val BUBBLE_BAR_BOUNCE_ANIMATION_DURATION_MS = 250L
    }

    /** Wrapper around the animating bubble with its show and hide animations. */
    private data class AnimatingBubble(
        val bubbleView: BubbleView,
        val showAnimation: Runnable,
        val hideAnimation: Runnable,
        val expand: Boolean,
        val state: State = State.CREATED
    ) {

        /**
         * The state of the animation.
         *
         * The animation is initially created but will be scheduled later using the [Scheduler].
         *
         * The normal uninterrupted cycle is for the bubble notification to animate in, then be in a
         * transient state and eventually to animate out.
         *
         * However different events, such as touch and external signals, may cause the animation to
         * end earlier.
         */
        enum class State {
            /** The animation is created but not started yet. */
            CREATED,
            /** The bubble notification is animating in. */
            ANIMATING_IN,
            /** The bubble notification is now fully showing and waiting to be hidden. */
            IN,
            /** The bubble notification is animating out. */
            ANIMATING_OUT
        }
    }

    /** An interface for scheduling jobs. */
    interface Scheduler {

        /** Schedule the given [block] to run. */
        fun post(block: Runnable)

        /** Schedule the given [block] to start with a delay of [delayMillis]. */
        fun postDelayed(delayMillis: Long, block: Runnable)

        /** Cancel the given [block] if it hasn't started yet. */
        fun cancel(block: Runnable)
    }

    /** A [Scheduler] that uses a Handler to run jobs. */
    private class HandlerScheduler(private val view: View) : Scheduler {

        override fun post(block: Runnable) {
            view.post(block)
        }

        override fun postDelayed(delayMillis: Long, block: Runnable) {
            view.postDelayed(block, delayMillis)
        }

        override fun cancel(block: Runnable) {
            view.removeCallbacks(block)
        }
    }

    private val springConfig =
        PhysicsAnimator.SpringConfig(
            stiffness = SpringForce.STIFFNESS_LOW,
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        )

    /** Animates a bubble for the state where the bubble bar is stashed. */
    fun animateBubbleInForStashed(b: BubbleBarBubble, isExpanding: Boolean) {
        // TODO b/346400677: handle animations for the same bubble interrupting each other
        if (animatingBubble?.bubbleView?.bubble?.key == b.key) return
        val bubbleView = b.view
        val animator = PhysicsAnimator.getInstance(bubbleView)
        if (animator.isRunning()) animator.cancel()
        // the animation of a new bubble is divided into 2 parts. The first part shows the bubble
        // and the second part hides it after a delay.
        val showAnimation = buildHandleToBubbleBarAnimation()
        val hideAnimation = if (isExpanding) Runnable {} else buildBubbleBarToHandleAnimation()
        animatingBubble =
            AnimatingBubble(bubbleView, showAnimation, hideAnimation, expand = isExpanding)
        scheduler.post(showAnimation)
        scheduler.postDelayed(FLYOUT_DELAY_MS, hideAnimation)
    }

    /**
     * Returns a [Runnable] that starts the animation that morphs the handle to the bubble bar.
     *
     * Visually, the animation is divided into 2 parts. The stash handle starts animating up and
     * fading out and then the bubble bar starts animating up and fading in.
     *
     * To make the transition from the handle to the bar smooth, the positions and movement of the 2
     * views must be synchronized. To do that we use a single spring path along the Y axis, starting
     * from the handle's position to the eventual bar's position. The path is split into 3 parts.
     * 1. In the first part, we only animate the handle.
     * 2. In the second part the handle is fully hidden, and the bubble bar is animating in.
     * 3. The third part is the overshoot of the spring animation, where we make the bubble fully
     *    visible which helps avoiding further updates when we re-enter the second part.
     */
    private fun buildHandleToBubbleBarAnimation() = Runnable {
        moveToState(AnimatingBubble.State.ANIMATING_IN)
        // prepare the bubble bar for the animation
        bubbleBarView.visibility = VISIBLE
        bubbleBarView.alpha = 0f
        bubbleBarView.translationY = 0f
        bubbleBarView.scaleX = 1f
        bubbleBarView.scaleY = BUBBLE_ANIMATION_INITIAL_SCALE_Y
        bubbleBarView.setBackgroundScaleX(1f)
        bubbleBarView.setBackgroundScaleY(1f)
        bubbleBarView.relativePivotY = 0.5f

        // this is the offset between the center of the bubble bar and the center of the stash
        // handle. when the handle becomes invisible and we start animating in the bubble bar,
        // the translation y is offset by this value to make the transition from the handle to the
        // bar smooth.
        val offset = bubbleStashController.getDiffBetweenHandleAndBarCenters()
        val stashedHandleTranslationYForAnimation =
            bubbleStashController.getStashedHandleTranslationForNewBubbleAnimation()
        val stashedHandleTranslationY =
            bubbleStashController.getHandleTranslationY() ?: return@Runnable
        val translationTracker = TranslationTracker(stashedHandleTranslationY)

        // this is the total distance that both the stashed handle and the bubble will be traveling
        // at the end of the animation the bubble bar will be positioned in the same place when it
        // shows while we're in an app.
        val totalTranslationY = bubbleStashController.bubbleBarTranslationYForTaskbar + offset
        val animator = bubbleStashController.getStashedHandlePhysicsAnimator() ?: return@Runnable
        animator.setDefaultSpringConfig(springConfig)
        animator.spring(DynamicAnimation.TRANSLATION_Y, totalTranslationY)
        animator.addUpdateListener { handle, values ->
            val ty = values[DynamicAnimation.TRANSLATION_Y]?.value ?: return@addUpdateListener
            when {
                ty >= stashedHandleTranslationYForAnimation -> {
                    // we're in the first leg of the animation. only animate the handle. the bubble
                    // bar remains hidden during this part of the animation

                    // map the path [0, stashedHandleTranslationY] to [0,1]
                    val fraction = ty / stashedHandleTranslationYForAnimation
                    handle.alpha = 1 - fraction
                }
                ty >= totalTranslationY -> {
                    // this is the second leg of the animation. the handle should be completely
                    // hidden and the bubble bar should start animating in.
                    // it's possible that we're re-entering this leg because this is a spring
                    // animation, so only set the alpha and scale for the bubble bar if we didn't
                    // already fully animate in.
                    handle.alpha = 0f
                    bubbleBarView.translationY = ty - offset
                    if (bubbleBarView.alpha != 1f) {
                        // map the path [stashedHandleTranslationY, totalTranslationY] to [0, 1]
                        val fraction =
                            (ty - stashedHandleTranslationYForAnimation) /
                                (totalTranslationY - stashedHandleTranslationYForAnimation)
                        bubbleBarView.alpha = fraction
                        bubbleBarView.scaleY =
                            BUBBLE_ANIMATION_INITIAL_SCALE_Y +
                                (1 - BUBBLE_ANIMATION_INITIAL_SCALE_Y) * fraction
                        if (bubbleBarView.alpha > MIN_ALPHA_FOR_TOUCHABLE) {
                            bubbleStashController.updateTaskbarTouchRegion()
                        }
                    }
                }
                else -> {
                    // we're past the target animated value, set the alpha and scale for the bubble
                    // bar so that it's fully visible and no longer changing, but keep moving it
                    // along the animation path
                    bubbleBarView.alpha = 1f
                    bubbleBarView.scaleY = 1f
                    bubbleBarView.translationY = ty - offset
                    bubbleStashController.updateTaskbarTouchRegion()
                }
            }
            translationTracker.updateTyAndExpandIfNeeded(ty)
        }
        animator.addEndListener { _, _, _, canceled, _, _, _ ->
            // if the show animation was canceled, also cancel the hide animation. this is typically
            // canceled in this class, but could potentially be canceled elsewhere.
            if (canceled || animatingBubble?.expand == true) {
                cancelHideAnimation()
                return@addEndListener
            }
            moveToState(AnimatingBubble.State.IN)
            // the bubble bar is now fully settled in. update taskbar touch region so it's touchable
            bubbleStashController.updateTaskbarTouchRegion()
        }
        animator.start()
    }

    /**
     * Returns a [Runnable] that starts the animation that hides the bubble bar and morphs it into
     * the stashed handle.
     *
     * Similarly to the show animation, this is visually divided into 2 parts. We first animate the
     * bubble bar out, and then animate the stash handle in. At the end of the animation we reset
     * values of the bubble bar.
     *
     * This is a spring animation that goes along the same path of the show animation in the
     * opposite order, and is split into 3 parts:
     * 1. In the first part the bubble animates out.
     * 2. In the second part the bubble bar is fully hidden and the handle animates in.
     * 3. The third part is the overshoot. The handle is made fully visible.
     */
    private fun buildBubbleBarToHandleAnimation() = Runnable {
        if (animatingBubble == null) return@Runnable
        moveToState(AnimatingBubble.State.ANIMATING_OUT)
        val offset = bubbleStashController.getDiffBetweenHandleAndBarCenters()
        val stashedHandleTranslationY =
            bubbleStashController.getStashedHandleTranslationForNewBubbleAnimation()
        // this is the total distance that both the stashed handle and the bar will be traveling
        val totalTranslationY = bubbleStashController.bubbleBarTranslationYForTaskbar + offset
        bubbleStashController.setHandleTranslationY(totalTranslationY)
        val animator = bubbleStashController.getStashedHandlePhysicsAnimator() ?: return@Runnable
        animator.setDefaultSpringConfig(springConfig)
        animator.spring(DynamicAnimation.TRANSLATION_Y, 0f)
        animator.addUpdateListener { handle, values ->
            val ty = values[DynamicAnimation.TRANSLATION_Y]?.value ?: return@addUpdateListener
            when {
                ty <= stashedHandleTranslationY -> {
                    // this is the first leg of the animation. only animate the bubble bar. the
                    // handle is hidden during this part
                    bubbleBarView.translationY = ty - offset
                    // map the path [totalTranslationY, stashedHandleTranslationY] to [0, 1]
                    val fraction =
                        (totalTranslationY - ty) / (totalTranslationY - stashedHandleTranslationY)
                    bubbleBarView.alpha = 1 - fraction
                    bubbleBarView.scaleY = 1 - (1 - BUBBLE_ANIMATION_INITIAL_SCALE_Y) * fraction
                    if (bubbleBarView.alpha > MIN_ALPHA_FOR_TOUCHABLE) {
                        bubbleStashController.updateTaskbarTouchRegion()
                    }
                }
                ty <= 0 -> {
                    // this is the second part of the animation. make the bubble bar invisible and
                    // start fading in the handle, but don't update the alpha if it's already fully
                    // visible
                    bubbleBarView.alpha = 0f
                    if (handle.alpha != 1f) {
                        // map the path [stashedHandleTranslationY, 0] to [0, 1]
                        val fraction = (stashedHandleTranslationY - ty) / stashedHandleTranslationY
                        handle.alpha = fraction
                    }
                }
                else -> {
                    // we reached the target value. set the alpha of the handle to 1
                    handle.alpha = 1f
                }
            }
        }
        animator.addEndListener { _, _, _, canceled, _, _, _ ->
            animatingBubble = null
            if (!canceled) bubbleStashController.stashBubbleBarImmediate()
            bubbleBarView.relativePivotY = 1f
            bubbleBarView.scaleY = 1f
            bubbleStashController.updateTaskbarTouchRegion()
        }
        animator.start()
    }

    /** Animates to the initial state of the bubble bar, when there are no previous bubbles. */
    fun animateToInitialState(b: BubbleBarBubble, isInApp: Boolean, isExpanding: Boolean) {
        // TODO b/346400677: handle animations for the same bubble interrupting each other
        if (animatingBubble?.bubbleView?.bubble?.key == b.key) return
        val bubbleView = b.view
        val animator = PhysicsAnimator.getInstance(bubbleView)
        if (animator.isRunning()) animator.cancel()
        // the animation of a new bubble is divided into 2 parts. The first part shows the bubble
        // and the second part hides it after a delay if we are in an app.
        val showAnimation = buildBubbleBarSpringInAnimation()
        val hideAnimation =
            if (isInApp && !isExpanding) {
                buildBubbleBarToHandleAnimation()
            } else {
                // in this case the bubble bar remains visible so not much to do. once we implement
                // the flyout we'll update this runnable to hide it.
                Runnable {
                    animatingBubble = null
                    bubbleStashController.showBubbleBarImmediate()
                    bubbleStashController.updateTaskbarTouchRegion()
                }
            }
        animatingBubble =
            AnimatingBubble(bubbleView, showAnimation, hideAnimation, expand = isExpanding)
        scheduler.post(showAnimation)
        scheduler.postDelayed(FLYOUT_DELAY_MS, hideAnimation)
    }

    private fun buildBubbleBarSpringInAnimation() = Runnable {
        moveToState(AnimatingBubble.State.ANIMATING_IN)
        // prepare the bubble bar for the animation
        bubbleBarView.translationY = bubbleBarView.height.toFloat()
        bubbleBarView.visibility = VISIBLE
        bubbleBarView.alpha = 1f
        bubbleBarView.scaleX = 1f
        bubbleBarView.scaleY = 1f

        val translationTracker = TranslationTracker(bubbleBarView.translationY)

        val animator = PhysicsAnimator.getInstance(bubbleBarView)
        animator.setDefaultSpringConfig(springConfig)
        animator.spring(DynamicAnimation.TRANSLATION_Y, bubbleStashController.bubbleBarTranslationY)
        animator.addUpdateListener { _, values ->
            val ty = values[DynamicAnimation.TRANSLATION_Y]?.value ?: return@addUpdateListener
            translationTracker.updateTyAndExpandIfNeeded(ty)
            bubbleStashController.updateTaskbarTouchRegion()
        }
        animator.addEndListener { _, _, _, _, _, _, _ ->
            if (animatingBubble?.expand == true) {
                cancelHideAnimation()
            } else {
                moveToState(AnimatingBubble.State.IN)
            }
            // the bubble bar is now fully settled in. update taskbar touch region so it's touchable
            bubbleStashController.updateTaskbarTouchRegion()
        }
        animator.start()
    }

    fun animateBubbleBarForCollapsed(b: BubbleBarBubble, isExpanding: Boolean) {
        // TODO b/346400677: handle animations for the same bubble interrupting each other
        if (animatingBubble?.bubbleView?.bubble?.key == b.key) return
        val bubbleView = b.view
        val animator = PhysicsAnimator.getInstance(bubbleView)
        if (animator.isRunning()) animator.cancel()
        val showAnimation = buildBubbleBarBounceAnimation()
        val hideAnimation = Runnable {
            animatingBubble = null
            bubbleStashController.showBubbleBarImmediate()
            bubbleStashController.updateTaskbarTouchRegion()
        }
        animatingBubble =
            AnimatingBubble(bubbleView, showAnimation, hideAnimation, expand = isExpanding)
        scheduler.post(showAnimation)
        scheduler.postDelayed(FLYOUT_DELAY_MS, hideAnimation)
    }

    /**
     * The bubble bar animation when it is collapsed is divided into 2 chained animations. The first
     * animation is a regular accelerate animation that moves the bubble bar upwards. When it ends
     * the bubble bar moves back to its initial position with a spring animation.
     */
    private fun buildBubbleBarBounceAnimation() = Runnable {
        moveToState(AnimatingBubble.State.ANIMATING_IN)
        val ty = bubbleStashController.bubbleBarTranslationY

        val springBackAnimation = PhysicsAnimator.getInstance(bubbleBarView)
        springBackAnimation.setDefaultSpringConfig(springConfig)
        springBackAnimation.spring(DynamicAnimation.TRANSLATION_Y, ty)
        springBackAnimation.addEndListener { _, _, _, _, _, _, _ ->
            if (animatingBubble?.expand == true) {
                expandBubbleBar()
                cancelHideAnimation()
            } else {
                moveToState(AnimatingBubble.State.IN)
            }
        }

        // animate the bubble bar up and start the spring back down animation when it ends.
        ObjectAnimator.ofFloat(bubbleBarView, View.TRANSLATION_Y, ty - bubbleBarBounceDistanceInPx)
            .withDuration(BUBBLE_BAR_BOUNCE_ANIMATION_DURATION_MS)
            .withEndAction {
                if (animatingBubble?.expand == true) expandBubbleBar()
                springBackAnimation.start()
            }
            .start()
    }

    /** Handles touching the animating bubble bar. */
    fun onBubbleBarTouchedWhileAnimating() {
        PhysicsAnimator.getInstance(bubbleBarView).cancelIfRunning()
        bubbleStashController.getStashedHandlePhysicsAnimator().cancelIfRunning()
        val hideAnimation = animatingBubble?.hideAnimation ?: return
        scheduler.cancel(hideAnimation)
        bubbleBarView.relativePivotY = 1f
        animatingBubble = null
    }

    /** Notifies the animator that the taskbar area was touched during an animation. */
    fun onStashStateChangingWhileAnimating() {
        val hideAnimation = animatingBubble?.hideAnimation ?: return
        scheduler.cancel(hideAnimation)
        animatingBubble = null
        bubbleStashController.getStashedHandlePhysicsAnimator().cancelIfRunning()
        bubbleBarView.relativePivotY = 1f
        bubbleStashController.onNewBubbleAnimationInterrupted(
            /* isStashed= */ bubbleBarView.alpha == 0f,
            bubbleBarView.translationY
        )
    }

    fun expandedWhileAnimating() {
        val animatingBubble = animatingBubble ?: return
        this.animatingBubble = animatingBubble.copy(expand = true)
        // if we're fully in and waiting to hide, cancel the hide animation and clean up
        if (animatingBubble.state == AnimatingBubble.State.IN) {
            expandBubbleBar()
            cancelHideAnimation()
        }
    }

    private fun cancelHideAnimation() {
        val hideAnimation = animatingBubble?.hideAnimation ?: return
        scheduler.cancel(hideAnimation)
        animatingBubble = null
        bubbleBarView.relativePivotY = 1f
        bubbleStashController.showBubbleBarImmediate()
    }

    private fun <T> PhysicsAnimator<T>?.cancelIfRunning() {
        if (this?.isRunning() == true) cancel()
    }

    private fun ObjectAnimator.withDuration(duration: Long): ObjectAnimator {
        setDuration(duration)
        return this
    }

    private fun ObjectAnimator.withEndAction(endAction: () -> Unit): ObjectAnimator {
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    endAction()
                }
            }
        )
        return this
    }

    private fun moveToState(state: AnimatingBubble.State) {
        val animatingBubble = this.animatingBubble ?: return
        this.animatingBubble = animatingBubble.copy(state = state)
    }

    private fun expandBubbleBar() {
        bubbleBarView.isExpanded = true
        onExpanded.run()
    }

    /**
     * Tracks the translation Y of the bubble bar during the animation. When the bubble bar expands
     * as part of the animation, the expansion should start after the bubble bar reaches the peak
     * position.
     */
    private inner class TranslationTracker(initialTy: Float) {
        private var previousTy = initialTy
        private var startedExpanding = false
        private var reachedPeak = false

        fun updateTyAndExpandIfNeeded(ty: Float) {
            if (!reachedPeak) {
                // the bubble bar is positioned at the bottom of the screen and moves up using
                // negative ty values. the peak is reached the first time we see a value that is
                // greater than the previous.
                if (ty > previousTy) {
                    reachedPeak = true
                }
            }
            val expand = animatingBubble?.expand ?: false
            if (reachedPeak && expand && !startedExpanding) {
                expandBubbleBar()
                startedExpanding = true
            }
            previousTy = ty
        }
    }
}
