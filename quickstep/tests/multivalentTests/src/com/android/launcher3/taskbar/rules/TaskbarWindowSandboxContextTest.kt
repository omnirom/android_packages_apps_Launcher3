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

import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@LauncherMultivalentJUnit.EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarWindowSandboxContextTest {

    private val context = TaskbarWindowSandboxContext.create(getInstrumentation().targetContext)

    @Test
    fun testCreateWindowContext_applicationContextSandboxed() {
        val windowContext = context.createWindowContext(TYPE_APPLICATION_OVERLAY, null)
        assertThat(windowContext.applicationContext).isInstanceOf(SandboxContext::class.java)
    }

    @Test
    fun testCreateWindowContext_nested_applicationContextSandboxed() {
        val windowContext = context.createWindowContext(TYPE_APPLICATION_OVERLAY, null)
        val nestedContext = windowContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null)
        assertThat(nestedContext.applicationContext).isInstanceOf(SandboxContext::class.java)
    }
}
