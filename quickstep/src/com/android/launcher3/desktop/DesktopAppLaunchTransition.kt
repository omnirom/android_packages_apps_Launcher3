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

package com.android.launcher3.desktop

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.IBinder
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransitionStub
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import androidx.core.animation.addListener
import com.android.app.animation.Interpolators
import com.android.internal.policy.ScreenDecorationsUtils
import com.android.quickstep.RemoteRunnable
import com.android.wm.shell.shared.animation.MinimizeAnimator
import com.android.wm.shell.shared.animation.WindowAnimator
import java.util.concurrent.Executor

/**
 * [android.window.RemoteTransition] for Desktop app launches.
 *
 * This transition supports minimize-changes, i.e. in a launch-transition, if a window is moved back
 * ([android.view.WindowManager.TRANSIT_TO_BACK]) this transition will apply a minimize animation to
 * that window.
 */
class DesktopAppLaunchTransition(
    private val context: Context,
    private val mainExecutor: Executor,
    private val launchType: AppLaunchType,
) : RemoteTransitionStub() {

    enum class AppLaunchType(
        val boundsAnimationParams: WindowAnimator.BoundsAnimationParams,
        val alphaDurationMs: Long,
    ) {
        LAUNCH(launchBoundsAnimationDef, /* alphaDurationMs= */ 200L),
        UNMINIMIZE(unminimizeBoundsAnimationDef, /* alphaDurationMs= */ 100L),
    }

    override fun startAnimation(
        token: IBinder,
        info: TransitionInfo,
        t: Transaction,
        transitionFinishedCallback: IRemoteTransitionFinishedCallback,
    ) {
        val safeTransitionFinishedCallback = RemoteRunnable {
            transitionFinishedCallback.onTransitionFinished(/* wct= */ null, /* sct= */ null)
        }
        mainExecutor.execute {
            runAnimators(info, safeTransitionFinishedCallback)
            t.apply()
        }
    }

    private fun runAnimators(info: TransitionInfo, finishedCallback: RemoteRunnable) {
        val animators = mutableListOf<Animator>()
        val animatorFinishedCallback: (Animator) -> Unit = { animator ->
            animators -= animator
            if (animators.isEmpty()) finishedCallback.run()
        }
        animators += createAnimators(info, animatorFinishedCallback)
        animators.forEach { it.start() }
    }

    private fun createAnimators(
        info: TransitionInfo,
        finishCallback: (Animator) -> Unit,
    ): List<Animator> {
        val transaction = Transaction()
        val launchAnimator =
            createLaunchAnimator(getLaunchChange(info), transaction, finishCallback)
        val minimizeChange = getMinimizeChange(info) ?: return listOf(launchAnimator)
        val minimizeAnimator =
            MinimizeAnimator.create(
                context.resources.displayMetrics,
                minimizeChange,
                transaction,
                finishCallback,
            )
        return listOf(launchAnimator, minimizeAnimator)
    }

    private fun getLaunchChange(info: TransitionInfo): Change =
        requireNotNull(info.changes.firstOrNull { change -> change.mode in LAUNCH_CHANGE_MODES }) {
            "expected an app launch Change"
        }

    private fun getMinimizeChange(info: TransitionInfo): Change? =
        info.changes.firstOrNull { change -> change.mode == TRANSIT_TO_BACK }

    private fun createLaunchAnimator(
        change: Change,
        transaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
    ): Animator {
        val boundsAnimator =
            WindowAnimator.createBoundsAnimator(
                context.resources.displayMetrics,
                launchType.boundsAnimationParams,
                change,
                transaction,
            )
        val alphaAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = launchType.alphaDurationMs
                interpolator = Interpolators.LINEAR
                addUpdateListener { animation ->
                    transaction.setAlpha(change.leash, animation.animatedValue as Float).apply()
                }
            }
        val clipRect = Rect(change.endAbsBounds).apply { offsetTo(0, 0) }
        transaction.setCrop(change.leash, clipRect)
        transaction.setCornerRadius(
            change.leash,
            ScreenDecorationsUtils.getWindowCornerRadius(context),
        )
        return AnimatorSet().apply {
            playTogether(boundsAnimator, alphaAnimator)
            addListener(onEnd = { animation -> onAnimFinish(animation) })
        }
    }

    companion object {
        /** Change modes that represent a task becoming visible / launching in Desktop mode. */
        val LAUNCH_CHANGE_MODES = intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT)

        private val launchBoundsAnimationDef =
            WindowAnimator.BoundsAnimationParams(
                durationMs = 600,
                startOffsetYDp = 36f,
                startScale = 0.95f,
                interpolator = Interpolators.STANDARD_DECELERATE,
            )

        private val unminimizeBoundsAnimationDef =
            WindowAnimator.BoundsAnimationParams(
                durationMs = 300,
                startOffsetYDp = 12f,
                startScale = 0.97f,
                interpolator = Interpolators.STANDARD_DECELERATE,
            )
    }
}
