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
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.quickstep.task.viewmodel.TaskOverlayViewModelTest
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [GetThumbnailUseCase] */
class GetThumbnailUseCaseTest {
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

    private val tasksRepository = FakeTasksRepository()
    private val systemUnderTest = GetThumbnailUseCase(tasksRepository)

    @Test
    fun taskNotSeeded_returnsNull() {
        assertThat(systemUnderTest.run(TASK_ID)).isNull()
    }

    @Test
    fun taskNotLoaded_returnsNull() {
        tasksRepository.seedTasks(listOf(task))

        assertThat(systemUnderTest.run(TASK_ID)).isNull()
    }

    @Test
    fun taskNotVisible_returnsNull() {
        tasksRepository.seedTasks(listOf(task))
        tasksRepository.seedThumbnailData(mapOf(TaskOverlayViewModelTest.TASK_ID to thumbnailData))

        assertThat(systemUnderTest.run(TASK_ID)).isNull()
    }

    @Test
    fun taskVisible_returnsThumbnail() {
        tasksRepository.seedTasks(listOf(task))
        tasksRepository.seedThumbnailData(mapOf(TaskOverlayViewModelTest.TASK_ID to thumbnailData))
        tasksRepository.setVisibleTasks(listOf(TaskOverlayViewModelTest.TASK_ID))

        assertThat(systemUnderTest.run(TASK_ID)).isEqualTo(thumbnailData.thumbnail)
    }

    companion object {
        const val TASK_ID = 0
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200
    }
}
