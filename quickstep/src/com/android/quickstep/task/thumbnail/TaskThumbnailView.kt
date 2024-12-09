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

package com.android.quickstep.task.thumbnail

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isInvisible
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.ViewPool
import com.android.quickstep.recents.di.RecentsDependencies
import com.android.quickstep.recents.di.inject
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.SnapshotSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.quickstep.task.viewmodel.TaskThumbnailViewModel
import com.android.quickstep.util.TaskCornerRadius
import com.android.quickstep.views.FixedSizeImageView
import com.android.systemui.shared.system.QuickStepContract
import kotlin.math.abs
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TaskThumbnailView : ConstraintLayout, ViewPool.Reusable {

    private val viewData: TaskThumbnailViewData by RecentsDependencies.inject(this)
    private val viewModel: TaskThumbnailViewModel by RecentsDependencies.inject(this)

    private lateinit var viewAttachedScope: CoroutineScope

    private val scrimView: View by lazy { findViewById(R.id.task_thumbnail_scrim) }
    private val liveTileView: LiveTileView by lazy { findViewById(R.id.task_thumbnail_live_tile) }
    private val thumbnailView: FixedSizeImageView by lazy { findViewById(R.id.task_thumbnail) }
    private val splashBackground: View by lazy { findViewById(R.id.splash_background) }
    private val splashIcon: FixedSizeImageView by lazy { findViewById(R.id.splash_icon) }

    private var uiState: TaskThumbnailUiState = Uninitialized
    private var inheritedScale: Float = 1f

    private val _measuredBounds = Rect()
    private val measuredBounds: Rect
        get() {
            _measuredBounds.set(0, 0, measuredWidth, measuredHeight)
            return _measuredBounds
        }

    private var overviewCornerRadius: Float = TaskCornerRadius.get(context)
    private var fullscreenCornerRadius: Float = QuickStepContract.getWindowCornerRadius(context)

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewAttachedScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineName("TaskThumbnailView"))
        viewModel.uiState
            .onEach { viewModelUiState ->
                uiState = viewModelUiState
                resetViews()
                when (viewModelUiState) {
                    is Uninitialized -> {}
                    is LiveTile -> drawLiveWindow()
                    is SnapshotSplash -> drawSnapshotSplash(viewModelUiState)
                    is BackgroundOnly -> drawBackground(viewModelUiState.backgroundColor)
                }
            }
            .launchIn(viewAttachedScope)
        viewModel.dimProgress
            .onEach { dimProgress -> scrimView.alpha = dimProgress }
            .launchIn(viewAttachedScope)
        viewModel.splashAlpha
            .onEach { splashAlpha ->
                splashBackground.alpha = splashAlpha
                splashIcon.alpha = splashAlpha
            }
            .launchIn(viewAttachedScope)
        viewModel.cornerRadiusProgress.onEach { invalidateOutline() }.launchIn(viewAttachedScope)
        viewModel.inheritedScale
            .onEach { viewModelInheritedScale ->
                inheritedScale = viewModelInheritedScale
                invalidateOutline()
            }
            .launchIn(viewAttachedScope)

        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(measuredBounds, getCurrentCornerRadius())
                }
            }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewAttachedScope.cancel("TaskThumbnailView detaching from window")
    }

    override fun onRecycle() {
        uiState = Uninitialized
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            viewData.width.value = abs(right - left)
            viewData.height.value = abs(bottom - top)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (uiState is SnapshotSplash) {
            setImageMatrix()
        }
    }

    override fun setScaleX(scaleX: Float) {
        super.setScaleX(scaleX)
        // Splash icon should ignore scale on TTV
        splashIcon.scaleX = 1 / scaleX
    }

    override fun setScaleY(scaleY: Float) {
        super.setScaleY(scaleY)
        // Splash icon should ignore scale on TTV
        splashIcon.scaleY = 1 / scaleY
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        overviewCornerRadius = TaskCornerRadius.get(context)
        fullscreenCornerRadius = QuickStepContract.getWindowCornerRadius(context)
        invalidateOutline()
    }

    private fun resetViews() {
        liveTileView.isInvisible = true
        thumbnailView.isInvisible = true
        splashBackground.alpha = 0f
        splashIcon.alpha = 0f
        scrimView.alpha = 0f
        setBackgroundColor(Color.BLACK)
    }

    private fun drawBackground(@ColorInt background: Int) {
        setBackgroundColor(background)
    }

    private fun drawLiveWindow() {
        liveTileView.isInvisible = false
    }

    private fun drawSnapshotSplash(snapshotSplash: SnapshotSplash) {
        drawSnapshot(snapshotSplash.snapshot)

        splashBackground.setBackgroundColor(snapshotSplash.snapshot.backgroundColor)
        splashIcon.setImageDrawable(snapshotSplash.splash)
    }

    private fun drawSnapshot(snapshot: Snapshot) {
        drawBackground(snapshot.backgroundColor)
        thumbnailView.setImageBitmap(snapshot.bitmap)
        thumbnailView.isInvisible = false
        setImageMatrix()
    }

    private fun setImageMatrix() {
        thumbnailView.imageMatrix = viewModel.getThumbnailPositionState(width, height, isLayoutRtl)
    }

    private fun getCurrentCornerRadius() =
        Utilities.mapRange(
            viewModel.cornerRadiusProgress.value,
            overviewCornerRadius,
            fullscreenCornerRadius
        ) / inheritedScale
}
