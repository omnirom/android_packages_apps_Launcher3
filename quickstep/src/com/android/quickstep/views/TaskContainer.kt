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

package com.android.quickstep.views

import android.graphics.Bitmap
import android.view.View
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.ViewUtils.addAccessibleChildToList
import com.android.quickstep.recents.di.RecentsDependencies
import com.android.quickstep.recents.di.get
import com.android.quickstep.recents.di.getScope
import com.android.quickstep.recents.di.inject
import com.android.quickstep.recents.viewmodel.TaskContainerViewModel
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.task.viewmodel.TaskContainerData
import com.android.quickstep.task.viewmodel.TaskThumbnailViewModel
import com.android.systemui.shared.recents.model.Task

/** Holder for all Task dependent information. */
class TaskContainer(
    val taskView: TaskView,
    val task: Task,
    val snapshotView: View,
    val iconView: TaskViewIcon,
    /**
     * This technically can be a vanilla [android.view.TouchDelegate] class, however that class
     * requires setting the touch bounds at construction, so we'd repeatedly be created many
     * instances unnecessarily as scrolling occurs, whereas [TransformingTouchDelegate] allows touch
     * delegated bounds only to be updated.
     */
    val iconTouchDelegate: TransformingTouchDelegate,
    /** Defaults to STAGE_POSITION_UNDEFINED if in not a split screen task view */
    @SplitConfigurationOptions.StagePosition val stagePosition: Int,
    val digitalWellBeingToast: DigitalWellBeingToast?,
    val showWindowsView: View?,
    taskOverlayFactory: TaskOverlayFactory,
) {
    val overlay: TaskOverlayFactory.TaskOverlay<*> = taskOverlayFactory.createOverlay(this)
    lateinit var taskContainerData: TaskContainerData

    private val taskThumbnailViewModel: TaskThumbnailViewModel by
        RecentsDependencies.inject(snapshotView)

    // TODO(b/335649589): Ideally create and obtain this from DI.
    private val taskContainerViewModel: TaskContainerViewModel by lazy {
        TaskContainerViewModel(
            sysUiStatusNavFlagsUseCase = RecentsDependencies.get(),
            getThumbnailUseCase = RecentsDependencies.get(),
            splashAlphaUseCase = RecentsDependencies.get(),
        )
    }

    init {
        if (enableRefactorTaskThumbnail()) {
            require(snapshotView is TaskThumbnailView)
            taskContainerData = RecentsDependencies.get(this)
            RecentsDependencies.getScope(snapshotView).apply {
                val taskViewScope = RecentsDependencies.getScope(taskView)
                linkTo(taskViewScope)

                val taskContainerScope = RecentsDependencies.getScope(this@TaskContainer)
                linkTo(taskContainerScope)
            }
        } else {
            require(snapshotView is TaskThumbnailViewDeprecated)
        }
    }

    val splitAnimationThumbnail: Bitmap?
        get() =
            if (enableRefactorTaskThumbnail()) {
                taskContainerViewModel.getThumbnail(task.key.id)
            } else {
                thumbnailViewDeprecated.thumbnail
            }

    val thumbnailView: TaskThumbnailView
        get() {
            require(enableRefactorTaskThumbnail())
            return snapshotView as TaskThumbnailView
        }

    val thumbnailViewDeprecated: TaskThumbnailViewDeprecated
        get() {
            require(!enableRefactorTaskThumbnail())
            return snapshotView as TaskThumbnailViewDeprecated
        }

    val shouldShowSplashView: Boolean
        get() =
            if (enableRefactorTaskThumbnail())
                taskContainerViewModel.shouldShowThumbnailSplash(task.key.id)
            else thumbnailViewDeprecated.shouldShowSplashView()

    val sysUiStatusNavFlags: Int
        get() =
            if (enableRefactorTaskThumbnail())
                taskContainerViewModel.getSysUiStatusNavFlags(task.key.id)
            else thumbnailViewDeprecated.sysUiStatusNavFlags

    /** Builds proto for logging */
    val itemInfo: TaskViewItemInfo
        get() = TaskViewItemInfo(this)

    fun bind() {
        digitalWellBeingToast?.bind(task, taskView, snapshotView, stagePosition)
        if (enableRefactorTaskThumbnail()) {
            bindThumbnailView()
        } else {
            thumbnailViewDeprecated.bind(task, overlay, taskView)
        }
        overlay.init()
    }

    fun destroy() {
        digitalWellBeingToast?.destroy()
        snapshotView.scaleX = 1f
        snapshotView.scaleY = 1f
        overlay.destroy()
        if (enableRefactorTaskThumbnail()) {
            RecentsDependencies.getInstance().removeScope(snapshotView)
            RecentsDependencies.getInstance().removeScope(this)
        }
    }

    fun bindThumbnailView() {
        taskThumbnailViewModel.bind(task.key.id)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        if (!enableRefactorTaskThumbnail()) {
            thumbnailViewDeprecated.setOverlayEnabled(enabled)
        }
    }

    fun addChildForAccessibility(outChildren: ArrayList<View>) {
        addAccessibleChildToList(iconView.asView(), outChildren)
        addAccessibleChildToList(snapshotView, outChildren)
        showWindowsView?.let { addAccessibleChildToList(it, outChildren) }
        digitalWellBeingToast?.let { addAccessibleChildToList(it, outChildren) }
        overlay.addChildForAccessibility(outChildren)
    }
}
