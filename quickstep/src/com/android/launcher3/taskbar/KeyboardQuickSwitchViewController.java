/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.KeyEvent;
import android.view.animation.AnimationUtils;
import android.window.RemoteTransition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.jank.Cuj;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SlideInRemoteTransition;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.QuickStepContract;

import java.io.PrintWriter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles initialization of the {@link KeyboardQuickSwitchView} and supplies it with the list of
 * tasks.
 */
public class KeyboardQuickSwitchViewController {

    @NonNull private final ViewCallbacks mViewCallbacks = new ViewCallbacks();
    @NonNull private final TaskbarControllers mControllers;
    @NonNull private final TaskbarOverlayContext mOverlayContext;
    @NonNull private final KeyboardQuickSwitchView mKeyboardQuickSwitchView;
    @NonNull private final KeyboardQuickSwitchController.ControllerCallbacks mControllerCallbacks;

    @Nullable private Animator mCloseAnimation;

    private int mCurrentFocusIndex = -1;

    private boolean mOnDesktop;
    private boolean mWasDesktopTaskFilteredOut;

    protected KeyboardQuickSwitchViewController(
            @NonNull TaskbarControllers controllers,
            @NonNull TaskbarOverlayContext overlayContext,
            @NonNull KeyboardQuickSwitchView keyboardQuickSwitchView,
            @NonNull KeyboardQuickSwitchController.ControllerCallbacks controllerCallbacks) {
        mControllers = controllers;
        mOverlayContext = overlayContext;
        mKeyboardQuickSwitchView = keyboardQuickSwitchView;
        mControllerCallbacks = controllerCallbacks;
    }

    protected int getCurrentFocusedIndex() {
        return mCurrentFocusIndex;
    }

    protected void openQuickSwitchView(
            @NonNull List<GroupTask> tasks,
            int numHiddenTasks,
            boolean updateTasks,
            int currentFocusIndexOverride,
            boolean onDesktop,
            boolean hasDesktopTask,
            boolean wasDesktopTaskFilteredOut) {
        mOverlayContext.getDragLayer().addView(mKeyboardQuickSwitchView);
        mOnDesktop = onDesktop;
        mWasDesktopTaskFilteredOut = wasDesktopTaskFilteredOut;

        mKeyboardQuickSwitchView.applyLoadPlan(
                mOverlayContext,
                tasks,
                numHiddenTasks,
                updateTasks,
                currentFocusIndexOverride,
                mViewCallbacks,
                /* useDesktopTaskView= */ !onDesktop && hasDesktopTask);
    }

    boolean isCloseAnimationRunning() {
        return mCloseAnimation != null;
    }

    protected void closeQuickSwitchView(boolean animate) {
        if (isCloseAnimationRunning()) {
            if (!animate) {
                mCloseAnimation.end();
            }
            // Let currently-running animation finish.
            return;
        }
        if (!animate) {
            InteractionJankMonitorWrapper.begin(
                    mKeyboardQuickSwitchView, Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_CLOSE);
            onCloseComplete();
            return;
        }
        mCloseAnimation = mKeyboardQuickSwitchView.getCloseAnimation();

        mCloseAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                InteractionJankMonitorWrapper.begin(
                        mKeyboardQuickSwitchView, Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_CLOSE);
            }
        });
        mCloseAnimation.addListener(AnimatorListeners.forEndCallback(this::onCloseComplete));
        mCloseAnimation.start();
    }

    /**
     * Launched the currently-focused task.
     *
     * Returns index -1 iff the RecentsView shouldn't be opened.
     *
     * If the index is not -1, then the {@link com.android.quickstep.views.TaskView} at the returned
     * index will be focused.
     */
    protected int launchFocusedTask() {
        if (mCurrentFocusIndex != -1) {
            return launchTaskAt(mCurrentFocusIndex);
        }
        // If the user quick switches too quickly, updateCurrentFocusIndex might not have run.
        return launchTaskAt(mControllerCallbacks.isFirstTaskRunning()
                && mKeyboardQuickSwitchView.getTaskCount() > 1 ? 1 : 0);
    }

    private int launchTaskAt(int index) {
        if (isCloseAnimationRunning()) {
            // Ignore taps on task views and alt key unpresses while the close animation is running.
            return -1;
        }
        if (index == mKeyboardQuickSwitchView.getOverviewTaskIndex()) {
            // If there is a desktop task view, then we should account for it when focusing the
            // first hidden non-desktop task view in recents view
            return mOnDesktop ? 1 : (mWasDesktopTaskFilteredOut ? index + 1 : index);
        }
        Runnable onStartCallback = () -> InteractionJankMonitorWrapper.begin(
                mKeyboardQuickSwitchView, Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_APP_LAUNCH);
        Runnable onFinishCallback = () -> InteractionJankMonitorWrapper.end(
                Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_APP_LAUNCH);
        TaskbarActivityContext context = mControllers.taskbarActivityContext;
        RemoteTransition remoteTransition = new RemoteTransition(new SlideInRemoteTransition(
                Utilities.isRtl(mControllers.taskbarActivityContext.getResources()),
                context.getDeviceProfile().overviewPageSpacing,
                QuickStepContract.getWindowCornerRadius(context),
                AnimationUtils.loadInterpolator(
                        context, android.R.interpolator.fast_out_extra_slow_in),
                onStartCallback,
                onFinishCallback),
                "SlideInTransition");
        if (index == mKeyboardQuickSwitchView.getDesktopTaskIndex()) {
            UI_HELPER_EXECUTOR.execute(() ->
                    SystemUiProxy.INSTANCE.get(mKeyboardQuickSwitchView.getContext())
                            .showDesktopApps(
                                    mKeyboardQuickSwitchView.getDisplay().getDisplayId(),
                                    remoteTransition));
            return -1;
        }
        // Even with a valid index, this can be null if the user tries to quick switch before the
        // views have been added in the KeyboardQuickSwitchView.
        GroupTask task = mControllerCallbacks.getTaskAt(index);
        if (task == null) {
            return mOnDesktop ? 1 : Math.max(0, index);
        }
        if (mControllerCallbacks.isTaskRunning(task)) {
            // Ignore attempts to run the selected task if it is already running.
            return -1;
        }
        mControllers.taskbarActivityContext.handleGroupTaskLaunch(
                task,
                remoteTransition,
                mOnDesktop,
                onStartCallback,
                onFinishCallback);
        return -1;
    }

    private void onCloseComplete() {
        mCloseAnimation = null;
        mOverlayContext.getDragLayer().removeView(mKeyboardQuickSwitchView);
        mControllerCallbacks.onCloseComplete();
        InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_CLOSE);
    }

    protected void onDestroy() {
        closeQuickSwitchView(false);
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "KeyboardQuickSwitchViewController:");

        pw.println(prefix + "\thasFocus=" + mKeyboardQuickSwitchView.hasFocus());
        pw.println(prefix + "\tisCloseAnimationRunning=" + isCloseAnimationRunning());
        pw.println(prefix + "\tmCurrentFocusIndex=" + mCurrentFocusIndex);
        pw.println(prefix + "\tmOnDesktop=" + mOnDesktop);
        pw.println(prefix + "\tmWasDesktopTaskFilteredOut=" + mWasDesktopTaskFilteredOut);
    }

    class ViewCallbacks {

        boolean onKeyUp(int keyCode, KeyEvent event, boolean isRTL, boolean allowTraversal) {
            if (keyCode != KeyEvent.KEYCODE_TAB
                    && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                    && keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                    && keyCode != KeyEvent.KEYCODE_GRAVE
                    && keyCode != KeyEvent.KEYCODE_ESCAPE
                    && keyCode != KeyEvent.KEYCODE_ENTER) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_GRAVE || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                closeQuickSwitchView(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                launchTaskAt(mCurrentFocusIndex);
                return true;
            }
            if (!allowTraversal) {
                return false;
            }
            boolean traverseBackwards = (keyCode == KeyEvent.KEYCODE_TAB && event.isShiftPressed())
                    || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isRTL)
                    || (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !isRTL);
            int taskCount = mKeyboardQuickSwitchView.getTaskCount();
            int toIndex = mCurrentFocusIndex == -1
                    // Focus the second-most recent app if possible
                    ? (taskCount > 1 ? 1 : 0)
                    : (traverseBackwards
                            // focus a more recent task or loop back to the opposite end
                            ? Math.max(0, mCurrentFocusIndex == 0
                                    ? taskCount - 1 : mCurrentFocusIndex - 1)
                            // focus a less recent app or loop back to the opposite end
                            : ((mCurrentFocusIndex + 1) % taskCount));

            if (mCurrentFocusIndex == toIndex) {
                return true;
            }
            mKeyboardQuickSwitchView.animateFocusMove(mCurrentFocusIndex, toIndex);

            return true;
        }

        void updateCurrentFocusIndex(int index) {
            mCurrentFocusIndex = index;
        }

        void launchTaskAt(int index) {
            mCurrentFocusIndex = index;
            mControllers.taskbarActivityContext.launchKeyboardFocusedTask();
        }

        void updateThumbnailInBackground(Task task, Consumer<ThumbnailData> callback) {
            mControllerCallbacks.updateThumbnailInBackground(task, callback);
        }

        void updateIconInBackground(Task task, Consumer<Task> callback) {
            mControllerCallbacks.updateIconInBackground(task, callback);
        }

        boolean isAspectRatioSquare() {
            return mControllerCallbacks.isAspectRatioSquare();
        }
    }
}
