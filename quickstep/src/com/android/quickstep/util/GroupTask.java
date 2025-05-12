/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.quickstep.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.quickstep.views.TaskViewType;
import com.android.systemui.shared.recents.model.Task;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Task} container that can contain one or two tasks, depending on if the two tasks
 * are represented as an app-pair in the recents task list.
 */
public class GroupTask {
    @NonNull
    public final Task task1;
    @Nullable
    public final Task task2;
    @Nullable
    public final SplitBounds mSplitBounds;
    public final TaskViewType taskViewType;

    public GroupTask(@NonNull Task task) {
        this(task, null, null);
    }

    public GroupTask(@NonNull Task t1, @Nullable Task t2, @Nullable SplitBounds splitBounds) {
        this(t1, t2, splitBounds, t2 != null ? TaskViewType.GROUPED : TaskViewType.SINGLE);
    }

    protected GroupTask(@NonNull Task t1, @Nullable Task t2, @Nullable SplitBounds splitBounds,
            TaskViewType taskViewType) {
        task1 = t1;
        task2 = t2;
        mSplitBounds = splitBounds;
        this.taskViewType = taskViewType;
    }

    public boolean containsTask(int taskId) {
        return task1.key.id == taskId || (task2 != null && task2.key.id == taskId);
    }

    public boolean hasMultipleTasks() {
        return task2 != null;
    }

    /**
     * Returns whether this task supports multiple tasks or not.
     */
    public boolean supportsMultipleTasks() {
        return taskViewType == TaskViewType.GROUPED;
    }

    /**
     * Returns a List of all the Tasks in this GroupTask
     */
    public List<Task> getTasks() {
        if (task2 == null) {
            return Collections.singletonList(task1);
        } else {
            return Arrays.asList(task1, task2);
        }
    }

    /**
     * Create a copy of this instance
     */
    public GroupTask copy() {
        return new GroupTask(
                new Task(task1),
                task2 != null ? new Task(task2) : null,
                mSplitBounds);
    }

    @Override
    public String toString() {
        return "type=" + taskViewType + " task1=" + task1 + " task2=" + task2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupTask that)) return false;
        return taskViewType == that.taskViewType && Objects.equals(task1,
                that.task1) && Objects.equals(task2, that.task2)
                && Objects.equals(mSplitBounds, that.mSplitBounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task1, task2, mSplitBounds, taskViewType);
    }
}
