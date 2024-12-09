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

import android.graphics.Bitmap
import android.view.Surface
import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.quickstep.recents.data.RecentsRotationStateRepository
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.viewmodel.TaskContainerData
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper
import com.android.systemui.shared.recents.utilities.Utilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class SplashAlphaUseCase(
    private val recentsViewData: RecentsViewData,
    private val taskContainerData: TaskContainerData,
    private val taskThumbnailViewData: TaskThumbnailViewData,
    private val tasksRepository: RecentTasksRepository,
    private val rotationStateRepository: RecentsRotationStateRepository,
) {
    fun execute(taskId: Int): Flow<Float> =
        combine(
                taskThumbnailViewData.width,
                taskThumbnailViewData.height,
                tasksRepository.getThumbnailById(taskId),
                taskContainerData.thumbnailSplashProgress,
                recentsViewData.thumbnailSplashProgress
            ) { width, height, thumbnailData, taskSplashProgress, globalSplashProgress ->
                val thumbnail = thumbnailData?.thumbnail
                when {
                    thumbnail == null -> 0f
                    taskSplashProgress > 0f -> taskSplashProgress
                    globalSplashProgress > 0f &&
                        isInaccurateThumbnail(thumbnail, thumbnailData.rotation, width, height) ->
                        globalSplashProgress
                    else -> 0f
                }
            }
            .distinctUntilChanged()

    private fun isInaccurateThumbnail(
        thumbnail: Bitmap,
        thumbnailRotation: Int,
        width: Int,
        height: Int
    ): Boolean {
        return isThumbnailAspectRatioDifferentFromThumbnailData(thumbnail, width, height) ||
            isThumbnailRotationDifferentFromTask(thumbnailRotation)
    }

    private fun isThumbnailAspectRatioDifferentFromThumbnailData(
        thumbnail: Bitmap,
        viewWidth: Int,
        viewHeight: Int
    ): Boolean {
        val viewAspect: Float = viewWidth / viewHeight.toFloat()
        val thumbnailAspect: Float = thumbnail.width / thumbnail.height.toFloat()
        return Utilities.isRelativePercentDifferenceGreaterThan(
            viewAspect,
            thumbnailAspect,
            PreviewPositionHelper.MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT
        )
    }

    private fun isThumbnailRotationDifferentFromTask(thumbnailRotation: Int): Boolean {
        val rotationState = rotationStateRepository.getRecentsRotationState()
        return if (rotationState.orientationHandlerRotation == Surface.ROTATION_0) {
            (rotationState.activityRotation - thumbnailRotation) % 2 != 0
        } else {
            rotationState.orientationHandlerRotation != thumbnailRotation
        }
    }
}
