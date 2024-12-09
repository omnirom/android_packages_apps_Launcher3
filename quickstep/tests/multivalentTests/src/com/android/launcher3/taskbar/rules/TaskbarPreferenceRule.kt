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

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.ConstantItem
import com.android.launcher3.LauncherPrefs
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Rule for modifying a Taskbar preference.
 *
 * The original preference value is restored on teardown.
 */
class TaskbarPreferenceRule<T : Any>(
    context: TaskbarWindowSandboxContext,
    private val constantItem: ConstantItem<T>
) : TestRule {

    private val prefs = LauncherPrefs.get(context)

    var value: T
        get() = prefs.get(constantItem)
        set(value) = getInstrumentation().runOnMainSync { prefs.put(constantItem, value) }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val originalValue = value
                try {
                    base.evaluate()
                } finally {
                    value = originalValue
                }
            }
        }
    }
}
