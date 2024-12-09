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

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.Display
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext

/**
 * Sandbox wrapper where [createWindowContext] provides contexts that are still sandboxed within
 * [application].
 *
 * Taskbar can create window contexts, which need to operate under the same sandbox application, but
 * [Context.getApplicationContext] by default returns the actual application. For this reason,
 * [SandboxContext] overrides [getApplicationContext] to return itself, which prevents leaving the
 * sandbox. [SandboxContext] and the real application have different sets of
 * [MainThreadInitializedObject] instances, so overriding the application prevents the latter set
 * from leaking into the sandbox. Similarly, this implementation overrides [getApplicationContext]
 * to return the original sandboxed [application], and it wraps created windowed contexts to
 * propagate this [application].
 */
class TaskbarWindowSandboxContext
private constructor(private val application: SandboxContext, base: Context) : ContextWrapper(base) {

    override fun createWindowContext(type: Int, options: Bundle?): Context {
        return TaskbarWindowSandboxContext(application, super.createWindowContext(type, options))
    }

    override fun createWindowContext(display: Display, type: Int, options: Bundle?): Context {
        return TaskbarWindowSandboxContext(
            application,
            super.createWindowContext(display, type, options),
        )
    }

    override fun getApplicationContext(): SandboxContext = application

    companion object {
        /** Creates a [TaskbarWindowSandboxContext] to sandbox [base] for Taskbar tests. */
        fun create(base: Context): TaskbarWindowSandboxContext {
            return SandboxContext(base).let { TaskbarWindowSandboxContext(it, it) }
        }
    }
}
