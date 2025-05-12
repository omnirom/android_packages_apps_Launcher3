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
import android.graphics.Color
import android.view.LayoutInflater
import com.android.launcher3.R
import com.android.quickstep.recents.di.RecentsDependencies
import com.android.quickstep.task.viewmodel.TaskThumbnailViewModel
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.ViewScreenshotTestRule
import platform.test.screenshot.getEmulatedDevicePathConfig

/** Screenshot tests for [TaskThumbnailView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class TaskThumbnailViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {

    @get:Rule
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    private val taskThumbnailViewModel = FakeTaskThumbnailViewModel()

    @Test
    fun taskThumbnailView_uninitialized() {
        screenshotRule.screenshotTest("taskThumbnailView_uninitialized") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity)
        }
    }

    @Test
    fun taskThumbnailView_backgroundOnly() {
        screenshotRule.screenshotTest("taskThumbnailView_backgroundOnly") { activity ->
            activity.actionBar?.hide()
            taskThumbnailViewModel.uiState.value = TaskThumbnailUiState.BackgroundOnly(Color.YELLOW)
            createTaskThumbnailView(activity)
        }
    }

    private fun createTaskThumbnailView(context: Context): TaskThumbnailView {
        val di = RecentsDependencies.initialize(context)
        val taskThumbnailView =
            LayoutInflater.from(context).inflate(R.layout.task_thumbnail, null, false)
                as TaskThumbnailView
        taskThumbnailView.cornerRadius = CORNER_RADIUS
        val ttvDiScopeId = di.getScope(taskThumbnailView).scopeId
        di.provide(TaskThumbnailViewData::class.java, ttvDiScopeId) { TaskThumbnailViewData() }
        di.provide(TaskThumbnailViewModel::class.java, ttvDiScopeId) { taskThumbnailViewModel }

        return taskThumbnailView
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() =
            DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isDarkTheme = false,
                isLandscape = false,
            )

        const val CORNER_RADIUS = 56f
    }
}
