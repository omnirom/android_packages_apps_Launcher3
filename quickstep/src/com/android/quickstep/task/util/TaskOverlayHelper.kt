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

package com.android.quickstep.task.util

import android.util.Log
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.recents.di.RecentsDependencies
import com.android.quickstep.recents.di.get
import com.android.quickstep.task.thumbnail.TaskOverlayUiState
import com.android.quickstep.task.thumbnail.TaskOverlayUiState.Disabled
import com.android.quickstep.task.thumbnail.TaskOverlayUiState.Enabled
import com.android.quickstep.task.viewmodel.TaskOverlayViewModel
import com.android.systemui.shared.recents.model.Task
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Helper for [TaskOverlayFactory.TaskOverlay] to interact with [TaskOverlayViewModel], this helper
 * should merge with [TaskOverlayFactory.TaskOverlay] when it's migrated to MVVM.
 */
class TaskOverlayHelper(val task: Task, val overlay: TaskOverlayFactory.TaskOverlay<*>) {
    private lateinit var overlayInitializedScope: CoroutineScope
    private var uiState: TaskOverlayUiState = Disabled

    private val viewModel: TaskOverlayViewModel by lazy {
        TaskOverlayViewModel(
            task = task,
            recentsViewData = RecentsDependencies.get(),
            getThumbnailPositionUseCase = RecentsDependencies.get(),
            recentTasksRepository = RecentsDependencies.get()
        )
    }

    // TODO(b/331753115): TaskOverlay should listen for state changes and react.
    val enabledState: Enabled
        get() = uiState as Enabled

    fun getThumbnailMatrix() = getThumbnailPositionState().matrix

    private fun getThumbnailPositionState() =
        viewModel.getThumbnailPositionState(
            overlay.snapshotView.width,
            overlay.snapshotView.height,
            overlay.snapshotView.isLayoutRtl
        )

    fun init() {
        overlayInitializedScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineName("TaskOverlayHelper"))
        viewModel.overlayState
            .onEach {
                uiState = it
                if (it is Enabled) {
                    initOverlay(it)
                } else {
                    reset()
                }
            }
            .launchIn(overlayInitializedScope)
        overlay.snapshotView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            (uiState as? Enabled)?.let { initOverlay(it) }
        }
    }

    private fun initOverlay(enabledState: Enabled) {
        Log.d(TAG, "initOverlay - taskId: ${task.key.id}, thumbnail: ${enabledState.thumbnail}")
        with(getThumbnailPositionState()) {
            overlay.initOverlay(task, enabledState.thumbnail, matrix, isRotated)
        }
    }

    private fun reset() {
        Log.d(TAG, "reset - taskId: ${task.key.id}")
        overlay.reset()
    }

    fun destroy() {
        overlayInitializedScope.cancel()
        uiState = Disabled
        reset()
    }

    companion object {
        private const val TAG = "TaskOverlayHelper"
    }
}
