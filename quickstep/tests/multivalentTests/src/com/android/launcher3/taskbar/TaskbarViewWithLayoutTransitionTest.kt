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

package com.android.launcher3.taskbar

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.launcher3.Flags.FLAG_TASKBAR_RECENTS_LAYOUT_TRANSITION
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarIconType.ALL_APPS
import com.android.launcher3.taskbar.TaskbarIconType.DIVIDER
import com.android.launcher3.taskbar.TaskbarIconType.HOTSEAT
import com.android.launcher3.taskbar.TaskbarIconType.RECENT
import com.android.launcher3.taskbar.TaskbarViewTestUtil.assertThat
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createRecents
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.ForceRtl
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
@EnableFlags(FLAG_TASKBAR_RECENTS_LAYOUT_TRANSITION)
class TaskbarViewWithLayoutTransitionTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    private lateinit var taskbarView: TaskbarView

    @Before
    fun obtainView() {
        taskbarView = taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_hotseatItems_hasDividerBetweenHotseatAndAllApps() {
        runOnMainSync { taskbarView.updateItems(createHotseatItems(2), emptyList()) }
        assertThat(taskbarView).hasIconTypes(*HOTSEAT * 2, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_recentsItems_hasDividerBetweenRecentsAndAllApps() {
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(4)) }
        assertThat(taskbarView).hasIconTypes(*RECENT * 4, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_recentsItems_recentsAreReversed() {
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(4)) }
        assertThat(taskbarView).hasRecentsOrder(startIndex = 0, expectedIds = listOf(3, 2, 1, 0))
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_hotseatItemsAndRecents_hasDividerBetweenRecentsAndHotseat() {
        runOnMainSync { taskbarView.updateItems(createHotseatItems(3), createRecents(2)) }
        assertThat(taskbarView).hasIconTypes(*RECENT * 2, DIVIDER, *HOTSEAT * 3, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addHotseatItemWithoutRecents_updatesHotseat() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList())
            taskbarView.updateItems(createHotseatItems(2), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(*HOTSEAT * 2, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addHotseatItemWithRecents_updatesHotseat() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
            taskbarView.updateItems(createHotseatItems(2), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, *HOTSEAT * 2, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeHotseatItem_updatesHotseat() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(2), createRecents(1))
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addRecentsItem_updatesRecents() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
            taskbarView.updateItems(createHotseatItems(1), createRecents(2))
        }
        assertThat(taskbarView).hasIconTypes(*RECENT * 2, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeRecentsItem_updatesRecents() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(2))
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, HOTSEAT, ALL_APPS)
    }
}
