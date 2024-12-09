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

import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import com.android.launcher3.Flags.FLAG_ENABLE_TASKBAR_PINNING
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING_IN_DESKTOP_MODE
import com.android.launcher3.util.DisplayController
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Rule that allows modifying the Taskbar pinned preferences.
 *
 * The original preference values are restored on teardown.
 *
 * If this rule is being used with [TaskbarUnitTestRule], make sure this rule is applied first.
 *
 * This rule is overkill if a test does not need to change the mode during Taskbar's lifecycle. If
 * the mode is static, use [TaskbarModeRule] instead, which forces the mode. A test can class can
 * declare both this rule and [TaskbarModeRule] but using both for a test method is unsupported.
 */
class TaskbarPinningPreferenceRule(context: TaskbarWindowSandboxContext) : TestRule {

    private val setFlagsRule =
        SetFlagsRule(FlagsParameterization(mapOf(FLAG_ENABLE_TASKBAR_PINNING to true)))
    private val pinningRule = TaskbarPreferenceRule(context, TASKBAR_PINNING)
    private val desktopPinningRule = TaskbarPreferenceRule(context, TASKBAR_PINNING_IN_DESKTOP_MODE)
    private val ruleChain =
        RuleChain.outerRule(setFlagsRule).around(pinningRule).around(desktopPinningRule)

    var isPinned by pinningRule::value
    var isPinnedInDesktopMode by desktopPinningRule::value

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                DisplayController.enableTaskbarModePreferenceForTests(true)
                try {
                    ruleChain.apply(base, description).evaluate()
                } finally {
                    DisplayController.enableTaskbarModePreferenceForTests(false)
                }
            }
        }
    }
}
