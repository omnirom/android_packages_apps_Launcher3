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
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarPreferenceRuleTest {

    private val context = TaskbarWindowSandboxContext.create(getInstrumentation().targetContext)
    private val preferenceRule = TaskbarPreferenceRule(context, TASKBAR_PINNING)

    @Test
    fun testSetup_toggleBoolean_updatesPreferences() {
        val originalValue = preferenceRule.value
        onSetup {
            preferenceRule.value = !preferenceRule.value
            assertThat(preferenceRule.value).isNotEqualTo(originalValue)
        }
    }

    @Test
    fun testTeardown_afterTogglingBoolean_preferenceReset() {
        val originalValue = preferenceRule.value
        onSetup { preferenceRule.value = !preferenceRule.value }
        assertThat(preferenceRule.value).isEqualTo(originalValue)
    }

    private fun onSetup(runTest: () -> Unit) {
        preferenceRule
            .apply(
                object : Statement() {
                    override fun evaluate() = runTest()
                },
                DESCRIPTION,
            )
            .evaluate()
    }

    private companion object {
        private val DESCRIPTION =
            Description.createSuiteDescription(TaskbarPreferenceRule::class.java)
    }
}
