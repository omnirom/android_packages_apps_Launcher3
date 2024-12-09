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

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.R

/** Creates and manages the visibility of the [BubbleBarFlyoutView]. */
class BubbleBarFlyoutController(
    private val container: FrameLayout,
    private val positioner: BubbleBarFlyoutPositioner,
) {

    private var flyout: BubbleBarFlyoutView? = null
    private val horizontalMargin =
        container.context.resources.getDimensionPixelSize(R.dimen.transient_taskbar_bottom_margin)

    fun setUpFlyout(message: BubbleBarFlyoutMessage) {
        flyout?.let(container::removeView)
        val flyout = BubbleBarFlyoutView(container.context, onLeft = positioner.isOnLeft)

        flyout.translationY = positioner.targetTy

        val lp =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or if (positioner.isOnLeft) Gravity.LEFT else Gravity.RIGHT,
            )
        lp.marginStart = horizontalMargin
        lp.marginEnd = horizontalMargin
        container.addView(flyout, lp)

        flyout.setData(message)
        this.flyout = flyout
    }

    fun hideFlyout() {
        val flyout = this.flyout ?: return
        container.removeView(flyout)
        this.flyout = null
    }
}
