/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.quickstep.views.TaskViewType;
import com.android.systemui.shared.recents.model.Task;

import java.util.List;
import java.util.Objects;

/**
 * A {@link Task} container that can contain N number of tasks that are part of the desktop in
 * recent tasks list.
 */
public class DesktopTask extends GroupTask {

    @NonNull
    public final List<Task> tasks;

    public DesktopTask(@NonNull List<Task> tasks) {
        super(tasks.get(0), null, null, TaskViewType.DESKTOP);
        this.tasks = tasks;
    }

    @Override
    public boolean containsTask(int taskId) {
        for (Task task : tasks) {
            if (task.key.id == taskId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasMultipleTasks() {
        return tasks.size() > 1;
    }

    @Override
    public boolean supportsMultipleTasks() {
        return true;
    }

    @Override
    @NonNull
    public List<Task> getTasks() {
        return tasks;
    }

    @Override
    public DesktopTask copy() {
        return new DesktopTask(tasks);
    }

    @Override
    public String toString() {
        return "type=" + taskViewType + " tasks=" + tasks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DesktopTask that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(tasks, that.tasks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tasks);
    }
}
