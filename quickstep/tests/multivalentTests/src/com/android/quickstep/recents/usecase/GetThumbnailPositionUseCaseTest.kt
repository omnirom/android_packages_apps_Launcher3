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

package com.android.quickstep.recents.usecase

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.view.Surface.ROTATION_90
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.FakeRecentsDeviceProfileRepository
import com.android.quickstep.recents.data.FakeRecentsRotationStateRepository
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.quickstep.recents.usecase.ThumbnailPositionState.MatrixScaling
import com.android.quickstep.recents.usecase.ThumbnailPositionState.MissingThumbnail
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Test for [GetThumbnailPositionUseCase] */
@RunWith(AndroidJUnit4::class)
class GetThumbnailPositionUseCaseTest {
    private val task =
        Task(Task.TaskKey(TASK_ID, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
            colorBackground = Color.BLACK
        }
    private val thumbnailData =
        ThumbnailData(
            thumbnail =
                mock<Bitmap>().apply {
                    whenever(width).thenReturn(THUMBNAIL_WIDTH)
                    whenever(height).thenReturn(THUMBNAIL_HEIGHT)
                }
        )

    private val deviceProfileRepository = FakeRecentsDeviceProfileRepository()
    private val rotationStateRepository = FakeRecentsRotationStateRepository()
    private val tasksRepository = FakeTasksRepository()
    private val previewPositionHelper = mock<PreviewPositionHelper>()

    private val systemUnderTest =
        GetThumbnailPositionUseCase(
            deviceProfileRepository,
            rotationStateRepository,
            tasksRepository,
            previewPositionHelper,
        )

    @Test
    fun invisibleTask_returnsIdentityMatrix() = runTest {
        tasksRepository.seedTasks(listOf(task))

        assertThat(systemUnderTest.run(TASK_ID, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl = true))
            .isInstanceOf(MissingThumbnail::class.java)
    }

    @Test
    fun visibleTaskWithoutThumbnailData_returnsIdentityMatrix() = runTest {
        tasksRepository.seedTasks(listOf(task))
        tasksRepository.setVisibleTasks(setOf(TASK_ID))

        assertThat(systemUnderTest.run(TASK_ID, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl = true))
            .isInstanceOf(MissingThumbnail::class.java)
    }

    @Test
    fun visibleTaskWithThumbnailData_returnsTransformedMatrix() = runTest {
        tasksRepository.seedThumbnailData(mapOf(TASK_ID to thumbnailData))
        tasksRepository.seedTasks(listOf(task))
        tasksRepository.setVisibleTasks(setOf(TASK_ID))

        val isLargeScreen = true
        deviceProfileRepository.setRecentsDeviceProfile(
            deviceProfileRepository.getRecentsDeviceProfile().copy(isLargeScreen = isLargeScreen)
        )
        val activityRotation = ROTATION_90
        rotationStateRepository.setRecentsRotationState(
            rotationStateRepository
                .getRecentsRotationState()
                .copy(activityRotation = activityRotation)
        )
        val isRtl = true
        val isRotated = true

        whenever(previewPositionHelper.matrix).thenReturn(MATRIX)
        whenever(previewPositionHelper.isOrientationChanged).thenReturn(isRotated)

        assertThat(systemUnderTest.run(TASK_ID, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .isEqualTo(MatrixScaling(MATRIX, isRotated))

        verify(previewPositionHelper)
            .updateThumbnailMatrix(
                Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
                thumbnailData,
                CANVAS_WIDTH,
                CANVAS_HEIGHT,
                isLargeScreen,
                activityRotation,
                isRtl,
            )
    }

    companion object {
        const val TASK_ID = 2
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200
        const val CANVAS_WIDTH = 300
        const val CANVAS_HEIGHT = 600
        val MATRIX =
            Matrix().apply {
                setValues(floatArrayOf(2.3f, 4.5f, 2.6f, 7.4f, 3.4f, 2.3f, 2.5f, 6.0f, 3.4f))
            }
    }
}
