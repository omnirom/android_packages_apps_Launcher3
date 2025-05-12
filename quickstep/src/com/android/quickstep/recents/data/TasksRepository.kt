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

import android.graphics.drawable.Drawable
import android.util.Log
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate.TaskIconChangedCallback
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate.TaskThumbnailChangedCallback
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class TasksRepository(
    private val recentsModel: RecentTasksDataSource,
    private val taskThumbnailDataSource: TaskThumbnailDataSource,
    private val taskIconDataSource: TaskIconDataSource,
    private val taskVisualsChangedDelegate: TaskVisualsChangedDelegate,
    private val recentsCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : RecentTasksRepository {
    private val tasks = MutableStateFlow(MapForStateFlow<Int, Task>(emptyMap()))
    private val taskRequests = HashMap<Int, Pair<Task.TaskKey, Job>>()

    override fun getAllTaskData(forceRefresh: Boolean): Flow<List<Task>> {
        if (forceRefresh) {
            recentsModel.getTasks { result ->
                tasks.value =
                    MapForStateFlow(
                        result
                            .flatMap { groupTask -> groupTask.tasks }
                            .associateBy { it.key.id }
                            .also {
                                // Clean tasks that are not in the latest group tasks list.
                                val tasksNoLongerVisible = it.keys.subtract(tasks.value.keys)
                                removeTasks(tasksNoLongerVisible)
                            }
                    )
            }
        }
        return tasks.map { it.values.toList() }
    }

    override fun getTaskDataById(taskId: Int) = tasks.map { it[taskId] }

    override fun getThumbnailById(taskId: Int) =
        getTaskDataById(taskId).map { it?.thumbnail }.distinctUntilChangedBy { it?.snapshotId }

    override fun setVisibleTasks(visibleTaskIdList: Set<Int>) {
        val tasksNoLongerVisible = taskRequests.keys.subtract(visibleTaskIdList)
        val newlyVisibleTasks = visibleTaskIdList.subtract(taskRequests.keys)
        if (tasksNoLongerVisible.isNotEmpty() || newlyVisibleTasks.isNotEmpty()) {
            Log.d(
                TAG,
                "setVisibleTasks to: $visibleTaskIdList, " +
                    "removed: $tasksNoLongerVisible, added: $newlyVisibleTasks",
            )
        }

        // Remove tasks are no longer visible
        removeTasks(tasksNoLongerVisible)
        // Add new tasks to be requested
        newlyVisibleTasks.forEach { taskId -> requestTaskData(taskId) }
    }

    private fun requestTaskData(taskId: Int) {
        val task = tasks.value[taskId] ?: return
        taskRequests[taskId] =
            Pair(
                task.key,
                recentsCoroutineScope.launch {
                    Log.i(TAG, "requestTaskData: $taskId")
                    fetchIcon(task)
                    fetchThumbnail(task)
                },
            )
    }

    private fun removeTasks(tasksToRemove: Set<Int>) {
        if (tasksToRemove.isEmpty()) return

        Log.i(TAG, "removeTasks: $tasksToRemove")
        tasksToRemove.forEach { taskId ->
            val request = taskRequests.remove(taskId) ?: return
            val (taskKey, job) = request
            job.cancel()

            // un-registering callbacks
            taskVisualsChangedDelegate.unregisterTaskIconChangedCallback(taskKey)
            taskVisualsChangedDelegate.unregisterTaskThumbnailChangedCallback(taskKey)

            // Clearing Task to reduce memory footprint
            tasks.value[taskId]?.apply {
                thumbnail = null
                icon = null
                title = null
                titleDescription = null
            }
        }
        tasks.update { oldValue -> MapForStateFlow(oldValue) }
    }

    private suspend fun fetchIcon(task: Task) {
        updateIcon(task.key.id, getIconFromDataSource(task)) // Fetch icon from cache
        taskVisualsChangedDelegate.registerTaskIconChangedCallback(
            task.key,
            object : TaskIconChangedCallback {
                override fun onTaskIconChanged() {
                    recentsCoroutineScope.launch {
                        updateIcon(task.key.id, getIconFromDataSource(task))
                    }
                }
            },
        )
    }

    private suspend fun fetchThumbnail(task: Task) {
        updateThumbnail(task.key.id, getThumbnailFromDataSource(task))
        taskVisualsChangedDelegate.registerTaskThumbnailChangedCallback(
            task.key,
            object : TaskThumbnailChangedCallback {
                override fun onTaskThumbnailChanged(thumbnailData: ThumbnailData?) {
                    updateThumbnail(task.key.id, thumbnailData)
                }

                override fun onHighResLoadingStateChanged() {
                    recentsCoroutineScope.launch {
                        updateThumbnail(task.key.id, getThumbnailFromDataSource(task))
                    }
                }
            },
        )
    }

    private fun updateIcon(taskId: Int, iconData: IconData) {
        val task = tasks.value[taskId] ?: return
        task.icon = iconData.icon
        task.titleDescription = iconData.contentDescription
        task.title = iconData.title
        tasks.update { oldValue -> MapForStateFlow(oldValue + (taskId to task)) }
    }

    private fun updateThumbnail(taskId: Int, thumbnail: ThumbnailData?) {
        val task = tasks.value[taskId] ?: return
        task.thumbnail = thumbnail
        tasks.update { oldValue -> MapForStateFlow(oldValue + (taskId to task)) }
    }

    private suspend fun getThumbnailFromDataSource(task: Task) =
        withContext(dispatcherProvider.main) {
            suspendCancellableCoroutine { continuation ->
                val cancellableTask =
                    taskThumbnailDataSource.getThumbnailInBackground(task) {
                        continuation.resume(it)
                    }
                continuation.invokeOnCancellation { cancellableTask?.cancel() }
            }
        }

    private suspend fun getIconFromDataSource(task: Task) =
        withContext(dispatcherProvider.main) {
            suspendCancellableCoroutine { continuation ->
                val cancellableTask =
                    taskIconDataSource.getIconInBackground(task) { icon, contentDescription, title
                        ->
                        icon.constantState?.let {
                            continuation.resume(
                                IconData(it.newDrawable().mutate(), contentDescription, title)
                            )
                        }
                    }
                continuation.invokeOnCancellation { cancellableTask?.cancel() }
            }
        }

    companion object {
        private const val TAG = "TasksRepository"
    }

    /** Helper class to support StateFlow emissions when using a Map with a MutableStateFlow. */
    private data class MapForStateFlow<K, T>(
        private val backingMap: Map<K, T>,
        private val updated: Long = System.nanoTime(),
    ) : Map<K, T> by backingMap

    private data class IconData(
        val icon: Drawable,
        val contentDescription: String,
        val title: String,
    )
}
