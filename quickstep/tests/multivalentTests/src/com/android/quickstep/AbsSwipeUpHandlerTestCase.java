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

package com.android.quickstep;

import static com.android.quickstep.AbsSwipeUpHandler.STATE_HANDLER_INVALIDATED;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherRootView;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.util.SystemUiController;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.system.InputConsumerController;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.HashMap;

public abstract class AbsSwipeUpHandlerTestCase<
        RECENTS_CONTAINER extends Context & RecentsViewContainer,
        STATE extends BaseState<STATE>,
        RECENTS_VIEW extends RecentsView<RECENTS_CONTAINER, STATE>,
        ACTIVITY_TYPE extends  StatefulActivity<STATE> & RecentsViewContainer,
        ACTIVITY_INTERFACE extends BaseActivityInterface<STATE, ACTIVITY_TYPE>,
        SWIPE_HANDLER extends AbsSwipeUpHandler<RECENTS_CONTAINER, RECENTS_VIEW, STATE>> {

    protected final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    protected final TaskAnimationManager mTaskAnimationManager = new TaskAnimationManager(mContext);
    protected final RecentsAnimationDeviceState mRecentsAnimationDeviceState =
            new RecentsAnimationDeviceState(mContext, true);
    protected final InputConsumerController mInputConsumerController =
            InputConsumerController.getRecentsAnimationInputConsumer();
    protected final ActivityManager.RunningTaskInfo mRunningTaskInfo =
            new ActivityManager.RunningTaskInfo();
    protected final TopTaskTracker.CachedTaskInfo mCachedTaskInfo =
            new TopTaskTracker.CachedTaskInfo(Collections.singletonList(mRunningTaskInfo));
    protected final RemoteAnimationTarget mRemoteAnimationTarget = new RemoteAnimationTarget(
            /* taskId= */ 0,
            /* mode= */ RemoteAnimationTarget.MODE_CLOSING,
            /* leash= */ new SurfaceControl(),
            /* isTranslucent= */ false,
            /* clipRect= */ null,
            /* contentInsets= */ null,
            /* prefixOrderIndex= */ 0,
            /* position= */ null,
            /* localBounds= */ null,
            /* screenSpaceBounds= */ null,
            new Configuration().windowConfiguration,
            /* isNotInRecents= */ false,
            /* startLeash= */ null,
            /* startBounds= */ null,
            /* taskInfo= */ mRunningTaskInfo,
            /* allowEnterPip= */ false);
    protected final RecentsAnimationTargets mRecentsAnimationTargets = new RecentsAnimationTargets(
            new RemoteAnimationTarget[] {mRemoteAnimationTarget},
            new RemoteAnimationTarget[] {mRemoteAnimationTarget},
            new RemoteAnimationTarget[] {mRemoteAnimationTarget},
            /* homeContentInsets= */ new Rect(),
            /* minimizedHomeBounds= */ null,
            new Bundle());

    @Mock protected ACTIVITY_INTERFACE mActivityInterface;
    @Mock protected ActivityInitListener<?> mActivityInitListener;
    @Mock protected RecentsAnimationController mRecentsAnimationController;
    @Mock protected STATE mState;
    @Mock protected ViewTreeObserver mViewTreeObserver;
    @Mock protected DragLayer mDragLayer;
    @Mock protected LauncherRootView mRootView;
    @Mock protected SystemUiController mSystemUiController;
    @Mock protected GestureState mGestureState;

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Before
    public void setUpRunningTaskInfo() {
        mRunningTaskInfo.baseIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Before
    public void setUpGestureState() {
        when(mGestureState.getRunningTask()).thenReturn(mCachedTaskInfo);
        when(mGestureState.getLastAppearedTaskIds()).thenReturn(new int[0]);
        when(mGestureState.getLastStartedTaskIds()).thenReturn(new int[1]);
        when(mGestureState.getHomeIntent()).thenReturn(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        doReturn(mActivityInterface).when(mGestureState).getContainerInterface();
    }

    @Before
    public void setUpRecentsView() {
        RECENTS_VIEW recentsView = getRecentsView();
        when(recentsView.getViewTreeObserver()).thenReturn(mViewTreeObserver);
        doAnswer(answer -> {
            runOnMainSync(() -> answer.<Runnable>getArgument(0).run());
            return this;
        }).when(recentsView).runOnPageScrollsInitialized(any());
    }

    @Before
    public void setUpRecentsContainer() {
        RecentsViewContainer recentsContainer = getRecentsContainer();
        RECENTS_VIEW recentsView = getRecentsView();

        when(recentsContainer.getDeviceProfile()).thenReturn(new DeviceProfile());
        when(recentsContainer.getOverviewPanel()).thenReturn(recentsView);
        when(recentsContainer.getDragLayer()).thenReturn(mDragLayer);
        when(recentsContainer.getRootView()).thenReturn(mRootView);
        when(recentsContainer.getSystemUiController()).thenReturn(mSystemUiController);
        when(mActivityInterface.createActivityInitListener(any()))
                .thenReturn(mActivityInitListener);
        doReturn(recentsContainer).when(mActivityInterface).getCreatedContainer();
        doAnswer(answer -> {
            answer.<Runnable>getArgument(0).run();
            return this;
        }).when(recentsContainer).runOnBindToTouchInteractionService(any());
    }

    @Test
    public void testInitWhenReady_registersActivityInitListener() {
        String reasonString = "because i said so";

        createSwipeHandler().initWhenReady(reasonString);
        verify(mActivityInitListener).register(eq(reasonString));
    }

    @Test
    public void testOnRecentsAnimationCanceled_unregistersActivityInitListener() {
        createSwipeHandler()
                .onRecentsAnimationCanceled(new HashMap<>());

        runOnMainSync(() -> verify(mActivityInitListener)
                .unregister(eq("AbsSwipeUpHandler.onRecentsAnimationCanceled")));
    }

    @Test
    public void testOnConsumerAboutToBeSwitched_unregistersActivityInitListener() {
        createSwipeHandler().onConsumerAboutToBeSwitched();

        runOnMainSync(() -> verify(mActivityInitListener)
                .unregister("AbsSwipeUpHandler.invalidateHandler"));
    }

    @Test
    public void testOnConsumerAboutToBeSwitched_midQuickSwitch_unregistersActivityInitListener() {
        createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.NEW_TASK)
                .onConsumerAboutToBeSwitched();

        runOnMainSync(() -> verify(mActivityInitListener)
                .unregister(eq("AbsSwipeUpHandler.cancelCurrentAnimation")));
    }

    @Test
    public void testStartNewTask_finishesRecentsAnimationController() {
        SWIPE_HANDLER absSwipeUpHandler = createSwipeHandler();

        onRecentsAnimationStart(absSwipeUpHandler);

        runOnMainSync(() -> {
            absSwipeUpHandler.startNewTask(unused -> {});
            verifyRecentsAnimationFinishedAndCallCallback();
        });
    }

    @Test
    public void testHomeGesture_finishesRecentsAnimationController() {
        createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.HOME);

        runOnMainSync(() -> {
            verify(mRecentsAnimationController).detachNavigationBarFromApp(true);
            verifyRecentsAnimationFinishedAndCallCallback();
        });
    }

    @Test
    public void testHomeGesture_invalidatesHandlerAfterParallelAnim() {
        ValueAnimator parallelAnim = new ValueAnimator();
        parallelAnim.setRepeatCount(ValueAnimator.INFINITE);
        when(mActivityInterface.getParallelAnimationToLauncher(any(), anyLong(), any()))
                .thenReturn(parallelAnim);
        SWIPE_HANDLER handler = createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.HOME);
        runOnMainSync(() -> {
            parallelAnim.start();
            verifyRecentsAnimationFinishedAndCallCallback();
            assertFalse(handler.mStateCallback.hasStates(STATE_HANDLER_INVALIDATED));
            parallelAnim.end();
            assertTrue(handler.mStateCallback.hasStates(STATE_HANDLER_INVALIDATED));
        });
    }

    @Test
    public void testHomeGesture_invalidatesHandlerIfNoParallelAnim() {
        when(mActivityInterface.getParallelAnimationToLauncher(any(), anyLong(), any()))
                .thenReturn(null);
        SWIPE_HANDLER handler = createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.HOME);
        runOnMainSync(() -> {
            verifyRecentsAnimationFinishedAndCallCallback();
            assertTrue(handler.mStateCallback.hasStates(STATE_HANDLER_INVALIDATED));
        });
    }

    /**
     * Verifies that RecentsAnimationController#finish() is called, and captures and runs any
     * callback that was passed to it. This ensures that STATE_CURRENT_TASK_FINISHED is correctly
     * set for example.
     */
    private void verifyRecentsAnimationFinishedAndCallCallback() {
        ArgumentCaptor<Runnable> finishCallback = ArgumentCaptor.forClass(Runnable.class);
        // Check if the 2 parameter method is called.
        verify(mRecentsAnimationController, atLeast(0)).finish(
                anyBoolean(), finishCallback.capture());
        if (finishCallback.getAllValues().isEmpty()) {
            // Check if the 3 parameter method is called.
            verify(mRecentsAnimationController).finish(
                    anyBoolean(), finishCallback.capture(), anyBoolean());
        }
        if (finishCallback.getValue() != null) {
            finishCallback.getValue().run();
        }
    }

    private SWIPE_HANDLER createSwipeUpHandlerForGesture(GestureState.GestureEndTarget endTarget) {
        boolean isQuickSwitch = endTarget == GestureState.GestureEndTarget.NEW_TASK;

        doReturn(mState).when(mActivityInterface).stateFromGestureEndTarget(any());

        SWIPE_HANDLER swipeHandler = createSwipeHandler(SystemClock.uptimeMillis(), isQuickSwitch);

        swipeHandler.onActivityInit(/* alreadyOnHome= */ false);
        swipeHandler.onGestureStarted(isQuickSwitch);
        onRecentsAnimationStart(swipeHandler);

        when(mGestureState.getRunningTaskIds(anyBoolean())).thenReturn(new int[0]);
        runOnMainSync(swipeHandler::switchToScreenshot);

        when(mGestureState.getEndTarget()).thenReturn(endTarget);
        when(mGestureState.isRecentsAnimationRunning()).thenReturn(isQuickSwitch);
        float xVelocityPxPerMs = isQuickSwitch ? 100 : 0;
        float yVelocityPxPerMs = isQuickSwitch ? 0 : -100;
        swipeHandler.onGestureEnded(
                yVelocityPxPerMs, new PointF(xVelocityPxPerMs, yVelocityPxPerMs));
        swipeHandler.onCalculateEndTarget();
        runOnMainSync(swipeHandler::onSettledOnEndTarget);

        return swipeHandler;
    }

    private void onRecentsAnimationStart(SWIPE_HANDLER absSwipeUpHandler) {
        when(mActivityInterface.getOverviewWindowBounds(any(), any())).thenReturn(new Rect());
        doNothing().when(mActivityInterface).setOnDeferredActivityLaunchCallback(any());

        runOnMainSync(() -> absSwipeUpHandler.onRecentsAnimationStart(
                mRecentsAnimationController, mRecentsAnimationTargets));
    }

    private static void runOnMainSync(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    @NonNull
    private SWIPE_HANDLER createSwipeHandler() {
        return createSwipeHandler(SystemClock.uptimeMillis(), false);
    }

    @NonNull
    protected abstract SWIPE_HANDLER createSwipeHandler(
            long touchTimeMs, boolean continuingLastGesture);

    @NonNull
    protected abstract RecentsViewContainer getRecentsContainer();

    @NonNull
    protected abstract RECENTS_VIEW getRecentsView();
}
