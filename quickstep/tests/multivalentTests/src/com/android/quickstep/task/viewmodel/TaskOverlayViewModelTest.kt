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

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.quickstep.recents.usecase.GetThumbnailPositionUseCase
import com.android.quickstep.recents.usecase.ThumbnailPositionState.MatrixScaling
import com.android.quickstep.recents.usecase.ThumbnailPositionState.MissingThumbnail
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.thumbnail.TaskOverlayUiState.Disabled
import com.android.quickstep.task.thumbnail.TaskOverlayUiState.Enabled
import com.android.quickstep.task.viewmodel.TaskOverlayViewModel.ThumbnailPositionState
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [TaskOverlayViewModel] */
@RunWith(AndroidJUnit4::class)
class TaskOverlayViewModelTest {
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
    private val recentsViewData = RecentsViewData()
    private val tasksRepository = FakeTasksRepository()
    private val mGetThumbnailPositionUseCase = mock<GetThumbnailPositionUseCase>()
    private val systemUnderTest =
        TaskOverlayViewModel(task, recentsViewData, mGetThumbnailPositionUseCase, tasksRepository)

    @Test
    fun initialStateIsDisabled() = runTest {
        assertThat(systemUnderTest.overlayState.first()).isEqualTo(Disabled)
    }

    @Test
    fun recentsViewOverlayDisabled_Disabled() = runTest {
        recentsViewData.overlayEnabled.value = false
        recentsViewData.settledFullyVisibleTaskIds.value = setOf(TASK_ID)

        assertThat(systemUnderTest.overlayState.first()).isEqualTo(Disabled)
    }

    @Test
    fun taskNotFullyVisible_Disabled() = runTest {
        recentsViewData.overlayEnabled.value = true
        recentsViewData.settledFullyVisibleTaskIds.value = setOf()

        assertThat(systemUnderTest.overlayState.first()).isEqualTo(Disabled)
    }

    @Test
    fun noThumbnail_Enabled() = runTest {
        recentsViewData.overlayEnabled.value = true
        recentsViewData.settledFullyVisibleTaskIds.value = setOf(TASK_ID)
        task.isLocked = false

        assertThat(systemUnderTest.overlayState.first())
            .isEqualTo(Enabled(isRealSnapshot = false, thumbnail = null))
    }

    @Test
    fun withThumbnail_RealSnapshot_NotLocked_Enabled() = runTest {
        recentsViewData.overlayEnabled.value = true
        recentsViewData.settledFullyVisibleTaskIds.value = setOf(TASK_ID)
        tasksRepository.seedTasks(listOf(task))
        tasksRepository.seedThumbnailData(mapOf(TASK_ID to thumbnailData))
        tasksRepository.setVisibleTasks(setOf(TASK_ID))
        thumbnailData.isRealSnapshot = true
        task.isLocked = false

        assertThat(systemUnderTest.overlayState.first())
            .isEqualTo(Enabled(isRealSnapshot = true, thumbnail = thumbnailData.thumbnail))
    }

    @Test
    fun withThumbnail_RealSnapshot_Locked_Enabled() = runTest {
        recentsViewData.overlayEnabled.value = true
        recentsViewData.settledFullyVisibleTaskIds.value = setOf(TASK_ID)
        tasksRepository.seedTasks(listOf(task))
        tasksRepository.seedThumbnailData(mapOf(TASK_ID to thumbnailData))
        tasksRepository.setVisibleTasks(setOf(TASK_ID))
        thumbnailData.isRealSnapshot = true
        task.isLocked = true

        assertThat(systemUnderTest.overlayState.first())
            .isEqualTo(Enabled(isRealSnapshot = false, thumbnail = thumbnailData.thumbnail))
    }

    @Test
    fun withThumbnail_FakeSnapshot_Enabled() = runTest {
        recentsViewData.overlayEnabled.value = true
        recentsViewData.settledFullyVisibleTaskIds.value = setOf(TASK_ID)
        tasksRepository.seedTasks(listOf(task))
        tasksRepository.seedThumbnailData(mapOf(TASK_ID to thumbnailData))
        tasksRepository.setVisibleTasks(setOf(TASK_ID))
        thumbnailData.isRealSnapshot = false
        task.isLocked = false

        assertThat(systemUnderTest.overlayState.first())
            .isEqualTo(Enabled(isRealSnapshot = false, thumbnail = thumbnailData.thumbnail))
    }

    @Test
    fun getThumbnailMatrix_MissingThumbnail() = runTest {
        val isRtl = true

        whenever(mGetThumbnailPositionUseCase.run(TASK_ID, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .thenReturn(MissingThumbnail)

        assertThat(systemUnderTest.getThumbnailPositionState(CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .isEqualTo(ThumbnailPositionState(Matrix.IDENTITY_MATRIX, isRotated = false))
    }

    @Test
    fun getThumbnailMatrix_MatrixScaling() = runTest {
        val isRtl = true
        val isRotated = true

        whenever(mGetThumbnailPositionUseCase.run(TASK_ID, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .thenReturn(MatrixScaling(MATRIX, isRotated))

        assertThat(systemUnderTest.getThumbnailPositionState(CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .isEqualTo(ThumbnailPositionState(MATRIX, isRotated))
    }

    companion object {
        const val TASK_ID = 0
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
