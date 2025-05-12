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

package com.android.launcher3.taskbar.rules

import com.android.quickstep.RecentsModel
import com.android.quickstep.RecentsModel.RecentTasksChangedListener
import com.android.quickstep.TaskIconCache
import com.android.quickstep.util.GroupTask
import java.util.function.Consumer
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MockedRecentsModelTestRule(private val context: TaskbarWindowSandboxContext) : TestRule {

    private val mockIconCache: TaskIconCache = mock()

    private val mockRecentsModel: RecentsModel = mock {
        on { iconCache } doReturn mockIconCache

        on { unregisterRecentTasksChangedListener() } doAnswer { recentTasksChangedListener = null }

        on { registerRecentTasksChangedListener(any<RecentTasksChangedListener>()) } doAnswer
            {
                recentTasksChangedListener = it.getArgument<RecentTasksChangedListener>(0)
            }

        on { getTasks(anyOrNull(), anyOrNull()) } doAnswer
            {
                val request = it.getArgument<Consumer<List<GroupTask>>?>(0)
                if (request != null) {
                    taskRequests.add { response -> request.accept(response) }
                }
                taskListId
            }

        on { getTasks(anyOrNull()) } doAnswer
            {
                val request = it.getArgument<Consumer<List<GroupTask>>?>(0)
                if (request != null) {
                    taskRequests.add { response -> request.accept(response) }
                }
                taskListId
            }

        on { isTaskListValid(any()) } doAnswer { taskListId == it.getArgument(0) }
    }

    private var recentTasks: List<GroupTask> = emptyList()
    private var taskListId = 0
    private var recentTasksChangedListener: RecentTasksChangedListener? = null
    private var taskRequests: MutableList<(List<GroupTask>) -> Unit> = mutableListOf()

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                context.putObject(RecentsModel.INSTANCE, mockRecentsModel)
                base?.evaluate()
            }
        }
    }

    // NOTE: For the update to take effect, `resolvePendingTaskRequests()` needs to be called, so
    // calbacks to any pending `RecentsModel.getTasks()` get called with the updated task list.
    fun updateRecentTasks(tasks: List<GroupTask>) {
        ++taskListId
        recentTasks = tasks
        recentTasksChangedListener?.onRecentTasksChanged()
    }

    fun resolvePendingTaskRequests() {
        val requests = mutableListOf<(List<GroupTask>) -> Unit>()
        requests.addAll(taskRequests)
        taskRequests.clear()

        requests.forEach { it(recentTasks) }
    }
}
