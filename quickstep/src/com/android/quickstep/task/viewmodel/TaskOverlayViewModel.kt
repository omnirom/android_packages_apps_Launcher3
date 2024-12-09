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

package com.android.quickstep.task.viewmodel

import android.graphics.Matrix
import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.quickstep.recents.usecase.GetThumbnailPositionUseCase
import com.android.quickstep.recents.usecase.ThumbnailPositionState.MatrixScaling
import com.android.quickstep.recents.usecase.ThumbnailPositionState.MissingThumbnail
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.thumbnail.TaskOverlayUiState.Disabled
import com.android.quickstep.task.thumbnail.TaskOverlayUiState.Enabled
import com.android.systemui.shared.recents.model.Task
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** View model for TaskOverlay */
class TaskOverlayViewModel(
    private val task: Task,
    recentsViewData: RecentsViewData,
    private val getThumbnailPositionUseCase: GetThumbnailPositionUseCase,
    recentTasksRepository: RecentTasksRepository,
) {
    val overlayState =
        combine(
                recentsViewData.overlayEnabled,
                recentsViewData.settledFullyVisibleTaskIds.map { it.contains(task.key.id) },
                recentTasksRepository.getThumbnailById(task.key.id)
            ) { isOverlayEnabled, isFullyVisible, thumbnailData ->
                if (isOverlayEnabled && isFullyVisible) {
                    Enabled(
                        isRealSnapshot = (thumbnailData?.isRealSnapshot ?: false) && !task.isLocked,
                        thumbnailData?.thumbnail,
                    )
                } else {
                    Disabled
                }
            }
            .distinctUntilChanged()

    fun getThumbnailPositionState(width: Int, height: Int, isRtl: Boolean): ThumbnailPositionState {
        return runBlocking {
            val matrix: Matrix
            val isRotated: Boolean
            when (
                val thumbnailPositionState =
                    getThumbnailPositionUseCase.run(task.key.id, width, height, isRtl)
            ) {
                is MatrixScaling -> {
                    matrix = thumbnailPositionState.matrix
                    isRotated = thumbnailPositionState.isRotated
                }
                is MissingThumbnail -> {
                    matrix = Matrix.IDENTITY_MATRIX
                    isRotated = false
                }
            }
            ThumbnailPositionState(matrix, isRotated)
        }
    }

    data class ThumbnailPositionState(val matrix: Matrix, val isRotated: Boolean)
}
