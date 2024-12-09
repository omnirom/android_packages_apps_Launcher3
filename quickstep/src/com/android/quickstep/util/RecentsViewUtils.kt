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

package com.android.quickstep.util

import com.android.launcher3.Flags.enableLargeDesktopWindowingTile
import com.android.quickstep.RecentsAnimationController
import com.android.quickstep.views.DesktopTaskView
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.ThumbnailData

/**
 * Helper class for [com.android.quickstep.views.RecentsView]. This util class contains refactored
 * and extracted functions from RecentsView to facilitate the implementation of unit tests.
 */
class RecentsViewUtils {
    /** Takes a screenshot of all [taskView] and return map of taskId to the screenshot */
    fun screenshotTasks(
        taskView: TaskView,
        recentsAnimationController: RecentsAnimationController,
    ): Map<Int, ThumbnailData> =
        taskView.taskContainers.associate {
            it.task.key.id to recentsAnimationController.screenshotTask(it.task.key.id)
        }

    /**
     * Sorts task groups to move desktop tasks to the end of the list.
     *
     * @param tasks List of group tasks to be sorted.
     * @return Sorted list of GroupTasks to be used in the RecentsView.
     */
    fun sortDesktopTasksToFront(tasks: List<GroupTask>): List<GroupTask> {
        val (desktopTasks, otherTasks) = tasks.partition { it.taskViewType == TaskViewType.DESKTOP }
        return otherTasks + desktopTasks
    }

    /** Returns the expected index of the focus task. */
    fun getFocusedTaskIndex(taskGroups: List<GroupTask>): Int {
        // The focused task index is placed after the desktop tasks views.
        return if (enableLargeDesktopWindowingTile()) {
            taskGroups.count { it.taskViewType == TaskViewType.DESKTOP }
        } else {
            0
        }
    }

    /** Counts [TaskView]s that are [DesktopTaskView] instances. */
    fun getDesktopTaskViewCount(taskViews: Iterable<TaskView>): Int =
        taskViews.count { it is DesktopTaskView }

    /** Returns a list of all large TaskView Ids from [TaskView]s */
    fun getLargeTaskViewIds(taskViews: Iterable<TaskView>): List<Int> =
        taskViews.filter { it.isLargeTile }.map { it.taskViewId }

    /**
     * Returns the first TaskView that should be displayed as a large tile.
     *
     * @param taskViews List of [TaskView]s
     */
    fun getFirstLargeTaskView(taskViews: Iterable<TaskView>): TaskView? =
        taskViews.firstOrNull { it.isLargeTile }

    /** Returns the last TaskView that should be displayed as a large tile. */
    fun getLastLargeTaskView(taskViews: Iterable<TaskView>): TaskView? =
        taskViews.lastOrNull { it.isLargeTile }

    /** Returns the first [TaskView], with some tasks possibly hidden in the carousel. */
    fun getFirstTaskViewInCarousel(
        nonRunningTaskCategoryHidden: Boolean,
        taskViews: Iterable<TaskView>,
        runningTaskView: TaskView?,
    ): TaskView? =
        taskViews.firstOrNull {
            it.isVisibleInCarousel(runningTaskView, nonRunningTaskCategoryHidden)
        }

    /** Returns the last [TaskView], with some tasks possibly hidden in the carousel. */
    fun getLastTaskViewInCarousel(
        nonRunningTaskCategoryHidden: Boolean,
        taskViews: Iterable<TaskView>,
        runningTaskView: TaskView?,
    ): TaskView? =
        taskViews.lastOrNull {
            it.isVisibleInCarousel(runningTaskView, nonRunningTaskCategoryHidden)
        }

    /** Returns the current list of [TaskView] children. */
    fun getTaskViews(taskViewCount: Int, requireTaskViewAt: (Int) -> TaskView): Iterable<TaskView> =
        (0 until taskViewCount).map(requireTaskViewAt)

    /** Apply attachAlpha to all [TaskView] accordingly to different conditions. */
    fun applyAttachAlpha(
        taskViews: Iterable<TaskView>,
        runningTaskView: TaskView?,
        runningTaskTileHidden: Boolean,
        nonRunningTaskCategoryHidden: Boolean,
    ) {
        taskViews.forEach { taskView ->
            val isVisible =
                if (taskView == runningTaskView) !runningTaskTileHidden
                else taskView.isVisibleInCarousel(runningTaskView, nonRunningTaskCategoryHidden)
            taskView.attachAlpha = if (isVisible) 1f else 0f
        }
    }

    private fun TaskView.isVisibleInCarousel(
        runningTaskView: TaskView?,
        nonRunningTaskCategoryHidden: Boolean,
    ): Boolean =
        if (!nonRunningTaskCategoryHidden) true
        else if (runningTaskView == null) true else getCategory() == runningTaskView.getCategory()

    private fun TaskView.getCategory(): TaskViewCategory =
        if (this is DesktopTaskView) TaskViewCategory.DESKTOP else TaskViewCategory.FULL_SCREEN

    private enum class TaskViewCategory {
        FULL_SCREEN,
        DESKTOP,
    }
}
