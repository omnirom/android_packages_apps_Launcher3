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

package com.android.launcher3.taskbar

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Process
import android.os.UserHandle
import android.platform.test.rule.TestWatcher
import android.testing.AndroidTestingRunner
import com.android.internal.R
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo
import com.android.quickstep.RecentsModel
import com.android.quickstep.RecentsModel.RecentTasksChangedListener
import com.android.quickstep.TaskIconCache
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidTestingRunner::class)
class TaskbarRecentAppsControllerTest : TaskbarBaseTestCase() {

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule
    val disableControllerForCertainTestsWatcher =
        object : TestWatcher() {
            override fun starting(description: Description) {
                // Update canShowRunningAndRecentAppsAtInit before setUp() is called for each test.
                canShowRunningAndRecentAppsAtInit =
                    description.methodName !in
                        listOf("canShowRunningAndRecentAppsAtInitIsFalse_getTasksNeverCalled")
            }
        }

    @Mock private lateinit var mockIconCache: TaskIconCache
    @Mock private lateinit var mockRecentsModel: RecentsModel
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockResources: Resources

    private var taskListChangeId: Int = 1

    private lateinit var recentAppsController: TaskbarRecentAppsController
    private lateinit var userHandle: UserHandle

    private var canShowRunningAndRecentAppsAtInit = true
    private var recentTasksChangedListener: RecentTasksChangedListener? = null

    @Before
    fun setUp() {
        super.setup()
        userHandle = Process.myUserHandle()

        // Set desktop mode supported
        whenever(mockContext.getResources()).thenReturn(mockResources)
        whenever(mockResources.getBoolean(R.bool.config_isDesktopModeSupported)).thenReturn(true)

        whenever(mockRecentsModel.iconCache).thenReturn(mockIconCache)
        whenever(mockRecentsModel.unregisterRecentTasksChangedListener()).then {
            recentTasksChangedListener = null
            it
        }
        recentAppsController = TaskbarRecentAppsController(mockContext, mockRecentsModel)
        recentAppsController.canShowRunningApps = canShowRunningAndRecentAppsAtInit
        recentAppsController.canShowRecentApps = canShowRunningAndRecentAppsAtInit
        recentAppsController.init(taskbarControllers)
        taskbarControllers.onPostInit()

        recentTasksChangedListener =
            if (canShowRunningAndRecentAppsAtInit) {
                val listenerCaptor = ArgumentCaptor.forClass(RecentTasksChangedListener::class.java)
                verify(mockRecentsModel)
                    .registerRecentTasksChangedListener(listenerCaptor.capture())
                listenerCaptor.value
            } else {
                verify(mockRecentsModel, never()).registerRecentTasksChangedListener(any())
                null
            }

        // Make sure updateHotseatItemInfos() is called after commitRunningAppsToUI()
        whenever(taskbarViewController.commitRunningAppsToUI()).then {
            recentAppsController.updateHotseatItemInfos(
                recentAppsController.shownHotseatItems.toTypedArray()
            )
        }
    }

    // See the TestWatcher rule at the top which sets canShowRunningAndRecentAppsAtInit = false.
    @Test
    fun canShowRunningAndRecentAppsAtInitIsFalse_getTasksNeverCalled() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = listOf(createTask(1, RUNNING_APP_PACKAGE_1)),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(mockRecentsModel, never()).getTasks(any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun canShowRunningAndRecentAppsIsFalseAfterInit_getTasksOnlyCalledInInit() {
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any<Consumer<List<GroupTask>>>())
        recentAppsController.canShowRunningApps = false
        recentAppsController.canShowRecentApps = false
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = listOf(createTask(1, RUNNING_APP_PACKAGE_1)),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        // Verify that getTasks() was not called again after the init().
        verify(mockRecentsModel, times(1)).getTasks(any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun updateHotseatItemInfos_cantShowRunning_inDesktopMode_returnsAllHotseatItems() {
        recentAppsController.canShowRunningApps = false
        setInDesktopMode(true)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = hotseatPackages,
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(hotseatPackages)
    }

    @Test
    fun updateHotseatItemInfos_cantShowRecent_notInDesktopMode_returnsAllHotseatItems() {
        recentAppsController.canShowRecentApps = false
        setInDesktopMode(false)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = hotseatPackages,
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(hotseatPackages)
    }

    @Test
    fun updateHotseatItemInfos_canShowRunning_inDesktopMode_returnsNonPredictedHotseatItems() {
        recentAppsController.canShowRunningApps = true
        setInDesktopMode(true)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        val expectedPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun updateHotseatItemInfos_inDesktopMode_hotseatPackageHasRunningTask_hotseatItemLinksToTask() {
        setInDesktopMode(true)

        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks = listOf(createTask(id = 1, HOTSEAT_PACKAGE_1)),
                recentTaskPackages = emptyList(),
            )

        assertThat(newHotseatItems).hasLength(2)
        assertThat(newHotseatItems[0]).isInstanceOf(TaskItemInfo::class.java)
        assertThat(newHotseatItems[1]).isNotInstanceOf(TaskItemInfo::class.java)
        val hotseatItem1 = newHotseatItems[0] as TaskItemInfo
        assertThat(hotseatItem1.taskId).isEqualTo(1)
    }

    @Test
    fun updateHotseatItemInfos_inDesktopMode_twoRunningTasksSamePackage_hotseatCoversFirstTask() {
        setInDesktopMode(true)

        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks =
                    listOf(
                        createTask(id = 1, HOTSEAT_PACKAGE_1),
                        createTask(id = 2, HOTSEAT_PACKAGE_1),
                    ),
                recentTaskPackages = emptyList(),
            )

        // First task is in Hotseat Items
        assertThat(newHotseatItems).hasLength(2)
        assertThat(newHotseatItems[0]).isInstanceOf(TaskItemInfo::class.java)
        assertThat(newHotseatItems[1]).isNotInstanceOf(TaskItemInfo::class.java)
        val hotseatItem1 = newHotseatItems[0] as TaskItemInfo
        assertThat(hotseatItem1.taskId).isEqualTo(1)
        // Second task is in shownTasks
        val shownTasks = recentAppsController.shownTasks.map { it.task1 }
        assertThat(shownTasks)
            .containsExactlyElementsIn(listOf(createTask(id = 2, HOTSEAT_PACKAGE_1)))
    }

    @Test
    fun updateHotseatItemInfos_canShowRecent_notInDesktopMode_returnsNonPredictedHotseatItems() {
        recentAppsController.canShowRecentApps = true
        setInDesktopMode(false)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        val expectedPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_cantShowRunning_inDesktopMode_shownTasks_returnsEmptyList() {
        recentAppsController.canShowRunningApps = false
        setInDesktopMode(true)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
            runningTasks =
                listOf(
                    createTask(id = 1, RUNNING_APP_PACKAGE_1),
                    createTask(id = 2, RUNNING_APP_PACKAGE_2),
                ),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_cantShowRecent_notInDesktopMode_shownTasks_returnsEmptyList() {
        recentAppsController.canShowRecentApps = false
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_noRecentTasks_shownTasks_returnsEmptyList() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks =
                listOf(
                    createTask(id = 1, RUNNING_APP_PACKAGE_1),
                    createTask(id = 2, RUNNING_APP_PACKAGE_2),
                ),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_noRunningApps_shownTasks_returnsEmptyList() {
        setInDesktopMode(true)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_shownTasks_returnsRunningTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        val shownTasks = recentAppsController.shownTasks.map { it.task1 }
        assertThat(shownTasks).containsExactlyElementsIn(listOf(task1, task2))
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_getRunningApps_returnsEmptySet() {
        setInDesktopMode(false)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).isEmpty()
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_getRunningApps_returnsAllDesktopTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_getRunningApps_includesHotseat() {
        setInDesktopMode(true)
        val runningTasks =
            listOf(
                createTask(id = 1, HOTSEAT_PACKAGE_1),
                createTask(id = 2, RUNNING_APP_PACKAGE_1),
                createTask(id = 3, RUNNING_APP_PACKAGE_2),
            )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = runningTasks,
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2, 3))
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_allAppsRunningAndInvisibleAppsMinimized() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3Minimized = createTask(id = 3, RUNNING_APP_PACKAGE_3, isVisible = false)
        val runningTasks = listOf(task1, task2, task3Minimized)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactly(1, 2, 3)
        assertThat(recentAppsController.minimizedTaskIds).containsExactly(3)
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_samePackage_differentTasks_severalRunningTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )

        val shownTasks = recentAppsController.shownTasks.map { it.task1 }
        assertThat(shownTasks).isEqualTo(listOf(task1, task2))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_multiInstance_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_1)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )

        val shownTasks = recentAppsController.shownTasks.map { it.task1 }
        assertThat(shownTasks).isEqualTo(listOf(task1, task2))
    }

    @Test
    fun updateHotseatItems_inDesktopMode_multiInstanceHotseatPackage_shownItems_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_1)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(RUNNING_APP_PACKAGE_1),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        updateRecentTasks( // Trigger a recent-tasks change before calling updateHotseatItems()
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(RUNNING_APP_PACKAGE_1),
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )

        val newHotseatItems = recentAppsController.shownHotseatItems
        assertThat(newHotseatItems).hasSize(1)
        assertThat(newHotseatItems[0]).isInstanceOf(TaskItemInfo::class.java)
        assertThat((newHotseatItems[0] as TaskItemInfo).taskId).isEqualTo(1)
        val shownTasks = recentAppsController.shownTasks.map { it.task1 }
        assertThat(shownTasks).isEqualTo(listOf(task2))
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3, RECENT_PACKAGE_1),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_1).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_addTask_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3 = createTask(id = 3, RUNNING_APP_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1, task3),
            recentTaskPackages = emptyList(),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        val expectedOrder =
            listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2, RUNNING_APP_PACKAGE_3)
        assertThat(shownPackages).isEqualTo(expectedOrder)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_addTask_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_3, RECENT_PACKAGE_2),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3, RECENT_PACKAGE_1),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_1).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_removeTask_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3 = createTask(id = 3, RUNNING_APP_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2, task3),
            recentTaskPackages = emptyList(),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        assertThat(shownPackages).isEqualTo(listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2))
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_removeTask_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_3).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2))
    }

    @Test
    fun onRecentTasksChanged_enterDesktopMode_shownTasks_onlyIncludesRunningTasks() {
        setInDesktopMode(false)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = recentTaskPackages,
        )

        setInDesktopMode(true)
        recentTasksChangedListener!!.onRecentTasksChanged()
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        assertThat(shownPackages).containsExactly(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2)
    }

    @Test
    fun onRecentTasksChanged_exitDesktopMode_shownTasks_onlyIncludesRecentTasks() {
        setInDesktopMode(true)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = recentTaskPackages,
        )
        setInDesktopMode(false)
        recentTasksChangedListener!!.onRecentTasksChanged()
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Don't expect RECENT_PACKAGE_3 because it is currently running.
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentTasks_shownTasks_returnsRecentTasks() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // RECENT_PACKAGE_3 is the top task (visible to user) so should be excluded.
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentAndRunningTasks_shownTasks_returnsRecentTaskAndDesktopTile() {
        setInDesktopMode(false)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val shownPackages = recentAppsController.shownTasks.map { it.packageNames }
        // Only 2 recent tasks shown: Desktop Tile + 1 Recent Task
        val desktopTilePackages = listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1)
        val expectedPackages = listOf(desktopTilePackages, recentTaskPackages)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentAndSplitTasks_shownTasks_returnsRecentTaskAndPair() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_SPLIT_PACKAGES_1, RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val shownPackages = recentAppsController.shownTasks.map { it.packageNames }
        // Only 2 recent tasks shown: Pair + 1 Recent Task
        val pairPackages = RECENT_SPLIT_PACKAGES_1.split("_")
        val recentTaskPackages = listOf(RECENT_PACKAGE_1)
        val expectedPackages = listOf(pairPackages, recentTaskPackages)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_noActualChangeToRecents_commitRunningAppsToUI_notCalled() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
        // Call onRecentTasksChanged() again with the same tasks, verify it's a no-op.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_noActualChangeToRunning_commitRunningAppsToUI_notCalled() {
        setInDesktopMode(true)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
        // Call onRecentTasksChanged() again with the same tasks, verify it's a no-op.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_onlyMinimizedChanges_commitRunningAppsToUI_isCalled() {
        setInDesktopMode(true)
        val task1Minimized = createTask(id = 1, RUNNING_APP_PACKAGE_1, isVisible = false)
        val task2Visible = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task2Minimized = createTask(id = 2, RUNNING_APP_PACKAGE_2, isVisible = false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1Minimized, task2Visible),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()

        // Call onRecentTasksChanged() again with a new minimized app, verify we update UI.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1Minimized, task2Minimized),
            recentTaskPackages = emptyList(),
        )

        verify(taskbarViewController, times(2)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_hotseatAppStartsRunning_commitRunningAppsToUI_isCalled() {
        setInDesktopMode(true)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        val originalTasks = listOf(createTask(id = 1, RUNNING_APP_PACKAGE_1))
        val newTasks =
            listOf(createTask(id = 1, RUNNING_APP_PACKAGE_1), createTask(id = 2, HOTSEAT_PACKAGE_1))
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = hotseatPackages,
            runningTasks = originalTasks,
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()

        // Call onRecentTasksChanged() again with a new running app, verify we update UI.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = hotseatPackages,
            runningTasks = newTasks,
            recentTaskPackages = emptyList(),
        )

        verify(taskbarViewController, times(2)).commitRunningAppsToUI()
    }

    private fun prepareHotseatAndRunningAndRecentApps(
        hotseatPackages: List<String>,
        runningTasks: List<Task>,
        recentTaskPackages: List<String>,
    ): Array<ItemInfo?> {
        val hotseatItems = createHotseatItemsFromPackageNames(hotseatPackages)
        recentAppsController.updateHotseatItemInfos(hotseatItems.toTypedArray())
        updateRecentTasks(runningTasks, recentTaskPackages)
        return recentAppsController.shownHotseatItems.toTypedArray()
    }

    private fun updateRecentTasks(runningTasks: List<Task>, recentTaskPackages: List<String>) {
        val recentTasks = createRecentTasksFromPackageNames(recentTaskPackages)
        val allTasks =
            ArrayList<GroupTask>().apply {
                if (!runningTasks.isEmpty()) {
                    add(DesktopTask(ArrayList(runningTasks)))
                }
                addAll(recentTasks)
            }
        doAnswer {
                val callback: Consumer<ArrayList<GroupTask>> = it.getArgument(0)
                callback.accept(allTasks)
                taskListChangeId
            }
            .whenever(mockRecentsModel)
            .getTasks(any<Consumer<List<GroupTask>>>())
        recentTasksChangedListener?.onRecentTasksChanged()
    }

    private fun createHotseatItemsFromPackageNames(packageNames: List<String>): List<ItemInfo> {
        return packageNames
            .map {
                createTestAppInfo(packageName = it).apply {
                    container =
                        if (it.startsWith("predicted")) {
                            CONTAINER_HOTSEAT_PREDICTION
                        } else {
                            CONTAINER_HOTSEAT
                        }
                }
            }
            .map { it.makeWorkspaceItem(taskbarActivityContext) }
    }

    private fun createTestAppInfo(
        packageName: String = "testPackageName",
        className: String = "testClassName",
    ) = AppInfo(ComponentName(packageName, className), className /* title */, userHandle, Intent())

    private fun createRecentTasksFromPackageNames(packageNames: List<String>): List<GroupTask> {
        return packageNames.map { packageName ->
            if (packageName.startsWith("split")) {
                val splitPackages = packageName.split("_")
                GroupTask(
                    createTask(100, splitPackages[0]),
                    createTask(101, splitPackages[1]),
                    /* splitBounds = */ null,
                )
            } else {
                // Use the number at the end of the test packageName as the id.
                val id = 1000 + packageName[packageName.length - 1].code
                GroupTask(createTask(id, packageName))
            }
        }
    }

    private fun createTask(id: Int, packageName: String, isVisible: Boolean = true): Task {
        return Task(
                Task.TaskKey(
                    id,
                    WINDOWING_MODE_FREEFORM,
                    Intent().apply { `package` = packageName },
                    ComponentName(packageName, "TestActivity"),
                    userHandle.identifier,
                    0,
                )
            )
            .apply { this.isVisible = isVisible }
    }

    private fun setInDesktopMode(inDesktopMode: Boolean) {
        whenever(taskbarControllers.taskbarDesktopModeController.areDesktopTasksVisible)
            .thenReturn(inDesktopMode)
    }

    private val GroupTask.packageNames: List<String>
        get() = tasks.map { task -> task.key.packageName }

    private companion object {
        const val HOTSEAT_PACKAGE_1 = "hotseat1"
        const val HOTSEAT_PACKAGE_2 = "hotseat2"
        const val PREDICTED_PACKAGE_1 = "predicted1"
        const val RUNNING_APP_PACKAGE_1 = "running1"
        const val RUNNING_APP_PACKAGE_2 = "running2"
        const val RUNNING_APP_PACKAGE_3 = "running3"
        const val RECENT_PACKAGE_1 = "recent1"
        const val RECENT_PACKAGE_2 = "recent2"
        const val RECENT_PACKAGE_3 = "recent3"
        const val RECENT_SPLIT_PACKAGES_1 = "split1_split2"
    }
}
