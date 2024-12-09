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

import androidx.core.animation.Animator
import androidx.core.animation.ValueAnimator

/**
 * Animates individual bubbles within the bubble bar while the bubble bar is expanded.
 *
 * This class should only be kept for the duration of the animation and a new instance should be
 * created for each animation.
 */
class BubbleAnimator(
    private val iconSize: Float,
    private val expandedBarIconSpacing: Float,
    private val bubbleCount: Int,
    private val onLeft: Boolean,
) {

    companion object {
        const val ANIMATION_DURATION_MS = 250L
    }

    private var state: State = State.Idle
    private lateinit var animator: ValueAnimator

    fun animateNewBubble(selectedBubbleIndex: Int, listener: Listener) {
        animator = createAnimator(listener)
        state = State.AddingBubble(selectedBubbleIndex)
        animator.start()
    }

    fun animateRemovedBubble(
        bubbleIndex: Int,
        selectedBubbleIndex: Int,
        removingLastBubble: Boolean,
        listener: Listener
    ) {
        animator = createAnimator(listener)
        state = State.RemovingBubble(bubbleIndex, selectedBubbleIndex, removingLastBubble)
        animator.start()
    }

    fun animateNewAndRemoveOld(
        selectedBubbleIndex: Int,
        removedBubbleIndex: Int,
        listener: Listener
    ) {
        animator = createAnimator(listener)
        state =
            State.AddingAndRemoving(
                selectedBubbleIndex = selectedBubbleIndex,
                removedBubbleIndex = removedBubbleIndex
            )
        animator.start()
    }

    private fun createAnimator(listener: Listener): ValueAnimator {
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(ANIMATION_DURATION_MS)
        animator.addUpdateListener { animation ->
            val animatedFraction = (animation as ValueAnimator).animatedFraction
            listener.onAnimationUpdate(animatedFraction)
        }
        animator.addListener(
            object : Animator.AnimatorListener {

                override fun onAnimationCancel(animation: Animator) {
                    listener.onAnimationCancel()
                }

                override fun onAnimationEnd(animation: Animator) {
                    state = State.Idle
                    listener.onAnimationEnd()
                }

                override fun onAnimationRepeat(animation: Animator) {}

                override fun onAnimationStart(animation: Animator) {}
            }
        )
        return animator
    }

    /**
     * The translation X of the bubble at index [bubbleIndex] when the bubble bar is expanded
     * according to the progress of this animation.
     *
     * Callers should verify that the animation is running before calling this.
     *
     * @see isRunning
     */
    fun getBubbleTranslationX(bubbleIndex: Int): Float {
        return when (val state = state) {
            State.Idle -> 0f
            is State.AddingBubble ->
                getBubbleTranslationXWhileScalingBubble(
                    bubbleIndex = bubbleIndex,
                    scalingBubbleIndex = 0,
                    bubbleScale = animator.animatedFraction
                )
            is State.RemovingBubble ->
                getBubbleTranslationXWhileScalingBubble(
                    bubbleIndex = bubbleIndex,
                    scalingBubbleIndex = state.bubbleIndex,
                    bubbleScale = 1 - animator.animatedFraction
                )
            is State.AddingAndRemoving ->
                getBubbleTranslationXWhileAddingBubbleAtLimit(
                    bubbleIndex = bubbleIndex,
                    removedBubbleIndex = state.removedBubbleIndex,
                    addedBubbleScale = animator.animatedFraction,
                    removedBubbleScale = 1 - animator.animatedFraction
                )
        }
    }

    /**
     * The expanded width of the bubble bar according to the progress of the animation.
     *
     * Callers should verify that the animation is running before calling this.
     *
     * @see isRunning
     */
    fun getExpandedWidth(): Float {
        val bubbleScale =
            when (state) {
                State.Idle -> 0f
                is State.AddingBubble -> animator.animatedFraction
                is State.RemovingBubble -> 1 - animator.animatedFraction
                is State.AddingAndRemoving -> {
                    // since we're adding a bubble and removing another bubble, their sizes together
                    // equal to a single bubble. the width is the same as having bubbleCount - 1
                    // bubbles at full scale.
                    val totalSpace = (bubbleCount - 2) * expandedBarIconSpacing
                    val totalIconSize = (bubbleCount - 1) * iconSize
                    return totalIconSize + totalSpace
                }
            }
        // When this animator is running the bubble bar is expanded so it's safe to assume that we
        // have at least 2 bubbles, but should update the logic to support optional overflow.
        // If we're removing the last bubble, the entire bar should animate and we shouldn't get
        // here.
        val totalSpace = (bubbleCount - 2 + bubbleScale) * expandedBarIconSpacing
        val totalIconSize = (bubbleCount - 1 + bubbleScale) * iconSize
        return totalIconSize + totalSpace
    }

    /**
     * Returns the arrow position according to the progress of the animation and, if the selected
     * bubble is being removed, accounting to the newly selected bubble.
     *
     * Callers should verify that the animation is running before calling this.
     *
     * @see isRunning
     */
    fun getArrowPosition(): Float {
        return when (val state = state) {
            State.Idle -> 0f
            is State.AddingBubble -> {
                val tx =
                    getBubbleTranslationXWhileScalingBubble(
                        bubbleIndex = state.selectedBubbleIndex,
                        scalingBubbleIndex = 0,
                        bubbleScale = animator.animatedFraction
                    )
                tx + iconSize / 2f
            }
            is State.RemovingBubble -> getArrowPositionWhenRemovingBubble(state)
            is State.AddingAndRemoving -> {
                // we never remove the selected bubble, so the arrow stays pointing to its center
                val tx =
                    getBubbleTranslationXWhileAddingBubbleAtLimit(
                        bubbleIndex = state.selectedBubbleIndex,
                        removedBubbleIndex = state.removedBubbleIndex,
                        addedBubbleScale = animator.animatedFraction,
                        removedBubbleScale = 1 - animator.animatedFraction
                    )
                tx + iconSize / 2f
            }
        }
    }

    private fun getArrowPositionWhenRemovingBubble(state: State.RemovingBubble): Float {
        return if (state.selectedBubbleIndex != state.bubbleIndex) {
            // if we're not removing the selected bubble, the selected bubble doesn't change so just
            // return the translation X of the selected bubble and add half icon
            val tx =
                getBubbleTranslationXWhileScalingBubble(
                    bubbleIndex = state.selectedBubbleIndex,
                    scalingBubbleIndex = state.bubbleIndex,
                    bubbleScale = 1 - animator.animatedFraction
                )
            tx + iconSize / 2f
        } else {
            // we're removing the selected bubble so the arrow needs to point to a different bubble.
            // if we're removing the last bubble the newly selected bubble will be the second to
            // last. otherwise, it'll be the next bubble (closer to the overflow)
            val iconAndSpacing = iconSize + expandedBarIconSpacing
            if (state.removingLastBubble) {
                if (onLeft) {
                    // the newly selected bubble is the bubble to the right. at the end of the
                    // animation all the bubbles will have shifted left, so the arrow stays at the
                    // same distance from the left edge of bar
                    (bubbleCount - state.bubbleIndex - 1) * iconAndSpacing + iconSize / 2f
                } else {
                    // the newly selected bubble is the bubble to the left. at the end of the
                    // animation all the bubbles will have shifted right, and the arrow would
                    // eventually be closer to the left edge of the bar by iconAndSpacing
                    val initialTx = state.bubbleIndex * iconAndSpacing + iconSize / 2f
                    initialTx - animator.animatedFraction * iconAndSpacing
                }
            } else {
                if (onLeft) {
                    // the newly selected bubble is to the left, and bubbles are shifting left, so
                    // move the arrow closer to the left edge of the bar by iconAndSpacing
                    val initialTx =
                        (bubbleCount - state.bubbleIndex - 1) * iconAndSpacing + iconSize / 2f
                    initialTx - animator.animatedFraction * iconAndSpacing
                } else {
                    // the newly selected bubble is to the right, and bubbles are shifting right, so
                    // the arrow stays at the same distance from the left edge of the bar
                    state.bubbleIndex * iconAndSpacing + iconSize / 2f
                }
            }
        }
    }

    /**
     * Returns the translation X for the bubble at index {@code bubbleIndex} when the bubble bar is
     * expanded and a bubble is animating in or out.
     *
     * @param bubbleIndex the index of the bubble for which the translation is requested
     * @param scalingBubbleIndex the index of the bubble that is animating
     * @param bubbleScale the current scale of the animating bubble
     */
    private fun getBubbleTranslationXWhileScalingBubble(
        bubbleIndex: Int,
        scalingBubbleIndex: Int,
        bubbleScale: Float
    ): Float {
        val iconAndSpacing = iconSize + expandedBarIconSpacing
        // the bubble is scaling from the center, so we need to adjust its translation so
        // that the distance to the adjacent bubble scales at the same rate.
        val pivotAdjustment = -(1 - bubbleScale) * iconSize / 2f

        return if (onLeft) {
            when {
                bubbleIndex < scalingBubbleIndex ->
                    // the bar is on the left and the current bubble is to the right of the scaling
                    // bubble so account for its scale
                    (bubbleCount - bubbleIndex - 2 + bubbleScale) * iconAndSpacing
                bubbleIndex == scalingBubbleIndex -> {
                    // the bar is on the left and this is the scaling bubble
                    val totalIconSize = (bubbleCount - bubbleIndex - 1) * iconSize
                    // don't count the spacing between the scaling bubble and the bubble on the left
                    // because we need to scale that space
                    val totalSpacing = (bubbleCount - bubbleIndex - 2) * expandedBarIconSpacing
                    val scaledSpace = bubbleScale * expandedBarIconSpacing
                    totalIconSize + totalSpacing + scaledSpace + pivotAdjustment
                }
                else ->
                    // the bar is on the left and the scaling bubble is on the right. the current
                    // bubble is unaffected by the scaling bubble
                    (bubbleCount - bubbleIndex - 1) * iconAndSpacing
            }
        } else {
            when {
                bubbleIndex < scalingBubbleIndex ->
                    // the bar is on the right and the scaling bubble is on the right. the current
                    // bubble is unaffected by the scaling bubble
                    iconAndSpacing * bubbleIndex
                bubbleIndex == scalingBubbleIndex ->
                    // the bar is on the right, and this is the animating bubble. it only needs to
                    // be adjusted for the scaling pivot.
                    iconAndSpacing * bubbleIndex + pivotAdjustment
                else ->
                    // the bar is on the right and the scaling bubble is on the left so account for
                    // its scale
                    iconAndSpacing * (bubbleIndex - 1 + bubbleScale)
            }
        }
    }

    private fun getBubbleTranslationXWhileAddingBubbleAtLimit(
        bubbleIndex: Int,
        removedBubbleIndex: Int,
        addedBubbleScale: Float,
        removedBubbleScale: Float
    ): Float {
        val iconAndSpacing = iconSize + expandedBarIconSpacing
        // the bubbles are scaling from the center, so we need to adjust their translation so
        // that the distance to the adjacent bubble scales at the same rate.
        val addedBubblePivotAdjustment = -(1 - addedBubbleScale) * iconSize / 2f
        val removedBubblePivotAdjustment = -(1 - removedBubbleScale) * iconSize / 2f

        return if (onLeft) {
            // this is how many bubbles there are to the left of the current bubble.
            // when the bubble bar is on the right the added bubble is the right-most bubble so it
            // doesn't affect the translation of any other bubble.
            // when the removed bubble is to the left of the current bubble, we need to subtract it
            // from bubblesToLeft and use removedBubbleScale instead when calculating the
            // translation.
            val bubblesToLeft = bubbleCount - bubbleIndex - 1
            when {
                bubbleIndex == 0 ->
                    // this is the added bubble and it's the right-most bubble. account for all the
                    // other bubbles -- including the removed bubble -- and adjust for the added
                    // bubble pivot.
                    (bubblesToLeft - 1 + removedBubbleScale) * iconAndSpacing +
                        addedBubblePivotAdjustment
                bubbleIndex < removedBubbleIndex ->
                    // the removed bubble is to the left so account for it
                    (bubblesToLeft - 1 + removedBubbleScale) * iconAndSpacing
                bubbleIndex == removedBubbleIndex -> {
                    // this is the removed bubble. all the bubbles to the left are at full scale
                    // but we need to scale the spacing between the removed bubble and the bubble to
                    // its left because the removed bubble disappears towards the left side
                    val totalIconSize = bubblesToLeft * iconSize
                    val totalSpacing =
                        (bubblesToLeft - 1 + removedBubbleScale) * expandedBarIconSpacing
                    totalIconSize + totalSpacing + removedBubblePivotAdjustment
                }
                else ->
                    // both added and removed bubbles are to the right so they don't affect the tx
                    bubblesToLeft * iconAndSpacing
            }
        } else {
            when {
                bubbleIndex == 0 -> addedBubblePivotAdjustment // we always add bubbles at index 0
                bubbleIndex < removedBubbleIndex ->
                    // the bar is on the right and the removed bubble is on the right. the current
                    // bubble is unaffected by the removed bubble. only need to factor in the added
                    // bubble's scale.
                    iconAndSpacing * (bubbleIndex - 1 + addedBubbleScale)
                bubbleIndex == removedBubbleIndex ->
                    // the bar is on the right, and this is the animating bubble.
                    iconAndSpacing * (bubbleIndex - 1 + addedBubbleScale) +
                        removedBubblePivotAdjustment
                else ->
                    // both the added and the removed bubbles are to the left of the current bubble
                    iconAndSpacing * (bubbleIndex - 2 + addedBubbleScale + removedBubbleScale)
            }
        }
    }

    val isRunning: Boolean
        get() = state != State.Idle

    /** The state of the animation. */
    sealed interface State {

        /** The animation is not running. */
        data object Idle : State

        /** A new bubble is being added to the bubble bar. */
        data class AddingBubble(val selectedBubbleIndex: Int) : State

        /** A bubble is being removed from the bubble bar. */
        data class RemovingBubble(
            /** The index of the bubble being removed. */
            val bubbleIndex: Int,
            /** The index of the selected bubble. */
            val selectedBubbleIndex: Int,
            /** Whether the bubble being removed is also the last bubble. */
            val removingLastBubble: Boolean
        ) : State

        /** A new bubble is being added and an old bubble is being removed from the bubble bar. */
        data class AddingAndRemoving(val selectedBubbleIndex: Int, val removedBubbleIndex: Int) :
            State
    }

    /** Callbacks for the animation. */
    interface Listener {

        /**
         * Notifies the listener of an animation update event, where `animatedFraction` represents
         * the progress of the animation starting from 0 and ending at 1.
         */
        fun onAnimationUpdate(animatedFraction: Float)

        /** Notifies the listener that the animation was canceled. */
        fun onAnimationCancel()

        /** Notifies that listener that the animation ended. */
        fun onAnimationEnd()
    }
}
