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

import android.app.Instrumentation
import android.app.PendingIntent
import android.content.IIntentSender
import android.content.Intent
import android.provider.Settings
import android.provider.Settings.Secure.NAV_BAR_KIDS_MODE
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.android.launcher3.LauncherAppState
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks
import com.android.launcher3.taskbar.TaskbarViewController
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.LauncherMultivalentJUnit.Companion.isRunningInRobolectric
import com.android.launcher3.util.TestUtil
import com.android.quickstep.AllAppsActionManager
import com.android.quickstep.TouchInteractionService
import com.android.quickstep.TouchInteractionService.TISBinder
import org.junit.Assume.assumeTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Manages the Taskbar lifecycle for unit tests.
 *
 * Tests should pass in themselves as [testInstance]. They also need to provide their target
 * [context] through the constructor.
 *
 * See [InjectController] for grabbing controller(s) under test with minimal boilerplate.
 *
 * The rule interacts with [TaskbarManager] on the main thread. A good rule of thumb for tests is
 * that code that is executed on the main thread in production should also happen on that thread
 * when tested.
 *
 * `@UiThreadTest` is incompatible with this rule. The annotation causes this rule to run on the
 * main thread, but it needs to be run on the test thread for it to work properly. Instead, only run
 * code that requires the main thread using something like [Instrumentation.runOnMainSync] or
 * [TestUtil.getOnUiThread].
 *
 * ```
 * @Test
 * fun example() {
 *     instrumentation.runOnMainSync { doWorkThatPostsMessage() }
 *     // Second lambda will not execute until message is processed.
 *     instrumentation.runOnMainSync { verifyMessageResults() }
 * }
 * ```
 */
class TaskbarUnitTestRule(
    private val testInstance: Any,
    private val context: TaskbarWindowSandboxContext,
) : TestRule {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val serviceTestRule = ServiceTestRule()

    private val userSetupCompleteRule = TaskbarSecureSettingRule(USER_SETUP_COMPLETE)
    private val kidsModeRule = TaskbarSecureSettingRule(NAV_BAR_KIDS_MODE)
    private val settingRules = RuleChain.outerRule(userSetupCompleteRule).around(kidsModeRule)

    private lateinit var taskbarManager: TaskbarManager

    val activityContext: TaskbarActivityContext
        get() {
            return taskbarManager.currentActivityContext
                ?: throw RuntimeException("Failed to obtain TaskbarActivityContext.")
        }

    override fun apply(base: Statement, description: Description): Statement {
        return settingRules.apply(createStatement(base, description), description)
    }

    private fun createStatement(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {

                // Only run test when Taskbar is enabled.
                instrumentation.runOnMainSync {
                    assumeTrue(
                        LauncherAppState.getIDP(context).getDeviceProfile(context).isTaskbarPresent
                    )
                }

                // Process secure setting annotations.
                instrumentation.runOnMainSync {
                    userSetupCompleteRule.putInt(
                        if (description.getAnnotation(UserSetupMode::class.java) != null) {
                            0
                        } else {
                            1
                        }
                    )
                    kidsModeRule.putInt(
                        if (description.getAnnotation(NavBarKidsMode::class.java) != null) 1 else 0
                    )
                }

                // Check for existing Taskbar instance from Launcher process.
                val launcherTaskbarManager: TaskbarManager? =
                    if (!isRunningInRobolectric) {
                        try {
                            val tisBinder =
                                serviceTestRule.bindService(
                                    Intent(context, TouchInteractionService::class.java)
                                ) as? TISBinder
                            tisBinder?.taskbarManager
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }

                taskbarManager =
                    TestUtil.getOnUiThread {
                        object :
                            TaskbarManager(
                                context,
                                AllAppsActionManager(context, UI_HELPER_EXECUTOR) {
                                    PendingIntent(IIntentSender.Default())
                                },
                                object : TaskbarNavButtonCallbacks {},
                                DesktopVisibilityController(context),
                            ) {
                            override fun recreateTaskbar() {
                                super.recreateTaskbar()
                                if (currentActivityContext != null) injectControllers()
                            }
                        }
                    }

                try {
                    TaskbarViewController.enableModelLoadingForTests(false)

                    // Replace Launcher Taskbar window with test instance.
                    instrumentation.runOnMainSync {
                        launcherTaskbarManager?.setSuspended(true)
                        taskbarManager.onUserUnlocked() // Required to complete initialization.
                    }

                    base.evaluate()
                } finally {
                    // Revert Taskbar window.
                    instrumentation.runOnMainSync {
                        taskbarManager.destroy()
                        launcherTaskbarManager?.setSuspended(false)
                    }

                    TaskbarViewController.enableModelLoadingForTests(true)
                }
            }
        }
    }

    /** Simulates Taskbar recreation lifecycle. */
    fun recreateTaskbar() = instrumentation.runOnMainSync { taskbarManager.recreateTaskbar() }

    private fun injectControllers() {
        val controllers = activityContext.controllers
        val controllerFieldsByType = controllers.javaClass.fields.associateBy { it.type }
        testInstance.javaClass.fields
            .filter { it.isAnnotationPresent(InjectController::class.java) }
            .forEach {
                it.set(
                    testInstance,
                    controllerFieldsByType[it.type]?.get(controllers)
                        ?: throw NoSuchElementException("Failed to find controller for ${it.type}"),
                )
            }
    }

    /**
     * Annotates test controller fields to inject the corresponding controllers from the current
     * [TaskbarControllers] instance.
     *
     * Controllers are injected during test setup and upon calling [recreateTaskbar].
     *
     * Multiple controllers can be injected if needed.
     */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FIELD)
    annotation class InjectController

    /** Overrides [USER_SETUP_COMPLETE] to be `false` for tests. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class UserSetupMode

    /** Overrides [NAV_BAR_KIDS_MODE] to be `true` for tests. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class NavBarKidsMode

    /** Rule for Taskbar integer-based secure settings. */
    private inner class TaskbarSecureSettingRule(private val settingName: String) : TestRule {

        override fun apply(base: Statement, description: Description): Statement {
            return object : Statement() {
                override fun evaluate() {
                    val originalValue =
                        Settings.Secure.getInt(context.contentResolver, settingName, /* def= */ 0)
                    try {
                        base.evaluate()
                    } finally {
                        instrumentation.runOnMainSync { putInt(originalValue) }
                    }
                }
            }
        }

        /** Puts [value] into secure settings under [settingName]. */
        fun putInt(value: Int) = Settings.Secure.putInt(context.contentResolver, settingName, value)
    }
}
