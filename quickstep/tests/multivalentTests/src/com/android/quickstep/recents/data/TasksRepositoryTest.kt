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

package com.android.quickstep.recents.data

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.android.launcher3.util.TestDispatcherProvider
import com.android.quickstep.task.thumbnail.TaskThumbnailViewModelTest
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TasksRepositoryTest {
    private val tasks = (0..5).map(::createTaskWithId)
    private val defaultTaskList =
        listOf(
            GroupTask(tasks[0]),
            GroupTask(tasks[1], tasks[2], null),
            DesktopTask(tasks.subList(3, 6))
        )
    private val recentsModel = FakeRecentTasksDataSource()
    private val taskThumbnailDataSource = FakeTaskThumbnailDataSource()
    private val taskIconDataSource = FakeTaskIconDataSource()
    private val taskVisualsChangeNotifier = FakeTaskVisualsChangeNotifier()
    private val highResLoadingStateNotifier = FakeHighResLoadingStateNotifier()
    private val taskVisualsChangedDelegate =
        TaskVisualsChangedDelegateImpl(taskVisualsChangeNotifier, highResLoadingStateNotifier)

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val systemUnderTest =
        TasksRepository(
            recentsModel,
            taskThumbnailDataSource,
            taskIconDataSource,
            taskVisualsChangedDelegate,
            testScope.backgroundScope,
            TestDispatcherProvider(dispatcher)
        )

    @Test
    fun getAllTaskDataReturnsFlattenedListOfTasks() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)

            assertThat(systemUnderTest.getAllTaskData(forceRefresh = true).first()).isEqualTo(tasks)
        }

    @Test
    fun getTaskDataByIdReturnsSpecificTask() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            assertThat(systemUnderTest.getTaskDataById(2).first()).isEqualTo(tasks[2])
        }

    @Test
    fun setVisibleTasksPopulatesThumbnails() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            val bitmap1 = taskThumbnailDataSource.taskIdToBitmap[1]
            val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(listOf(1, 2))

            assertThat(systemUnderTest.getTaskDataById(1).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap1)
            assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap2)
        }

    @Test
    fun setVisibleTasksPopulatesIcons() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(listOf(1, 2))

            systemUnderTest
                .getTaskDataById(1)
                .first()!!
                .assertHasIconDataFromSource(taskIconDataSource)
            systemUnderTest
                .getTaskDataById(2)
                .first()!!
                .assertHasIconDataFromSource(taskIconDataSource)
        }

    @Test
    fun changingVisibleTasksContainsAlreadyPopulatedThumbnails() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(listOf(1, 2))

            assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap2)

            // Prevent new loading of Bitmaps
            taskThumbnailDataSource.shouldLoadSynchronously = false
            systemUnderTest.setVisibleTasks(listOf(2, 3))

            assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap2)
        }

    @Test
    fun changingVisibleTasksContainsAlreadyPopulatedIcons() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(listOf(1, 2))

            systemUnderTest
                .getTaskDataById(2)
                .first()!!
                .assertHasIconDataFromSource(taskIconDataSource)

            // Prevent new loading of Drawables
            taskThumbnailDataSource.shouldLoadSynchronously = false
            systemUnderTest.setVisibleTasks(listOf(2, 3))

            systemUnderTest
                .getTaskDataById(2)
                .first()!!
                .assertHasIconDataFromSource(taskIconDataSource)
        }

    @Test
    fun retrievedImagesAreDiscardedWhenTaskBecomesInvisible() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(listOf(1, 2))

            val task2 = systemUnderTest.getTaskDataById(2).first()!!
            assertThat(task2.thumbnail!!.thumbnail).isEqualTo(bitmap2)
            task2.assertHasIconDataFromSource(taskIconDataSource)

            // Prevent new loading of Bitmaps
            taskThumbnailDataSource.shouldLoadSynchronously = false
            taskIconDataSource.shouldLoadSynchronously = false
            systemUnderTest.setVisibleTasks(listOf(0, 1))

            val task2AfterVisibleTasksChanged = systemUnderTest.getTaskDataById(2).first()!!
            assertThat(task2AfterVisibleTasksChanged.thumbnail).isNull()
            assertThat(task2AfterVisibleTasksChanged.icon).isNull()
            assertThat(task2AfterVisibleTasksChanged.titleDescription).isNull()
            assertThat(task2AfterVisibleTasksChanged.title).isNull()
        }

    @Test
    fun retrievedThumbnailsCauseEmissionOnTaskDataFlow() =
        testScope.runTest {
            // Setup fakes
            recentsModel.seedTasks(defaultTaskList)
            val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
            taskThumbnailDataSource.shouldLoadSynchronously = false

            // Setup TasksRepository
            systemUnderTest.getAllTaskData(forceRefresh = true)
            systemUnderTest.setVisibleTasks(listOf(1, 2))

            // Assert there is no bitmap in first emission
            assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail).isNull()

            // Simulate bitmap loading after first emission
            taskThumbnailDataSource.taskIdToUpdatingTask.getValue(2).invoke()

            // Check for second emission
            assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap2)
        }

    @Test
    fun onTaskThumbnailChanged_setsNewThumbnailDataOnTask() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(listOf(1))

            val expectedThumbnailData = createThumbnailData()
            val expectedPreviousBitmap = taskThumbnailDataSource.taskIdToBitmap[1]
            val taskDataFlow = systemUnderTest.getTaskDataById(1)

            val task1ThumbnailValues = mutableListOf<ThumbnailData?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.thumbnail }.toList(task1ThumbnailValues)
            }
            taskVisualsChangedDelegate.onTaskThumbnailChanged(1, expectedThumbnailData)

            assertThat(task1ThumbnailValues[1]!!.thumbnail).isEqualTo(expectedPreviousBitmap)
            assertThat(task1ThumbnailValues.last()).isEqualTo(expectedThumbnailData)
        }

    @Test
    fun onHighResLoadingStateChanged_setsNewThumbnailDataOnTask() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(listOf(1))

            val expectedBitmap = mock<Bitmap>()
            val expectedPreviousBitmap = taskThumbnailDataSource.taskIdToBitmap[1]
            val taskDataFlow = systemUnderTest.getTaskDataById(1)

            val task1ThumbnailValues = mutableListOf<Bitmap?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.thumbnail?.thumbnail }.toList(task1ThumbnailValues)
            }
            taskThumbnailDataSource.taskIdToBitmap[1] = expectedBitmap
            taskVisualsChangedDelegate.onHighResLoadingStateChanged(true)

            assertThat(task1ThumbnailValues[1]).isEqualTo(expectedPreviousBitmap)
            assertThat(task1ThumbnailValues.last()).isEqualTo(expectedBitmap)
        }

    @Test
    fun onTaskIconChanged_setsNewIconOnTask() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(listOf(1))

            val expectedIcon = FakeTaskIconDataSource.mockCopyableDrawable()
            val expectedPreviousIcon = taskIconDataSource.taskIdToDrawable[1]
            val taskDataFlow = systemUnderTest.getTaskDataById(1)

            val task1IconValues = mutableListOf<Drawable?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.icon }.toList(task1IconValues)
            }
            taskIconDataSource.taskIdToDrawable[1] = expectedIcon
            taskVisualsChangedDelegate.onTaskIconChanged(1)

            assertThat(task1IconValues[1]).isEqualTo(expectedPreviousIcon)
            assertThat(task1IconValues.last()).isEqualTo(expectedIcon)
        }

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000))

    private fun createThumbnailData(): ThumbnailData {
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(TaskThumbnailViewModelTest.THUMBNAIL_WIDTH)
        whenever(bitmap.height).thenReturn(TaskThumbnailViewModelTest.THUMBNAIL_HEIGHT)

        return ThumbnailData(thumbnail = bitmap)
    }
}
