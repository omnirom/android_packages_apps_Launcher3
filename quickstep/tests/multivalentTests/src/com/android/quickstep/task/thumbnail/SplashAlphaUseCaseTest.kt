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

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.Surface
import android.view.Surface.ROTATION_90
import com.android.quickstep.recents.data.FakeRecentsRotationStateRepository
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.viewmodel.TaskContainerData
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SplashAlphaUseCaseTest {
    private val recentsViewData = RecentsViewData()
    private val taskContainerData = TaskContainerData()
    private val taskThumbnailViewData = TaskThumbnailViewData()
    private val recentTasksRepository = FakeTasksRepository()
    private val recentsRotationStateRepository = FakeRecentsRotationStateRepository()
    private val systemUnderTest =
        SplashAlphaUseCase(
            recentsViewData,
            taskContainerData,
            taskThumbnailViewData,
            recentTasksRepository,
            recentsRotationStateRepository,
        )

    @Test
    fun execute_withNullThumbnail_showsSplash() = runTest {
        assertThat(systemUnderTest.execute(0).first()).isEqualTo(SPLASH_HIDDEN)
    }

    @Test
    fun execute_withTaskSpecificSplashAlpha_showsSplash() = runTest {
        setupTask(2)
        taskContainerData.thumbnailSplashProgress.value = 0.7f

        assertThat(systemUnderTest.execute(2).first()).isEqualTo(0.7f)
    }

    @Test
    fun execute_withNoGlobalSplashEnabled_doesntShowSplash() = runTest {
        setupTask(2)

        assertThat(systemUnderTest.execute(2).first()).isEqualTo(SPLASH_HIDDEN)
    }

    @Test
    fun execute_withSameAspectRatioAndRotation_withGlobalSplashEnabled_doesntShowSplash() =
        runTest {
            setupTask(2)
            recentsViewData.thumbnailSplashProgress.value = 0.5f
            taskThumbnailViewData.width.value = THUMBNAIL_WIDTH * 2
            taskThumbnailViewData.height.value = THUMBNAIL_HEIGHT * 2

            assertThat(systemUnderTest.execute(2).first()).isEqualTo(SPLASH_HIDDEN)
        }

    @Test
    fun execute_withDifferentAspectRatioAndSameRotation_showsSplash() = runTest {
        setupTask(2)
        recentsViewData.thumbnailSplashProgress.value = 0.5f
        taskThumbnailViewData.width.value = THUMBNAIL_WIDTH
        taskThumbnailViewData.height.value = THUMBNAIL_HEIGHT * 2

        assertThat(systemUnderTest.execute(2).first()).isEqualTo(0.5f)
    }

    @Test
    fun execute_withSameAspectRatioAndDifferentRotation_showsSplash() = runTest {
        setupTask(2, createThumbnailData(rotation = ROTATION_90))
        recentsViewData.thumbnailSplashProgress.value = 0.5f
        taskThumbnailViewData.width.value = THUMBNAIL_WIDTH * 2
        taskThumbnailViewData.height.value = THUMBNAIL_HEIGHT * 2

        assertThat(systemUnderTest.execute(2).first()).isEqualTo(0.5f)
    }

    @Test
    fun execute_withDifferentAspectRatioAndRotation_showsSplash() = runTest {
        setupTask(2, createThumbnailData(rotation = ROTATION_90))
        recentsViewData.thumbnailSplashProgress.value = 0.5f
        taskThumbnailViewData.width.value = THUMBNAIL_WIDTH
        taskThumbnailViewData.height.value = THUMBNAIL_HEIGHT * 2

        assertThat(systemUnderTest.execute(2).first()).isEqualTo(0.5f)
    }

    private val tasks = (0..5).map(::createTaskWithId)

    private fun setupTask(taskId: Int, thumbnailData: ThumbnailData = createThumbnailData()) {
        recentTasksRepository.seedThumbnailData(mapOf(taskId to thumbnailData))
        val expectedIconData = mock<Drawable>()
        recentTasksRepository.seedIconData(taskId, "Task $taskId", "", expectedIconData)
        recentTasksRepository.seedTasks(tasks)
        recentTasksRepository.setVisibleTasks(setOf(taskId))
    }

    private fun createThumbnailData(
        rotation: Int = Surface.ROTATION_0,
        width: Int = THUMBNAIL_WIDTH,
        height: Int = THUMBNAIL_HEIGHT,
    ): ThumbnailData {
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(width)
        whenever(bitmap.height).thenReturn(height)

        return ThumbnailData(thumbnail = bitmap, rotation = rotation)
    }

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
            colorBackground = Color.argb(taskId, taskId, taskId, taskId)
        }

    companion object {
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200

        const val SPLASH_HIDDEN = 0f
        const val SPLASH_SHOWN = 1f
    }
}
