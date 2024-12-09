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
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.launcher3.R
import com.android.launcher3.popup.RoundedArrowDrawable

/** The flyout view used to notify the user of a new bubble notification. */
class BubbleBarFlyoutView(context: Context, private val onLeft: Boolean) :
    ConstraintLayout(context) {

    private val sender: TextView by
        lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.bubble_flyout_name) }

    private val avatar: ImageView by
        lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.bubble_flyout_avatar) }

    private val message: TextView by
        lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.bubble_flyout_text) }

    private val flyoutPadding by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_padding)
        }

    private val triangleHeight by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_triangle_height)
        }

    private val triangleOverlap by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(
                R.dimen.bubblebar_flyout_triangle_overlap_amount
            )
        }

    private val triangleWidth by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_triangle_width)
        }

    private val triangleRadius by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_triangle_radius)
        }

    private val minFlyoutWidth by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_min_width)
        }

    private val maxFlyoutWidth by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_max_width)
        }

    private val cornerRadius: Float
    private val triangle: Path = Path()
    private var backgroundColor = Color.BLACK

    /**
     * The paint used to draw the background, whose color changes as the flyout transitions to the
     * tinted notification dot.
     */
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        LayoutInflater.from(context).inflate(R.layout.bubblebar_flyout, this, true)

        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.dialogCornerRadius))
        cornerRadius = ta.getDimensionPixelSize(0, 0).toFloat()
        ta.recycle()

        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false

        val padding = context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_padding)
        // add extra padding to the bottom of the view to include the triangle
        setPadding(padding, padding, padding, padding + triangleHeight - triangleOverlap)
        translationZ =
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_elevation).toFloat()

        RoundedArrowDrawable.addDownPointingRoundedTriangleToPath(
            triangleWidth.toFloat(),
            triangleHeight.toFloat(),
            triangleRadius.toFloat(),
            triangle,
        )

        applyConfigurationColors(resources.configuration)
    }

    fun setData(flyoutMessage: BubbleBarFlyoutMessage) {
        // the avatar is only displayed in group chat messages
        if (flyoutMessage.senderAvatar != null && flyoutMessage.isGroupChat) {
            avatar.visibility = VISIBLE
            avatar.setImageDrawable(flyoutMessage.senderAvatar)
        } else {
            avatar.visibility = GONE
        }

        val minTextViewWidth: Int
        val maxTextViewWidth: Int
        if (avatar.visibility == VISIBLE) {
            minTextViewWidth = minFlyoutWidth - avatar.width - flyoutPadding * 2
            maxTextViewWidth = maxFlyoutWidth - avatar.width - flyoutPadding * 2
        } else {
            // when there's no avatar, the width of the text view is constant, so we're setting the
            // min and max to the same value
            minTextViewWidth = minFlyoutWidth - flyoutPadding * 2
            maxTextViewWidth = minTextViewWidth
        }

        if (flyoutMessage.senderName.isEmpty()) {
            sender.visibility = GONE
        } else {
            sender.minWidth = minTextViewWidth
            sender.maxWidth = maxTextViewWidth
            sender.text = flyoutMessage.senderName
            sender.visibility = VISIBLE
        }

        message.minWidth = minTextViewWidth
        message.maxWidth = maxTextViewWidth
        message.text = flyoutMessage.message
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat() - triangleHeight + triangleOverlap,
            cornerRadius,
            cornerRadius,
            backgroundPaint,
        )
        drawTriangle(canvas)
        super.onDraw(canvas)
    }

    private fun drawTriangle(canvas: Canvas) {
        canvas.save()
        val triangleX = if (onLeft) cornerRadius else width - cornerRadius - triangleWidth
        canvas.translate(triangleX, (height - triangleHeight).toFloat())
        canvas.drawPath(triangle, backgroundPaint)
        canvas.restore()
    }

    private fun applyConfigurationColors(configuration: Configuration) {
        val nightModeFlags = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        val defaultBackgroundColor = if (isNightModeOn) Color.BLACK else Color.WHITE
        val defaultTextColor = if (isNightModeOn) Color.WHITE else Color.BLACK
        val ta =
            context.obtainStyledAttributes(
                intArrayOf(
                    com.android.internal.R.attr.materialColorSurfaceContainer,
                    com.android.internal.R.attr.materialColorOnSurface,
                    com.android.internal.R.attr.materialColorOnSurfaceVariant,
                )
            )
        backgroundColor = ta.getColor(0, defaultBackgroundColor)
        sender.setTextColor(ta.getColor(1, defaultTextColor))
        message.setTextColor(ta.getColor(2, defaultTextColor))
        ta.recycle()
        backgroundPaint.color = backgroundColor
    }
}
