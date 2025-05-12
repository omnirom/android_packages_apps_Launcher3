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
package com.android.launcher3.taskbar.bubbles;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.android.launcher3.Utilities.mapRange;
import static com.android.launcher3.taskbar.TaskbarPinningController.PINNING_PERSISTENT;
import static com.android.launcher3.taskbar.TaskbarPinningController.PINNING_TRANSIENT;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarInsetsController;
import com.android.launcher3.taskbar.TaskbarSharedState;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.taskbar.bubbles.animation.BubbleBarViewAnimator;
import com.android.launcher3.taskbar.bubbles.flyout.BubbleBarFlyoutController;
import com.android.launcher3.taskbar.bubbles.flyout.BubbleBarFlyoutPositioner;
import com.android.launcher3.taskbar.bubbles.flyout.FlyoutCallbacks;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.SystemUiProxy;
import com.android.wm.shell.Flags;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Controller for {@link BubbleBarView}. Manages the visibility of the bubble bar as well as
 * responding to changes in bubble state provided by BubbleBarController.
 */
public class BubbleBarViewController {

    private static final String TAG = "BubbleBarViewController";
    private static final float APP_ICON_SMALL_DP = 44f;
    private static final float APP_ICON_MEDIUM_DP = 48f;
    private static final float APP_ICON_LARGE_DP = 52f;
    /** The dot size is defined as a percentage of the icon size. */
    private static final float DOT_TO_BUBBLE_SIZE_RATIO = 0.228f;
    public static final int TASKBAR_FADE_IN_DURATION_MS = 150;
    public static final int TASKBAR_FADE_IN_DELAY_MS = 50;
    public static final int TASKBAR_FADE_OUT_DURATION_MS = 100;
    private final SystemUiProxy mSystemUiProxy;
    private final TaskbarActivityContext mActivity;
    private final BubbleBarView mBarView;
    private int mIconSize;
    private int mBubbleBarPadding;
    private final int mDragElevation;

    // Initialized in init.
    private BubbleStashController mBubbleStashController;
    private BubbleBarController mBubbleBarController;
    private BubbleDragController mBubbleDragController;
    private TaskbarStashController mTaskbarStashController;
    private TaskbarInsetsController mTaskbarInsetsController;
    private TaskbarViewPropertiesProvider mTaskbarViewPropertiesProvider;
    private View.OnClickListener mBubbleClickListener;
    private BubbleView.Controller mBubbleViewController;
    private BubbleBarOverflow mOverflowBubble;

    // These are exposed to {@link BubbleStashController} to animate for stashing/un-stashing
    private final MultiValueAlpha mBubbleBarAlpha;
    private final AnimatedFloat mBubbleBarBubbleAlpha = new AnimatedFloat(this::updateBubbleAlpha);
    private final AnimatedFloat mBubbleBarBackgroundAlpha = new AnimatedFloat(
            this::updateBackgroundAlpha);
    private final AnimatedFloat mBubbleBarScaleX = new AnimatedFloat(this::updateScaleX);
    private final AnimatedFloat mBubbleBarScaleY = new AnimatedFloat(this::updateScaleY);
    private final AnimatedFloat mBubbleBarBackgroundScaleX = new AnimatedFloat(
            this::updateBackgroundScaleX);
    private final AnimatedFloat mBubbleBarBackgroundScaleY = new AnimatedFloat(
            this::updateBackgroundScaleY);
    private final AnimatedFloat mBubbleBarTranslationY = new AnimatedFloat(
            this::updateTranslationY);
    private final AnimatedFloat mBubbleOffsetY = new AnimatedFloat(
            this::updateBubbleOffsetY);
    private final AnimatedFloat mBubbleBarPinning = new AnimatedFloat(pinningProgress -> {
        updateTranslationY();
        setBubbleBarScaleAndPadding(pinningProgress);
    });

    // Modified when swipe up is happening on the bubble bar or task bar.
    private float mBubbleBarSwipeUpTranslationY;
    // Modified when bubble bar is springing back into the stash handle.
    private float mBubbleBarStashTranslationY;

    // Whether the bar is hidden for a sysui state.
    private boolean mHiddenForSysui;
    // Whether the bar is hidden because there are no bubbles.
    private boolean mHiddenForNoBubbles = true;
    // Whether the bar is hidden when stashed
    private boolean mHiddenForStashed;
    private boolean mShouldShowEducation;

    public boolean mOverflowAdded;

    private BubbleBarViewAnimator mBubbleBarViewAnimator;
    private final FrameLayout mBubbleBarContainer;
    private BubbleBarFlyoutController mBubbleBarFlyoutController;
    private TaskbarSharedState mTaskbarSharedState;
    private final TimeSource mTimeSource = System::currentTimeMillis;
    private final int mTaskbarTranslationDelta;

    @Nullable
    private BubbleBarBoundsChangeListener mBoundsChangeListener;

    public BubbleBarViewController(TaskbarActivityContext activity, BubbleBarView barView,
            FrameLayout bubbleBarContainer) {
        mActivity = activity;
        mBarView = barView;
        mBubbleBarContainer = bubbleBarContainer;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(mActivity);
        mBubbleBarAlpha = new MultiValueAlpha(mBarView, 1 /* num alpha channels */);
        mIconSize = activity.getResources().getDimensionPixelSize(
                R.dimen.bubblebar_icon_size);
        mDragElevation = activity.getResources().getDimensionPixelSize(
                R.dimen.bubblebar_drag_elevation);
        mTaskbarTranslationDelta = getBubbleBarTranslationDeltaForTaskbar(activity);
    }

    /** Initializes controller. */
    public void init(TaskbarControllers controllers, BubbleControllers bubbleControllers,
            TaskbarViewPropertiesProvider taskbarViewPropertiesProvider) {
        mTaskbarSharedState = controllers.getSharedState();
        mBubbleStashController = bubbleControllers.bubbleStashController;
        mBubbleBarController = bubbleControllers.bubbleBarController;
        mBubbleDragController = bubbleControllers.bubbleDragController;
        mTaskbarStashController = controllers.taskbarStashController;
        mTaskbarInsetsController = controllers.taskbarInsetsController;
        mBubbleBarFlyoutController = new BubbleBarFlyoutController(
                mBubbleBarContainer, createFlyoutPositioner(), createFlyoutCallbacks());
        mBubbleBarViewAnimator = new BubbleBarViewAnimator(
                mBarView, mBubbleStashController, mBubbleBarFlyoutController,
                createBubbleBarParentViewController(), mBubbleBarController::showExpandedView,
                () -> setHiddenForBubbles(false));
        mTaskbarViewPropertiesProvider = taskbarViewPropertiesProvider;
        onBubbleBarConfigurationChanged(/* animate= */ false);
        mActivity.addOnDeviceProfileChangeListener(
                dp -> onBubbleBarConfigurationChanged(/* animate= */ true));
        mBubbleBarScaleY.updateValue(1f);
        mBubbleClickListener = v -> onBubbleClicked((BubbleView) v);
        mBubbleDragController.setupBubbleBarView(mBarView);
        mOverflowBubble = bubbleControllers.bubbleCreator.createOverflow(mBarView);
        if (!Flags.enableOptionalBubbleOverflow()) {
            showOverflow(true);
        }
        mBarView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    mTaskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged();
                    if (mBoundsChangeListener != null) {
                        mBoundsChangeListener.onBoundsChanged();
                    }
                });
        float pinningValue = DisplayController.isTransientTaskbar(mActivity)
                ? PINNING_TRANSIENT
                : PINNING_PERSISTENT;
        mBubbleBarPinning.updateValue(pinningValue);
        mBarView.setController(new BubbleBarView.Controller() {
            @Override
            public float getBubbleBarTranslationY() {
                return mBubbleStashController.getBubbleBarTranslationY();
            }

            @Override
            public void onBubbleBarTouched() {
                if (isAnimatingNewBubble()) {
                    interruptAnimationForTouch();
                }
            }

            @Override
            public void expandBubbleBar() {
                BubbleBarViewController.this.setExpanded(
                        /* isExpanded= */ true, /* maybeShowEdu*/ true);
            }

            @Override
            public void dismissBubbleBar() {
                onDismissAllBubbles();
            }

            @Override
            public void updateBubbleBarLocation(BubbleBarLocation location,
                    @BubbleBarLocation.UpdateSource int source) {
                mBubbleBarController.updateBubbleBarLocation(location, source);
            }

            @Override
            public void setIsDragging(boolean dragging) {
                mBubbleBarContainer.setElevation(dragging ? mDragElevation : 0);
            }
        });

        mBubbleViewController = new BubbleView.Controller() {
            @Override
            public BubbleBarLocation getBubbleBarLocation() {
                return BubbleBarViewController.this.getBubbleBarLocation();
            }

            @Override
            public void dismiss(BubbleView bubble) {
                if (bubble.getBubble() != null) {
                    notifySysUiBubbleDismissed(bubble.getBubble());
                }
                onBubbleDismissed(bubble);
            }

            @Override
            public void collapse() {
                collapseBubbleBar();
            }

            @Override
            public void updateBubbleBarLocation(BubbleBarLocation location,
                    @BubbleBarLocation.UpdateSource int source) {
                mBubbleBarController.updateBubbleBarLocation(location, source);
            }
        };
    }

    /** Returns animated float property responsible for pinning transition animation. */
    public AnimatedFloat getBubbleBarPinning() {
        return mBubbleBarPinning;
    }

    private BubbleBarFlyoutPositioner createFlyoutPositioner() {
        return new BubbleBarFlyoutPositioner() {

            @Override
            public boolean isOnLeft() {
                return mBarView.getBubbleBarLocation().isOnLeft(mBarView.isLayoutRtl());
            }

            @Override
            public float getTargetTy() {
                return mBarView.getTranslationY() - mBarView.getHeight();
            }

            @Override
            @NonNull
            public PointF getDistanceToCollapsedPosition() {
                // the flyout animates from the selected bubble dot. calculate the distance it needs
                // to translate itself to its starting position.
                PointF distanceToDotCenter = mBarView.getSelectedBubbleDotDistanceFromTopLeft();

                // if we're gravitating left, return the distance between the top left corner of the
                // bubble bar and the bottom left corner of the dot.
                // if we're gravitating right, return the distance between the top right corner of
                // the bubble bar and the bottom right corner of the dot.
                float distanceX = isOnLeft()
                        ? distanceToDotCenter.x - getCollapsedSize() / 2
                        : mBarView.getWidth() - distanceToDotCenter.x - getCollapsedSize() / 2;
                float distanceY = distanceToDotCenter.y + getCollapsedSize() / 2;
                return new PointF(distanceX, distanceY);
            }

            @Override
            public float getCollapsedSize() {
                return mIconSize * DOT_TO_BUBBLE_SIZE_RATIO;
            }

            @Override
            public int getCollapsedColor() {
                return mBarView.getSelectedBubbleDotColor();
            }

            @Override
            public float getCollapsedElevation() {
                return mBarView.getBubbleElevation();
            }

            @Override
            public float getDistanceToRevealTriangle() {
                return getDistanceToCollapsedPosition().y - mBarView.getPointerSize();
            }
        };
    }

    private FlyoutCallbacks createFlyoutCallbacks() {
        return new FlyoutCallbacks() {
            @Override
            public void flyoutClicked() {
                interruptAnimationForTouch();
                setExpanded(/* isExpanded= */ true, /* maybeShowEdu*/ true);
            }
        };
    }

    private BubbleBarParentViewHeightUpdateNotifier createBubbleBarParentViewController() {
        return new BubbleBarParentViewHeightUpdateNotifier() {
            @Override
            public void updateTopBoundary() {
                mActivity.setTaskbarWindowForAnimatingBubble();
            }
        };
    }

    private void onBubbleClicked(BubbleView bubbleView) {
        if (mBubbleBarPinning.isAnimating()) return;
        bubbleView.markSeen();
        BubbleBarItem bubble = bubbleView.getBubble();
        if (bubble == null) {
            Log.e(TAG, "bubble click listener, bubble was null");
        }

        final String currentlySelected = mBubbleBarController.getSelectedBubbleKey();
        if (mBarView.isExpanded() && Objects.equals(bubble.getKey(), currentlySelected)) {
            // Tapping the currently selected bubble while expanded collapses the view.
            collapseBubbleBar();
        } else {
            mBubbleBarController.showAndSelectBubble(bubble);
        }
    }

    /** Interrupts the running animation for a touch event on the bubble bar or flyout. */
    private void interruptAnimationForTouch() {
        mBubbleBarViewAnimator.interruptForTouch();
        mBubbleStashController.onNewBubbleAnimationInterrupted(false, mBarView.getTranslationY());
    }

    private void collapseBubbleBar() {
        setExpanded(false);
        mBubbleStashController.stashBubbleBar();
    }

    /** Notifies that the stash state is changing. */
    public void onStashStateChanging() {
        if (isAnimatingNewBubble()) {
            mBubbleBarViewAnimator.onStashStateChangingWhileAnimating();
        }
    }

    /** Shows the education view if it was previously requested. */
    private boolean maybeShowEduView() {
        if (mShouldShowEducation) {
            mShouldShowEducation = false;
            // Get the bubble bar bounds on screen
            Rect bounds = new Rect();
            mBarView.getBoundsOnScreen(bounds);
            // Calculate user education reference position in Screen coordinates
            Point position = new Point(bounds.centerX(), bounds.top);
            // Show user education relative to the reference point
            mSystemUiProxy.showUserEducation(position);
            return true;
        }
        return false;
    }

    /** Notifies that the IME became visible. */
    public void onImeVisible() {
        if (isAnimatingNewBubble()) {
            mBubbleBarViewAnimator.interruptForIme();
        }
    }

    //
    // The below animators are exposed to BubbleStashController so it can manage the stashing
    // animation.
    //

    public MultiPropertyFactory<View> getBubbleBarAlpha() {
        return mBubbleBarAlpha;
    }

    public AnimatedFloat getBubbleBarBubbleAlpha() {
        return mBubbleBarBubbleAlpha;
    }

    public AnimatedFloat getBubbleBarBackgroundAlpha() {
        return mBubbleBarBackgroundAlpha;
    }

    public AnimatedFloat getBubbleBarScaleX() {
        return mBubbleBarScaleX;
    }

    public AnimatedFloat getBubbleBarScaleY() {
        return mBubbleBarScaleY;
    }

    public AnimatedFloat getBubbleBarBackgroundScaleX() {
        return mBubbleBarBackgroundScaleX;
    }

    public AnimatedFloat getBubbleBarBackgroundScaleY() {
        return mBubbleBarBackgroundScaleY;
    }

    public AnimatedFloat getBubbleBarTranslationY() {
        return mBubbleBarTranslationY;
    }

    public AnimatedFloat getBubbleOffsetY() {
        return mBubbleOffsetY;
    }

    public float getBubbleBarCollapsedWidth() {
        return mBarView.collapsedWidth();
    }

    public float getBubbleBarCollapsedHeight() {
        return mBarView.getBubbleBarCollapsedHeight();
    }

    /** Returns the bubble bar arrow height.*/
    public float getBubbleBarArrowHeight() {
        return mBarView.getArrowHeight();
    }

    /**
     * @see BubbleBarView#getRelativePivotX()
     */
    public float getBubbleBarRelativePivotX() {
        return mBarView.getRelativePivotX();
    }

    /**
     * @see BubbleBarView#getRelativePivotY()
     */
    public float getBubbleBarRelativePivotY() {
        return mBarView.getRelativePivotY();
    }

    /**
     * @see BubbleBarView#setRelativePivot(float, float)
     */
    public void setBubbleBarRelativePivot(float x, float y) {
        mBarView.setRelativePivot(x, y);
    }

    /**
     * Whether the bubble bar is visible or not.
     */
    public boolean isBubbleBarVisible() {
        return mBarView.getVisibility() == VISIBLE;
    }

    /** Whether the bubble bar has bubbles. */
    public boolean hasBubbles() {
        return mBubbleBarController.getSelectedBubbleKey() != null;
    }

    /**
     * @return current {@link BubbleBarLocation}
     */
    public BubbleBarLocation getBubbleBarLocation() {
        return mBarView.getBubbleBarLocation();
    }

    /**
     * @return the max collapsed width for the bubble bar.
     */
    public float getCollapsedWidthWithMaxVisibleBubbles() {
        return mBarView.getCollapsedWidthWithMaxVisibleBubbles();
    }

    /**
     * @return {@code true} if bubble bar is on the left edge of the screen, {@code false} if on
     * the right
     */
    public boolean isBubbleBarOnLeft() {
        return mBarView.getBubbleBarLocation().isOnLeft(mBarView.isLayoutRtl());
    }

    /**
     * Update bar {@link BubbleBarLocation}
     */
    public void setBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        mBarView.setBubbleBarLocation(bubbleBarLocation);
    }

    /**
     * Animate bubble bar to the given location. The location change is transient. It does not
     * update the state of the bubble bar.
     * To update bubble bar pinned location, use {@link #setBubbleBarLocation(BubbleBarLocation)}.
     */
    public void animateBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        mBarView.animateToBubbleBarLocation(bubbleBarLocation);
    }

    /**
     * The bounds of the bubble bar.
     */
    public Rect getBubbleBarBounds() {
        return mBarView.getBubbleBarBounds();
    }

    /** Returns the bounds of the flyout view if it exists, or {@code null} otherwise. */
    @Nullable
    public Rect getFlyoutBounds() {
        return mBubbleBarFlyoutController.getFlyoutBounds();
    }

    /** Checks that bubble bar is visible and that the motion event is within bounds. */
    public boolean isEventOverBubbleBar(MotionEvent event) {
        if (!isBubbleBarVisible()) return false;
        final Rect bounds = getBubbleBarBounds();
        final int bubbleBarTopOnScreen = mBarView.getRestingTopPositionOnScreen();
        final float x = event.getX();
        return event.getRawY() >= bubbleBarTopOnScreen && x >= bounds.left && x <= bounds.right;
    }

    /** Whether a new bubble is animating. */
    public boolean isAnimatingNewBubble() {
        return mBubbleBarViewAnimator != null && mBubbleBarViewAnimator.isAnimating();
    }

    public boolean isNewBubbleAnimationRunningOrPending() {
        return mBubbleBarViewAnimator != null && mBubbleBarViewAnimator.hasAnimation();
    }

    /** The horizontal margin of the bubble bar from the edge of the screen. */
    public int getHorizontalMargin() {
        return mBarView.getHorizontalMargin();
    }

    /**
     * When the bubble bar is not stashed, it can be collapsed (the icons are in a stack) or
     * expanded (the icons are in a row). This indicates whether the bubble bar is expanded.
     */
    public boolean isExpanded() {
        return mBarView.isExpanded();
    }

    /**
     * Whether the motion event is within the bounds of the bubble bar.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        return mBarView.isEventOverAnyItem(ev);
    }

    //
    // Visibility of the bubble bar
    //

    /**
     * Returns whether the bubble bar is hidden because there are no bubbles.
     */
    public boolean isHiddenForNoBubbles() {
        return mHiddenForNoBubbles;
    }

    /** Returns maximum height of the bubble bar with the flyout view. */
    public int getBubbleBarWithFlyoutMaximumHeight() {
        if (!isBubbleBarVisible() && !isAnimatingNewBubble()) return 0;
        int bubbleBarTopOnHome = (int) (mBubbleStashController.getBubbleBarVerticalCenterForHome()
                + mBarView.getBubbleBarCollapsedHeight() / 2 + mBarView.getArrowHeight());
        if (isAnimatingNewBubble()) {
            if (mTaskbarStashController.isInApp() && mBubbleStashController.getHasHandleView()) {
                // when animating a bubble in an app, the bubble bar will be higher than its
                // position on home
                float bubbleBarTopDistanceFromBottom =
                        -mBubbleStashController.getBubbleBarTranslationYForTaskbar()
                                + mBarView.getHeight();
                return (int) bubbleBarTopDistanceFromBottom
                        + mBubbleBarFlyoutController.getMaximumFlyoutHeight();
            }
            return bubbleBarTopOnHome + mBubbleBarFlyoutController.getMaximumFlyoutHeight();
        } else {
            return bubbleBarTopOnHome;
        }
    }

    /**
     * Sets whether the bubble bar should be hidden because there are no bubbles.
     */
    public void setHiddenForBubbles(boolean hidden) {
        if (mHiddenForNoBubbles != hidden) {
            mHiddenForNoBubbles = hidden;
            if (hidden) {
                mBarView.dismiss(() -> {
                    updateVisibilityForStateChange();
                    mBarView.setExpanded(false);
                    adjustTaskbarAndHotseatToBubbleBarState(/* isBubbleBarExpanded= */ false);
                    mActivity.bubbleBarVisibilityChanged(/* isVisible= */ false);
                });
            } else {
                updateVisibilityForStateChange();
                mActivity.bubbleBarVisibilityChanged(/* isVisible= */ true);
            }
        }
    }

    /** Sets a callback that updates the selected bubble after the bubble bar collapses. */
    public void setUpdateSelectedBubbleAfterCollapse(
            Consumer<String> updateSelectedBubbleAfterCollapse) {
        mBarView.setUpdateSelectedBubbleAfterCollapse(updateSelectedBubbleAfterCollapse);
    }

    /** Returns whether the bubble bar should be hidden because of the current sysui state. */
    boolean isHiddenForSysui() {
        return mHiddenForSysui;
    }

    /**
     * Sets whether the bubble bar should be hidden due to SysUI state (e.g. on lockscreen).
     */
    public void setHiddenForSysui(boolean hidden) {
        if (mHiddenForSysui != hidden) {
            mHiddenForSysui = hidden;
            updateVisibilityForStateChange();
        }
    }

    /** Sets whether the bubble bar should be hidden due to stashed state */
    public void setHiddenForStashed(boolean hidden) {
        if (mHiddenForStashed != hidden) {
            mHiddenForStashed = hidden;
            updateVisibilityForStateChange();
        }
    }

    private void updateVisibilityForStateChange() {
        if (!mHiddenForSysui && !mHiddenForNoBubbles && !mHiddenForStashed) {
            mBarView.setVisibility(VISIBLE);
        } else {
            mBarView.setVisibility(INVISIBLE);
        }
    }

    //
    // Modifying view related properties.
    //

    /** Notifies controller of configuration change, so bubble bar can be adjusted */
    public void onBubbleBarConfigurationChanged(boolean animate) {
        int newIconSize;
        int newPadding;
        Resources res = mActivity.getResources();
        if (mBubbleStashController.isBubblesShowingOnHome()
                || mBubbleStashController.isTransientTaskBar()) {
            newIconSize = getBubbleBarIconSizeFromDeviceProfile(res);
            newPadding = getBubbleBarPaddingFromDeviceProfile(res);
        } else {
            // the bubble bar is shown inside the persistent task bar, use preset sizes
            newIconSize = res.getDimensionPixelSize(R.dimen.bubblebar_icon_size_persistent_taskbar);
            newPadding = res.getDimensionPixelSize(
                    R.dimen.bubblebar_icon_spacing_persistent_taskbar);
        }
        updateBubbleBarIconSizeAndPadding(newIconSize, newPadding, animate);
    }

    private int getBubbleBarIconSizeFromDeviceProfile(Resources res) {
        return getBubbleBarIconSizeFromDeviceProfile(res, mActivity.getDeviceProfile());
    }

    private int getBubbleBarIconSizeFromDeviceProfile(Resources res, DeviceProfile deviceProfile) {
        DisplayMetrics dm = res.getDisplayMetrics();
        float smallIconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                APP_ICON_SMALL_DP, dm);
        float mediumIconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                APP_ICON_MEDIUM_DP, dm);
        float smallMediumThreshold = (smallIconSize + mediumIconSize) / 2f;
        int taskbarIconSize = deviceProfile.taskbarIconSize;
        return taskbarIconSize <= smallMediumThreshold
                ? res.getDimensionPixelSize(R.dimen.bubblebar_icon_size_small) :
                res.getDimensionPixelSize(R.dimen.bubblebar_icon_size);

    }

    private int getBubbleBarPaddingFromDeviceProfile(Resources res) {
        return getBubbleBarPaddingFromDeviceProfile(res, mActivity.getDeviceProfile());
    }

    private int getBubbleBarPaddingFromDeviceProfile(Resources res, DeviceProfile deviceProfile) {
        DisplayMetrics dm = res.getDisplayMetrics();
        float mediumIconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                APP_ICON_MEDIUM_DP, dm);
        float largeIconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                APP_ICON_LARGE_DP, dm);
        float mediumLargeThreshold = (mediumIconSize + largeIconSize) / 2f;
        return deviceProfile.taskbarIconSize >= mediumLargeThreshold
                ? res.getDimensionPixelSize(R.dimen.bubblebar_icon_spacing_large) :
                res.getDimensionPixelSize(R.dimen.bubblebar_icon_spacing);
    }

    private void updateBubbleBarIconSizeAndPadding(int iconSize, int padding, boolean animate) {
        if (mIconSize == iconSize && mBubbleBarPadding == padding) return;
        mIconSize = iconSize;
        mBubbleBarPadding = padding;
        if (animate) {
            mBarView.animateBubbleBarIconSize(iconSize, padding);
        } else {
            mBarView.setIconSizeAndPadding(iconSize, padding);
        }
    }

    /**
     * Sets the translation of the bubble bar during the swipe up gesture.
     */
    public void setTranslationYForSwipe(float transY) {
        mBubbleBarSwipeUpTranslationY = transY;
        updateTranslationY();
    }

    /**
     * Sets the translation of the bubble bar during the stash animation.
     */
    public void setTranslationYForStash(float transY) {
        mBubbleBarStashTranslationY = transY;
        updateTranslationY();
    }

    private void updateTranslationY() {
        mBarView.setTranslationY(mBubbleBarTranslationY.value + mBubbleBarSwipeUpTranslationY
                + mBubbleBarStashTranslationY + getBubbleBarTranslationYForTaskbarPinning());
    }

    /** Computes translation y for taskbar pinning. */
    private float getBubbleBarTranslationYForTaskbarPinning() {
        if (mTaskbarSharedState == null) return 0f;
        float pinningProgress = mBubbleBarPinning.value;
        if (mTaskbarSharedState.startTaskbarVariantIsTransient) {
            return mapRange(pinningProgress, /* min = */ 0f, mTaskbarTranslationDelta);
        } else {
            return mapRange(pinningProgress, -mTaskbarTranslationDelta, /* max = */ 0f);
        }
    }

    private void setBubbleBarScaleAndPadding(float pinningProgress) {
        Resources res = mActivity.getResources();
        // determine icon scale for pinning
        int persistentIconSize = res.getDimensionPixelSize(
                R.dimen.bubblebar_icon_size_persistent_taskbar);
        int transientIconSize = getBubbleBarIconSizeFromDeviceProfile(res,
                mActivity.getTransientTaskbarDeviceProfile());
        float pinningIconSize = mapRange(pinningProgress, transientIconSize, persistentIconSize);

        // determine bubble bar padding for pinning
        int persistentPadding = res.getDimensionPixelSize(
                R.dimen.bubblebar_icon_spacing_persistent_taskbar);
        int transientPadding = getBubbleBarPaddingFromDeviceProfile(res,
                mActivity.getTransientTaskbarDeviceProfile());
        float pinningPadding = mapRange(pinningProgress, transientPadding, persistentPadding);
        mBarView.setIconSizeAndPaddingForPinning(pinningIconSize, pinningPadding);
    }

    /**
     * Calculates the vertical difference in the bubble bar positions for pinned and transient
     * taskbar modes.
     */
    private int getBubbleBarTranslationDeltaForTaskbar(TaskbarActivityContext activity) {
        Resources res = activity.getResources();
        int persistentBubbleSize = res
                .getDimensionPixelSize(R.dimen.bubblebar_icon_size_persistent_taskbar);
        int persistentSpacingSize = res
                .getDimensionPixelSize(R.dimen.bubblebar_icon_spacing_persistent_taskbar);
        int persistentBubbleBarSize = persistentBubbleSize + persistentSpacingSize * 2;
        int persistentTaskbarHeight = activity.getPersistentTaskbarDeviceProfile().taskbarHeight;
        int persistentBubbleBarY = (persistentTaskbarHeight - persistentBubbleBarSize) / 2;
        int transientBubbleBarY = activity.getTransientTaskbarDeviceProfile().taskbarBottomMargin;
        return transientBubbleBarY - persistentBubbleBarY;
    }

    private void updateScaleX(float scale) {
        mBarView.setScaleX(scale);
    }

    private void updateScaleY(float scale) {
        mBarView.setScaleY(scale);
    }

    private void updateBackgroundScaleX(float scale) {
        mBarView.setBackgroundScaleX(scale);
    }

    private void updateBackgroundScaleY(float scale) {
        mBarView.setBackgroundScaleY(scale);
    }

    private void updateBubbleAlpha(float alpha) {
        mBarView.setBubbleAlpha(alpha);
    }

    private void updateBubbleOffsetY(float transY) {
        mBarView.setBubbleOffsetY(transY);
    }

    private void updateBackgroundAlpha(float alpha) {
        mBarView.setBackgroundAlpha(alpha);
    }

    //
    // Manipulating the specific bubble views in the bar
    //

    /**
     * Removes the provided bubble from the bubble bar.
     */
    public void removeBubble(BubbleBarBubble b) {
        if (b != null) {
            mBarView.removeBubble(b.getView());
            b.getView().setController(null);
        } else {
            Log.w(TAG, "removeBubble, bubble was null!");
        }
    }

    /** Adds a new bubble and removes an old bubble at the same time. */
    public void addBubbleAndRemoveBubble(BubbleBarBubble addedBubble,
            BubbleBarBubble removedBubble, boolean isExpanding, boolean suppressAnimation,
            boolean addOverflowToo) {
        mBarView.addBubbleAndRemoveBubble(addedBubble.getView(), removedBubble.getView(),
                addOverflowToo ? () -> showOverflow(true) : null);
        addedBubble.getView().setOnClickListener(mBubbleClickListener);
        addedBubble.getView().setController(mBubbleViewController);
        removedBubble.getView().setController(null);
        mBubbleDragController.setupBubbleView(addedBubble.getView());
        if (!suppressAnimation) {
            animateBubbleNotification(addedBubble, isExpanding, /* isUpdate= */ false);
        }
    }

    /** Whether the overflow view is added to the bubble bar. */
    public boolean isOverflowAdded() {
        return mOverflowAdded;
    }

    /** Shows or hides the overflow view. */
    public void showOverflow(boolean showOverflow) {
        if (mOverflowAdded == showOverflow) return;
        mOverflowAdded = showOverflow;
        if (mOverflowAdded) {
            mBarView.addBubble(mOverflowBubble.getView());
            mOverflowBubble.getView().setOnClickListener(mBubbleClickListener);
            mOverflowBubble.getView().setController(mBubbleViewController);
        } else {
            mBarView.removeBubble(mOverflowBubble.getView());
            mOverflowBubble.getView().setOnClickListener(null);
            mOverflowBubble.getView().setController(null);
        }
    }

    /** Adds the overflow view to the bubble bar while animating a view away. */
    public void addOverflowAndRemoveBubble(BubbleBarBubble removedBubble) {
        if (mOverflowAdded) return;
        mOverflowAdded = true;
        mBarView.addBubbleAndRemoveBubble(mOverflowBubble.getView(), removedBubble.getView(),
                null /* onEndRunnable */);
        mOverflowBubble.getView().setOnClickListener(mBubbleClickListener);
        mOverflowBubble.getView().setController(mBubbleViewController);
        removedBubble.getView().setController(null);
    }

    /** Removes the overflow view to the bubble bar while animating a view in. */
    public void removeOverflowAndAddBubble(BubbleBarBubble addedBubble) {
        if (!mOverflowAdded) return;
        mOverflowAdded = false;
        mBarView.addBubbleAndRemoveBubble(addedBubble.getView(), mOverflowBubble.getView(),
                null /* onEndRunnable */);
        addedBubble.getView().setOnClickListener(mBubbleClickListener);
        addedBubble.getView().setController(mBubbleViewController);
        mOverflowBubble.getView().setController(null);
    }

    /**
     * Adds the provided bubble to the bubble bar.
     */
    public void addBubble(BubbleBarItem b, boolean isExpanding, boolean suppressAnimation) {
        if (b != null) {
            mBarView.addBubble(b.getView());
            b.getView().setOnClickListener(mBubbleClickListener);
            mBubbleDragController.setupBubbleView(b.getView());
            b.getView().setController(mBubbleViewController);

            if (suppressAnimation || !(b instanceof BubbleBarBubble bubble)) {
                // the bubble bar and handle are initialized as part of the first bubble animation.
                // if the animation is suppressed, immediately stash or show the bubble bar to
                // ensure they've been initialized.
                if (mTaskbarStashController.isInApp()
                        && mBubbleStashController.isTransientTaskBar()
                        && mTaskbarStashController.isStashed()) {
                    mBubbleStashController.stashBubbleBarImmediate();
                } else {
                    mBubbleStashController.showBubbleBarImmediate();
                }
                return;
            }
            animateBubbleNotification(bubble, isExpanding, /* isUpdate= */ false);
        } else {
            Log.w(TAG, "addBubble, bubble was null!");
        }
    }

    /** Animates the bubble bar to notify the user about a bubble change. */
    public void animateBubbleNotification(BubbleBarBubble bubble, boolean isExpanding,
            boolean isUpdate) {
        // if we're not already animating another bubble, update the dot visibility. otherwise the
        // the dot will be handled as part of the animation.
        if (!mBubbleBarViewAnimator.isAnimating()) {
            bubble.getView().updateDotVisibility(
                    /* animate= */ !mBubbleStashController.isStashed());
        }
        // if we're expanded, don't animate the bubble bar.
        if (isExpanded()) {
            return;
        }
        boolean isInApp = mTaskbarStashController.isInApp();
        // if this is the first bubble, animate to the initial state.
        if (mBarView.getBubbleChildCount() == 1 && !isUpdate) {
            mBubbleBarViewAnimator.animateToInitialState(bubble, isInApp, isExpanding);
            return;
        }
        // if we're not stashed or we're in persistent taskbar, animate for collapsed state.
        boolean animateForCollapsed = !mBubbleStashController.isStashed()
                || !mBubbleStashController.isTransientTaskBar();
        if (animateForCollapsed) {
            mBubbleBarViewAnimator.animateBubbleBarForCollapsed(bubble, isExpanding);
            return;
        }

        if (isInApp && mBubbleStashController.getHasHandleView()) {
            mBubbleBarViewAnimator.animateBubbleInForStashed(bubble, isExpanding);
        }
    }

    /**
     * Reorders the bubbles based on the provided list.
     */
    public void reorderBubbles(List<BubbleBarBubble> newOrder) {
        List<BubbleView> viewList = newOrder.stream().filter(Objects::nonNull)
                .map(BubbleBarBubble::getView).toList();
        mBarView.reorder(viewList);
    }

    /**
     * Updates the selected bubble.
     */
    public void updateSelectedBubble(BubbleBarItem newlySelected) {
        mBarView.setSelectedBubble(newlySelected.getView());
    }

    /** @see #setExpanded(boolean, boolean) */
    public void setExpanded(boolean isExpanded) {
        setExpanded(isExpanded, /* maybeShowEdu= */ false);
    }

    /**
     * Sets whether the bubble bar should be expanded (not unstashed, but have the contents
     * within it expanded). This method notifies SystemUI that the bubble bar is expanded and
     * showing a selected bubble. This method should ONLY be called from UI events originating
     * from Launcher.
     *
     * @param isExpanded whether the bar should be expanded
     * @param maybeShowEdu whether we should show the edu view before expanding
     */
    public void setExpanded(boolean isExpanded, boolean maybeShowEdu) {
        // if we're trying to expand try showing the edu view instead
        if (maybeShowEdu && isExpanded && !mBarView.isExpanded() && maybeShowEduView()) {
            return;
        }
        if (!mBubbleBarPinning.isAnimating() && isExpanded != mBarView.isExpanded()) {
            mBarView.setExpanded(isExpanded);
            adjustTaskbarAndHotseatToBubbleBarState(isExpanded);
            if (!isExpanded) {
                mSystemUiProxy.collapseBubbles();
            } else {
                mBubbleBarController.showSelectedBubble();
                mTaskbarStashController.updateAndAnimateTransientTaskbar(true /* stash */,
                        false /* shouldBubblesFollow */);
            }
        }
    }

    /**
     * Hides the persistent taskbar if it is going to intersect with the expanded bubble bar if in
     * app or overview.
     */
    private void adjustTaskbarAndHotseatToBubbleBarState(boolean isBubbleBarExpanded) {
        if (!mBubbleStashController.isBubblesShowingOnHome()
                && !mBubbleStashController.isTransientTaskBar()) {
            boolean hideTaskbar = isBubbleBarExpanded && isIntersectingTaskbar();
            Animator taskbarAlphaAnimator = mTaskbarViewPropertiesProvider.getIconsAlpha()
                    .animateToValue(hideTaskbar ? 0 : 1);
            taskbarAlphaAnimator.setDuration(hideTaskbar
                    ? TASKBAR_FADE_OUT_DURATION_MS : TASKBAR_FADE_IN_DURATION_MS);
            if (!hideTaskbar) {
                taskbarAlphaAnimator.setStartDelay(TASKBAR_FADE_IN_DELAY_MS);
            }
            taskbarAlphaAnimator.setInterpolator(Interpolators.LINEAR);
            taskbarAlphaAnimator.start();
        }
    }

    /** Return {@code true} if expanded bubble bar would intersect the taskbar. */
    public boolean isIntersectingTaskbar() {
        if (mBarView.isExpanding() || mBarView.isExpanded()) {
            Rect taskbarViewBounds = mTaskbarViewPropertiesProvider.getTaskbarViewBounds();
            return mBarView.getBubbleBarExpandedBounds().intersect(taskbarViewBounds);
        } else {
            return false;
        }
    }

    /**
     * Sets whether the bubble bar should be expanded. This method is used in response to UI events
     * from SystemUI.
     */
    public void setExpandedFromSysui(boolean isExpanded) {
        if (isNewBubbleAnimationRunningOrPending() && isExpanded) {
            mBubbleBarViewAnimator.expandedWhileAnimating();
            return;
        }
        if (!isExpanded) {
            mBubbleStashController.stashBubbleBar();
        } else {
            mBubbleStashController.showBubbleBar(true /* expand the bubbles */);
        }
    }

    /**
     * Stores a request to show the education view for later processing when appropriate.
     *
     * @see #maybeShowEduView()
     */
    public void prepareToShowEducation() {
        mShouldShowEducation = true;
    }

    /**
     * Updates the dragged bubble view in the bubble bar view, and notifies SystemUI
     * that a bubble is being dragged to dismiss.
     *
     * @param bubbleView dragged bubble view
     */
    public void onBubbleDragStart(@NonNull BubbleView bubbleView) {
        if (bubbleView.getBubble() == null) return;

        mSystemUiProxy.startBubbleDrag(bubbleView.getBubble().getKey());
        mBarView.setDraggedBubble(bubbleView);
    }

    /**
     * Notifies SystemUI to expand the selected bubble when the bubble is released.
     */
    public void onBubbleDragRelease(BubbleBarLocation location) {
        mSystemUiProxy.stopBubbleDrag(location, mBarView.getRestingTopPositionOnScreen());
    }

    /** Handle given bubble being dismissed */
    public void onBubbleDismissed(BubbleView bubble) {
        mBubbleBarController.onBubbleDismissed(bubble);
        mBarView.removeBubble(bubble);
    }

    /**
     * Notifies {@link BubbleBarView} that drag and all animations are finished.
     */
    public void onBubbleDragEnd() {
        mBarView.setDraggedBubble(null);
    }

    /** Notifies that dragging the bubble bar ended. */
    public void onBubbleBarDragEnd() {
        // we may have changed the bubble bar translation Y value from the value it had at the
        // beginning of the drag, so update the translation Y animator state
        mBubbleBarTranslationY.updateValue(mBarView.getTranslationY());
    }

    /**
     * Get translation for bubble bar when drag is released.
     *
     * @see BubbleBarView#getBubbleBarDragReleaseTranslation(PointF, BubbleBarLocation)
     */
    public PointF getBubbleBarDragReleaseTranslation(PointF initialTranslation,
            BubbleBarLocation location) {
        return mBarView.getBubbleBarDragReleaseTranslation(initialTranslation, location);
    }

    /**
     * Get translation for bubble view when drag is released.
     *
     * @see BubbleBarView#getDraggedBubbleReleaseTranslation(PointF, BubbleBarLocation)
     */
    public PointF getDraggedBubbleReleaseTranslation(PointF initialTranslation,
            BubbleBarLocation location) {
        if (location == mBarView.getBubbleBarLocation()) {
            return initialTranslation;
        }
        return mBarView.getDraggedBubbleReleaseTranslation(initialTranslation, location);
    }

    /**
     * Notify SystemUI that the given bubble has been dismissed.
     */
    public void notifySysUiBubbleDismissed(@NonNull BubbleBarItem bubble) {
        mSystemUiProxy.dragBubbleToDismiss(bubble.getKey(), mTimeSource.currentTimeMillis());
    }

    /**
     * Called when bubble stack was dismissed
     */
    public void onDismissAllBubbles() {
        mSystemUiProxy.removeAllBubbles();
    }

    /** Removes all existing bubble views */
    public void removeAllBubbles() {
        mBarView.removeAllViews();
    }

    /** Returns the view index of the existing bubble */
    public int bubbleViewIndex(View bubbleView) {
        return mBarView.indexOfChild(bubbleView);
    }

    /**
     * Set listener to be notified when bubble bar bounds have changed
     */
    public void setBoundsChangeListener(@Nullable BubbleBarBoundsChangeListener listener) {
        mBoundsChangeListener = listener;
    }

    /** Called when the controller is destroyed. */
    public void onDestroy() {
        adjustTaskbarAndHotseatToBubbleBarState(/*isBubbleBarExpanded = */false);
    }

    /**
     * Create an animator for showing or hiding bubbles when stashed state changes
     *
     * @param isStashed {@code true} when bubble bar should be stashed to the handle
     */
    public Animator createRevealAnimatorForStashChange(boolean isStashed) {
        Rect stashedHandleBounds = new Rect();
        mBubbleStashController.getHandleBounds(stashedHandleBounds);
        int childCount = mBarView.getChildCount();
        float newChildWidth = (float) stashedHandleBounds.width() / childCount;
        AnimatorSet animatorSet = new AnimatorSet();
        for (int i = 0; i < childCount; i++) {
            BubbleView child = (BubbleView) mBarView.getChildAt(i);
            animatorSet.play(
                    createRevealAnimForBubble(child, isStashed, stashedHandleBounds,
                            newChildWidth));
        }
        return animatorSet;
    }

    private Animator createRevealAnimForBubble(BubbleView bubbleView, boolean isStashed,
            Rect stashedHandleBounds, float newWidth) {
        Rect viewBounds = new Rect(0, 0, bubbleView.getWidth(), bubbleView.getHeight());

        int viewCenterY = viewBounds.centerY();
        int halfHandleHeight = stashedHandleBounds.height() / 2;
        int widthDelta = Math.max(0, (int) (viewBounds.width() - newWidth) / 2);

        Rect stashedViewBounds = new Rect(
                viewBounds.left + widthDelta,
                viewCenterY - halfHandleHeight,
                viewBounds.right - widthDelta,
                viewCenterY + halfHandleHeight
        );

        float viewRadius = 0f; // Use 0 to not clip the new message dot or the app icon
        float stashedRadius = stashedViewBounds.height() / 2f;

        return new RoundedRectRevealOutlineProvider(viewRadius, stashedRadius, viewBounds,
                stashedViewBounds).createRevealAnimator(bubbleView, !isStashed, 0);
    }

    /**
     * Listener to receive updates about bubble bar bounds changing
     */
    public interface BubbleBarBoundsChangeListener {
        /** Called when bounds have changed */
        void onBoundsChanged();
    }

    /** Interface for getting the current timestamp. */
    interface TimeSource {
        long currentTimeMillis();
    }

    /** Dumps the state of BubbleBarViewController. */
    public void dump(PrintWriter pw) {
        pw.println("Bubble bar view controller state:");
        pw.println("  mHiddenForSysui: " + mHiddenForSysui);
        pw.println("  mHiddenForNoBubbles: " + mHiddenForNoBubbles);
        pw.println("  mShouldShowEducation: " + mShouldShowEducation);
        pw.println("  mBubbleBarTranslationY.value: " + mBubbleBarTranslationY.value);
        pw.println("  mBubbleBarSwipeUpTranslationY: " + mBubbleBarSwipeUpTranslationY);
        pw.println("  mOverflowAdded: " + mOverflowAdded);
        if (mBarView != null) {
            mBarView.dump(pw);
        } else {
            pw.println("  Bubble bar view is null!");
        }
    }

    /** Interface for BubbleBarViewController to get the taskbar view properties. */
    public interface TaskbarViewPropertiesProvider {

        /** Returns the bounds of the taskbar. */
        Rect getTaskbarViewBounds();

        /** Returns taskbar icons alpha */
        MultiPropertyFactory<View>.MultiProperty getIconsAlpha();
    }
}
