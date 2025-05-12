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
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [SysUiStatusNavFlagsUseCase] */
class SysUiStatusNavFlagsUseCaseTest {
    private lateinit var tasksRepository: FakeTasksRepository
    private lateinit var sysUiStatusNavFlagsUseCase: SysUiStatusNavFlagsUseCase

    @Before
    fun setup() {
        tasksRepository = FakeTasksRepository()
        sysUiStatusNavFlagsUseCase = SysUiStatusNavFlagsUseCase(tasksRepository)
        initTaskRepository()
    }

    @Test
    fun onLightAppearanceReturnExpectedFlags() {
        assertThat(sysUiStatusNavFlagsUseCase.getSysUiStatusNavFlags(FIRST_TASK_ID))
            .isEqualTo(FLAGS_APPEARANCE_LIGHT_THEME)
    }

    @Test
    fun onDarkAppearanceReturnExpectedFlags() {
        assertThat(sysUiStatusNavFlagsUseCase.getSysUiStatusNavFlags(SECOND_TASK_ID))
            .isEqualTo(FLAGS_APPEARANCE_DARK_THEME)
    }

    @Test
    fun whenThumbnailIsNullReturnDefault() {
        assertThat(sysUiStatusNavFlagsUseCase.getSysUiStatusNavFlags(UNKNOWN_TASK_ID))
            .isEqualTo(FLAGS_DEFAULT)
    }

    private fun initTaskRepository() {
        val firstTask =
            Task(Task.TaskKey(FIRST_TASK_ID, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
                colorBackground = Color.BLACK
            }
        val firstThumbnailData =
            ThumbnailData(
                thumbnail =
                    mock<Bitmap>().apply {
                        whenever(width).thenReturn(THUMBNAIL_WIDTH)
                        whenever(height).thenReturn(THUMBNAIL_HEIGHT)
                    },
                appearance = APPEARANCE_LIGHT_THEME,
            )

        val secondTask =
            Task(Task.TaskKey(SECOND_TASK_ID, 0, Intent(), ComponentName("", ""), 0, 2005)).apply {
                colorBackground = Color.BLACK
            }
        val secondThumbnailData =
            ThumbnailData(
                thumbnail =
                    mock<Bitmap>().apply {
                        whenever(width).thenReturn(THUMBNAIL_WIDTH)
                        whenever(height).thenReturn(THUMBNAIL_HEIGHT)
                    },
                appearance = APPEARANCE_DARK_THEME,
            )

        tasksRepository.seedTasks(listOf(firstTask, secondTask))
        tasksRepository.seedThumbnailData(
            mapOf(FIRST_TASK_ID to firstThumbnailData, SECOND_TASK_ID to secondThumbnailData)
        )
        tasksRepository.setVisibleTasks(setOf(FIRST_TASK_ID, SECOND_TASK_ID))
    }

    companion object {
        const val FIRST_TASK_ID = 0
        const val SECOND_TASK_ID = 100
        const val UNKNOWN_TASK_ID = 404
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200
        const val APPEARANCE_LIGHT_THEME = 24
        const val FLAGS_APPEARANCE_LIGHT_THEME = 5
        const val APPEARANCE_DARK_THEME = 0
        const val FLAGS_APPEARANCE_DARK_THEME = 10
        const val FLAGS_DEFAULT = 0
    }
}
