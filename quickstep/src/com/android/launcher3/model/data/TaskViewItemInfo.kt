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

package com.android.launcher3.model.data

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import com.android.launcher3.Flags.privateSpaceRestrictAccessibilityDrag
import com.android.launcher3.LauncherSettings
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.pm.UserCache
import com.android.quickstep.TaskUtils
import com.android.quickstep.views.TaskContainer

class TaskViewItemInfo(taskContainer: TaskContainer) : WorkspaceItemInfo() {
    @VisibleForTesting(otherwise = PRIVATE) val taskViewAtom: LauncherAtom.TaskView

    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_TASK
        container = LauncherSettings.Favorites.CONTAINER_TASKSWITCHER
        val componentKey = TaskUtils.getLaunchComponentKeyForTask(taskContainer.task.key)
        user = componentKey.user
        intent = Intent().setComponent(componentKey.componentName)
        title = taskContainer.task.title
        if (privateSpaceRestrictAccessibilityDrag()) {
            if (
                UserCache.getInstance(taskContainer.taskView.context)
                    .getUserInfo(componentKey.user)
                    .isPrivate
            ) {
                runtimeStatusFlags = runtimeStatusFlags or ItemInfoWithIcon.FLAG_NOT_PINNABLE
            }
        }

        taskViewAtom =
            createTaskViewAtom(
                type = taskContainer.taskView.type.ordinal,
                index =
                    taskContainer.taskView.recentsView?.indexOfChild(taskContainer.taskView) ?: -1,
                componentName = componentKey.componentName.flattenToShortString(),
                cardinality = taskContainer.taskView.taskContainers.size,
            )
    }

    override fun buildProto(cInfo: CollectionInfo?, context: Context): LauncherAtom.ItemInfo =
        super.buildProto(cInfo, context).toBuilder().setTaskView(taskViewAtom).build()

    companion object {
        @VisibleForTesting(otherwise = PRIVATE)
        fun createTaskViewAtom(
            type: Int,
            index: Int,
            componentName: String,
            cardinality: Int,
        ): LauncherAtom.TaskView =
            LauncherAtom.TaskView.newBuilder()
                .apply {
                    this.type = type
                    this.index = index
                    this.componentName = componentName
                    this.cardinality = cardinality
                }
                .build()
    }
}
