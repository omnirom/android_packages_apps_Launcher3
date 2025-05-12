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

import android.animation.AnimatorTestRule
import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.launcher3.Flags.FLAG_TASKBAR_OVERFLOW
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.taskbar.rules.MockedRecentsModelTestRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.TestUtil.getOnUiThread
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.util.DesktopTask
import com.android.systemui.shared.recents.model.Task
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.android.wm.shell.desktopmode.IDesktopTaskListener
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelTablet2023"])
@EnableFlags(
    FLAG_TASKBAR_OVERFLOW,
    FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,
    FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    FLAG_ENABLE_BUBBLE_BAR,
)
class TaskbarOverflowTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1)
    val context =
        TaskbarWindowSandboxContext.create { builder ->
            builder.bindSystemUiProxy(
                object : SystemUiProxy(this) {
                    override fun setDesktopTaskListener(listener: IDesktopTaskListener?) {
                        desktopTaskListener = listener
                    }
                }
            )
        }

    @get:Rule(order = 2) val recentsModel = MockedRecentsModelTestRule(context)

    @get:Rule(order = 3) val taskbarModeRule = TaskbarModeRule(context)

    @get:Rule(order = 4) val animatorTestRule = AnimatorTestRule(this)

    @get:Rule(order = 5) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var taskbarViewController: TaskbarViewController
    @InjectController lateinit var recentAppsController: TaskbarRecentAppsController
    @InjectController lateinit var bubbleBarViewController: BubbleBarViewController
    @InjectController lateinit var bubbleStashController: BubbleStashController

    private var desktopTaskListener: IDesktopTaskListener? = null

    @Before
    fun ensureRunningAppsShowing() {
        runOnMainSync {
            if (!recentAppsController.canShowRunningApps) {
                recentAppsController.onDestroy()
                recentAppsController.canShowRunningApps = true
                recentAppsController.init(taskbarUnitTestRule.activityContext.controllers)
            }
            recentsModel.resolvePendingTaskRequests()
        }
    }

    @Test
    @TaskbarMode(PINNED)
    fun testTaskbarWithMaxNumIcons_pinned() {
        addRunningAppsAndVerifyOverflowState(0)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testTaskbarWithMaxNumIcons_transient() {
        addRunningAppsAndVerifyOverflowState(0)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOverflownTaskbar_pinned() {
        addRunningAppsAndVerifyOverflowState(5)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testOverflownTaskbar_transient() {
        addRunningAppsAndVerifyOverflowState(5)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testBubbleBarReducesTaskbarMaxNumIcons_pinned() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)

        runOnMainSync { bubbleBarViewController.setHiddenForBubbles(false) }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testBubbleBarReducesTaskbarMaxNumIcons_transient() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)

        runOnMainSync { bubbleBarViewController.setHiddenForBubbles(false) }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin)
            .isAtLeast(
                navButtonEndSpacing +
                    bubbleBarViewController.collapsedWidthWithMaxVisibleBubbles.toInt()
            )
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testBubbleBarReducesTaskbarMaxNumIcons_transientBubbleInitiallyStashed() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)
        runOnMainSync {
            bubbleStashController.stashBubbleBarImmediate()
            bubbleBarViewController.setHiddenForBubbles(false)
        }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin)
            .isAtLeast(
                navButtonEndSpacing +
                    bubbleBarViewController.collapsedWidthWithMaxVisibleBubbles.toInt()
            )
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testStashingBubbleBarMaintainsMaxNumIcons_transient() {
        runOnMainSync { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)

        runOnMainSync { bubbleStashController.stashBubbleBarImmediate() }
        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))
    }

    @Test
    @TaskbarMode(PINNED)
    fun testHidingBubbleBarIncreasesMaxNumIcons_pinned() {
        runOnMainSync { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val initialMaxNumIconViews = addRunningAppsAndVerifyOverflowState(5)

        runOnMainSync {
            bubbleBarViewController.setHiddenForBubbles(true)
            animatorTestRule.advanceTimeBy(150)
        }

        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(initialMaxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))

        assertThat(taskbarIconsCentered).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testHidingBubbleBarIncreasesMaxNumIcons_transient() {
        runOnMainSync { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val initialMaxNumIconViews = addRunningAppsAndVerifyOverflowState(5)

        runOnMainSync {
            bubbleBarViewController.setHiddenForBubbles(true)
            animatorTestRule.advanceTimeBy(150)
        }

        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(initialMaxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))

        assertThat(taskbarIconsCentered).isTrue()
    }

    private fun createDesktopTask(tasksToAdd: Int) {
        val tasks =
            (0..<tasksToAdd).map {
                Task(Task.TaskKey(it, 0, Intent(), ComponentName("", ""), 0, 2000))
            }
        recentsModel.updateRecentTasks(listOf(DesktopTask(tasks)))
        desktopTaskListener?.onTasksVisibilityChanged(
            context.virtualDisplay.display.displayId,
            tasksToAdd,
        )
        runOnMainSync { recentsModel.resolvePendingTaskRequests() }
    }

    private val navButtonEndSpacing: Int
        get() {
            return taskbarUnitTestRule.activityContext.resources.getDimensionPixelSize(
                taskbarUnitTestRule.activityContext.deviceProfile.inv.inlineNavButtonsEndSpacing
            )
        }

    private val taskbarOverflowIconIndex: Int
        get() {
            return getOnUiThread {
                taskbarViewController.iconViews.indexOfFirst { it is TaskbarOverflowView }
            }
        }

    private val maxNumberOfTaskbarIcons: Int
        get() = getOnUiThread { taskbarViewController.maxNumIconViews }

    private val currentNumberOfTaskbarIcons: Int
        get() = getOnUiThread { taskbarViewController.iconViews.size }

    private val taskbarIconsCentered: Boolean
        get() {
            return getOnUiThread {
                val iconLayoutBounds =
                    taskbarViewController.transientTaskbarIconLayoutBoundsInParent
                val availableWidth = taskbarUnitTestRule.activityContext.deviceProfile.widthPx
                iconLayoutBounds.left - (availableWidth - iconLayoutBounds.right) < 2
            }
        }

    private val taskbarEndMargin: Int
        get() {
            return getOnUiThread {
                taskbarUnitTestRule.activityContext.deviceProfile.widthPx -
                    taskbarViewController.transientTaskbarIconLayoutBoundsInParent.right
            }
        }

    /**
     * Adds enough running apps for taskbar to enter overflow of `targetOverflowSize`, and verifies
     * * max number of icons in the taskbar remains unchanged
     * * number of icons in the taskbar is at most max number of icons
     * * whether the taskbar overflow icon is shown, and its position in taskbar.
     *
     * Returns max number of icons.
     */
    private fun addRunningAppsAndVerifyOverflowState(targetOverflowSize: Int): Int {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(0)
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)
        assertThat(initialIconCount).isLessThan(maxNumIconViews)

        createDesktopTask(maxNumIconViews - initialIconCount + targetOverflowSize)

        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex)
            .isEqualTo(if (targetOverflowSize > 0) initialIconCount else -1)
        return maxNumIconViews
    }
}
