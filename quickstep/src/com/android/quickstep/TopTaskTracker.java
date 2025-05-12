/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.content.Intent.ACTION_CHOOSER;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_A;
import static com.android.wm.shell.Flags.enableFlexibleSplit;
import static com.android.wm.shell.shared.GroupedTaskInfo.TYPE_SPLIT;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.TaskInfo;
import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitStageInfo;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.SplitConfigurationOptions.StageType;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.splitscreen.ISplitScreenListener;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This class tracked the top-most task and  some 'approximate' task history to allow faster
 * system state estimation during touch interaction
 */
public class TopTaskTracker extends ISplitScreenListener.Stub
        implements TaskStackChangeListener, SafeCloseable {
    private static final String TAG = "TopTaskTracker";
    public static MainThreadInitializedObject<TopTaskTracker> INSTANCE =
            new MainThreadInitializedObject<>(TopTaskTracker::new);

    private static final int HISTORY_SIZE = 5;

    private final Context mContext;

    // Only used when Flags.enableShellTopTaskTracking() is disabled
    // Ordered list with first item being the most recent task.
    private final LinkedList<TaskInfo> mOrderedTaskList = new LinkedList<>();
    private final SplitStageInfo mMainStagePosition = new SplitStageInfo();
    private final SplitStageInfo mSideStagePosition = new SplitStageInfo();
    private int mPinnedTaskId = INVALID_TASK_ID;

    // Only used when Flags.enableShellTopTaskTracking() is enabled
    // Mapping of display id to running tasks.  Running tasks are ordered from top most to
    // bottom most.
    private ArrayMap<Integer, ArrayList<GroupedTaskInfo>> mVisibleTasks = new ArrayMap<>();

    private TopTaskTracker(Context context) {
        mContext = context;

        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            // Just prepopulate a list for the default display tasks so we don't need to add null
            // checks everywhere
            mVisibleTasks.put(DEFAULT_DISPLAY, new ArrayList<>());
        } else {
            mMainStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_MAIN;
            mSideStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_SIDE;

            TaskStackChangeListeners.getInstance().registerTaskStackListener(this);
            SystemUiProxy.INSTANCE.get(context).registerSplitScreenListener(this);
        }
    }

    @Override
    public void close() {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(this);
        SystemUiProxy.INSTANCE.get(mContext).unregisterSplitScreenListener(this);
    }

    @Override
    public void onTaskRemoved(int taskId) {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        mOrderedTaskList.removeIf(rto -> rto.taskId == taskId);
    }

    @Override
    public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
        handleTaskMovedToFront(taskInfo);
    }

    void handleTaskMovedToFront(TaskInfo taskInfo) {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        mOrderedTaskList.removeIf(rto -> rto.taskId == taskInfo.taskId);
        mOrderedTaskList.addFirst(taskInfo);

        // Workaround for b/372067617, if the home task is being brought to front, then it will
        // occlude all other tasks, so mark them as not-visible
        if (taskInfo.getActivityType() == ACTIVITY_TYPE_HOME) {
            // We've moved the task to the front of the list above, so only iterate the tasks after
            for (int i = 1; i < mOrderedTaskList.size(); i++) {
                final TaskInfo info = mOrderedTaskList.get(i);
                if (info.displayId != taskInfo.displayId) {
                    // Only fall through to reset visibility for tasks on the same display as the
                    // home task being brought forward
                    continue;
                }
                info.isVisible = false;
                info.isVisibleRequested = false;
            }
        }

        // Keep the home display's top running task in the first while adding a non-home
        // display's task to the list, to avoid showing non-home display's task upon going to
        // Recents animation.
        if (taskInfo.displayId != DEFAULT_DISPLAY) {
            final TaskInfo topTaskOnHomeDisplay = mOrderedTaskList.stream()
                    .filter(rto -> rto.displayId == DEFAULT_DISPLAY).findFirst().orElse(null);
            if (topTaskOnHomeDisplay != null) {
                mOrderedTaskList.removeIf(rto -> rto.taskId == topTaskOnHomeDisplay.taskId);
                mOrderedTaskList.addFirst(topTaskOnHomeDisplay);
            }
        }

        if (mOrderedTaskList.size() >= HISTORY_SIZE) {
            // If we grow in size, remove the last taskInfo which is not part of the split task.
            Iterator<TaskInfo> itr = mOrderedTaskList.descendingIterator();
            while (itr.hasNext()) {
                TaskInfo info = itr.next();
                if (info.taskId != taskInfo.taskId
                        && info.taskId != mMainStagePosition.taskId
                        && info.taskId != mSideStagePosition.taskId) {
                    itr.remove();
                    return;
                }
            }
        }
    }

    /**
     * Called when the set of visible tasks have changed.
     */
    public void onVisibleTasksChanged(GroupedTaskInfo[] visibleTasks) {
        if (!com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        // TODO(346588978): Per-display info, just have everything in order by display

        // Clear existing tasks for each display
        mVisibleTasks.forEach((displayId, visibleTasksOnDisplay) -> visibleTasksOnDisplay.clear());

        // Update the visible tasks on each display
        for (int i = 0; i < visibleTasks.length; i++) {
            final int displayId = visibleTasks[i].getTaskInfo1().getDisplayId();
            final ArrayList<GroupedTaskInfo> displayTasks;
            if (mVisibleTasks.containsKey(displayId)) {
                displayTasks = mVisibleTasks.get(displayId);
            } else {
                displayTasks = new ArrayList<>();
                mVisibleTasks.put(displayId, displayTasks);
            }
            displayTasks.add(visibleTasks[i]);
        }
    }

    @Override
    public void onStagePositionChanged(@StageType int stage, @StagePosition int position) {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        if (stage == SplitConfigurationOptions.STAGE_TYPE_MAIN) {
            mMainStagePosition.stagePosition = position;
        } else {
            mSideStagePosition.stagePosition = position;
        }
    }

    public void onTaskChanged(RunningTaskInfo taskInfo) {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        for (int i = 0; i < mOrderedTaskList.size(); i++) {
            if (mOrderedTaskList.get(i).taskId == taskInfo.taskId) {
                mOrderedTaskList.set(i, taskInfo);
                break;
            }
        }
    }

    @Override
    public void onTaskStageChanged(int taskId, @StageType int stage, boolean visible) {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        // If a task is not visible anymore or has been moved to undefined, stop tracking it.
        if (!visible || stage == SplitConfigurationOptions.STAGE_TYPE_UNDEFINED) {
            if (mMainStagePosition.taskId == taskId) {
                mMainStagePosition.taskId = INVALID_TASK_ID;
            } else if (mSideStagePosition.taskId == taskId) {
                mSideStagePosition.taskId = INVALID_TASK_ID;
            } // else it's an un-tracked child
            return;
        }

        if (stage == SplitConfigurationOptions.STAGE_TYPE_MAIN
                || (enableFlexibleSplit() && stage == STAGE_TYPE_A)) {
            mMainStagePosition.taskId = taskId;
        } else {
            mSideStagePosition.taskId = taskId;
        }
    }

    @Override
    public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        mPinnedTaskId = taskId;
    }

    @Override
    public void onActivityUnpinned() {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        mPinnedTaskId = INVALID_TASK_ID;
    }

    /**
     * @return index 0 will be task in left/top position, index 1 in right/bottom position.
     * Will return empty array if device is not in staged split
     */
    public int[] getRunningSplitTaskIds() {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            // TODO(346588978): This assumes default display for now
            final ArrayList<GroupedTaskInfo> visibleTasks = mVisibleTasks.get(DEFAULT_DISPLAY);
            final GroupedTaskInfo splitTaskInfo = visibleTasks.stream()
                    .filter(taskInfo -> taskInfo.getType() == TYPE_SPLIT)
                    .findFirst().orElse(null);
            if (splitTaskInfo != null && splitTaskInfo.getSplitBounds() != null) {
                return new int[] {
                        splitTaskInfo.getSplitBounds().leftTopTaskId,
                        splitTaskInfo.getSplitBounds().rightBottomTaskId
                };
            }
            return new int[0];
        } else {
            if (mMainStagePosition.taskId == INVALID_TASK_ID
                    || mSideStagePosition.taskId == INVALID_TASK_ID) {
                return new int[]{};
            }
            int[] out = new int[2];
            if (mMainStagePosition.stagePosition == STAGE_POSITION_TOP_OR_LEFT) {
                out[0] = mMainStagePosition.taskId;
                out[1] = mSideStagePosition.taskId;
            } else {
                out[1] = mMainStagePosition.taskId;
                out[0] = mSideStagePosition.taskId;
            }
            return out;
        }
    }

    /**
     * Dumps the list of tasks in top task tracker.
     */
    public void dump(PrintWriter pw) {
        if (!com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            return;
        }

        // TODO(346588978): This assumes default display for now
        final ArrayList<GroupedTaskInfo> displayTasks = mVisibleTasks.get(DEFAULT_DISPLAY);
        pw.println("TopTaskTracker:");
        pw.println("  tasks: [");
        for (GroupedTaskInfo taskInfo : displayTasks) {
            final TaskInfo info = taskInfo.getTaskInfo1();
            final boolean isExcluded = (info.baseIntent.getFlags()
                    & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0;
            pw.println("    " + info.taskId + ": excluded=" + isExcluded
                    + " visibleRequested=" + info.isVisibleRequested
                    + " visible=" + info.isVisible
                    + " " + info.baseIntent.getComponent());
        }
        pw.println("  ]");
    }

    /**
     * Returns the CachedTaskInfo for the top most task
     */
    @NonNull
    @UiThread
    public CachedTaskInfo getCachedTopTask(boolean filterOnlyVisibleRecents) {
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            // TODO(346588978): Currently ignore filterOnlyVisibleRecents, but perhaps make this an
            //  explicit filter For things to ignore (ie. PIP/Bubbles/Assistant/etc/so that this is
            //  explicit)
            // TODO(346588978): This assumes default display for now (as does all of Launcher)
            final ArrayList<GroupedTaskInfo> displayTasks = mVisibleTasks.get(DEFAULT_DISPLAY);
            return new CachedTaskInfo(new ArrayList<>(displayTasks));
        } else {
            if (filterOnlyVisibleRecents) {
                // Since we only know about the top most task, any filtering may not be applied on
                // the cache. The second to top task may change while the top task is still the
                // same.
                RunningTaskInfo[] tasks = TraceHelper.allowIpcs("getCachedTopTask.true", () ->
                        ActivityManagerWrapper.getInstance().getRunningTasks(true));
                return new CachedTaskInfo(Arrays.asList(tasks));
            }

            if (mOrderedTaskList.isEmpty()) {
                RunningTaskInfo[] tasks = TraceHelper.allowIpcs("getCachedTopTask.false", () ->
                        ActivityManagerWrapper.getInstance().getRunningTasks(
                                false /* filterOnlyVisibleRecents */));
                Collections.addAll(mOrderedTaskList, tasks);
            }

            ArrayList<TaskInfo> tasks = new ArrayList<>(mOrderedTaskList);
            // Strip the pinned task and recents task
            tasks.removeIf(t -> t.taskId == mPinnedTaskId || isRecentsTask(t));
            return new CachedTaskInfo(tasks);
        }
    }

    private static boolean isRecentsTask(TaskInfo task) {
        return task != null && task.configuration.windowConfiguration
                .getActivityType() == ACTIVITY_TYPE_RECENTS;
    }

    /**
     * Class to provide information about a task which can be safely cached and do not change
     * during the lifecycle of the task.
     */
    public static class CachedTaskInfo {

        // Only used when enableShellTopTaskTracking() is disabled
        @Nullable
        private final TaskInfo mTopTask;
        @Nullable
        public final List<TaskInfo> mAllCachedTasks;

        // Only used when enableShellTopTaskTracking() is enabled
        @Nullable
        private final GroupedTaskInfo mTopGroupedTask;
        @Nullable
        private final ArrayList<GroupedTaskInfo> mVisibleTasks;


        // Only used when enableShellTopTaskTracking() is enabled
        CachedTaskInfo(@NonNull ArrayList<GroupedTaskInfo> visibleTasks) {
            mAllCachedTasks = null;
            mTopTask = null;
            mVisibleTasks = visibleTasks;
            mTopGroupedTask = !mVisibleTasks.isEmpty() ? mVisibleTasks.getFirst() : null;

        }

        // Only used when enableShellTopTaskTracking() is disabled
        CachedTaskInfo(@NonNull List<TaskInfo> allCachedTasks) {
            mVisibleTasks = null;
            mTopGroupedTask = null;
            mAllCachedTasks = allCachedTasks;
            mTopTask = allCachedTasks.isEmpty() ? null : allCachedTasks.get(0);
        }

        /**
         * @return The list of visible tasks
         */
        public ArrayList<GroupedTaskInfo> getVisibleTasks() {
            return mVisibleTasks;
        }

        /**
         * @return The top task id
         */
        public int getTaskId() {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                // Callers should use topGroupedTaskContainsTask() instead
                return INVALID_TASK_ID;
            } else {
                return mTopTask != null ? mTopTask.taskId : INVALID_TASK_ID;
            }
        }

        /**
         * @return Whether the top grouped task contains the given {@param taskId} if
         *         Flags.enableShellTopTaskTracking() is true, otherwise it checks the top
         *         task as reported from TaskStackListener.
         */
        public boolean topGroupedTaskContainsTask(int taskId) {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                return mTopGroupedTask != null && mTopGroupedTask.containsTask(taskId);
            } else {
                return mTopTask != null && mTopTask.taskId == taskId;
            }
        }

        /**
         * Returns true if the root of the task chooser activity
         */
        public boolean isRootChooseActivity() {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                // TODO(346588978): Update this to not make an assumption on a specific task info
                return mTopGroupedTask != null && ACTION_CHOOSER.equals(
                        mTopGroupedTask.getTaskInfo1().baseIntent.getAction());
            } else {
                return mTopTask != null && ACTION_CHOOSER.equals(mTopTask.baseIntent.getAction());
            }
        }

        /**
         * If the given task holds an activity that is excluded from recents, and there
         * is another running task that is not excluded from recents, returns that underlying task.
         */
        public @Nullable CachedTaskInfo getVisibleNonExcludedTask() {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                // Callers should not need this when the full set of visible tasks are provided
                return null;
            }
            if (mTopTask == null
                    || (mTopTask.baseIntent.getFlags() & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0) {
                // Not an excluded task.
                return null;
            }
            List<TaskInfo> visibleNonExcludedTasks = mAllCachedTasks.stream()
                    .filter(t -> t.isVisible
                            && (t.baseIntent.getFlags() & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0
                            && t.getActivityType() != ACTIVITY_TYPE_HOME
                            && t.getActivityType() != ACTIVITY_TYPE_RECENTS)
                    .toList();
            return visibleNonExcludedTasks.isEmpty() ? null
                    : new CachedTaskInfo(visibleNonExcludedTasks);
        }

        /**
         * Returns true if this represents the HOME activity type task
         */
        public boolean isHomeTask() {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                // TODO(346588978): Update this to not make an assumption on a specific task info
                return mTopGroupedTask != null
                        && mTopGroupedTask.getTaskInfo1().getActivityType() == ACTIVITY_TYPE_HOME;
            } else {
                return mTopTask != null && mTopTask.configuration.windowConfiguration
                        .getActivityType() == ACTIVITY_TYPE_HOME;
            }
        }

        /**
         * Returns true if this represents the RECENTS activity type task
         */
        public boolean isRecentsTask() {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                // TODO(346588978): Update this to not make an assumption on a specific task info
                return mTopGroupedTask != null
                        && TopTaskTracker.isRecentsTask(mTopGroupedTask.getTaskInfo1());
            } else {
                return TopTaskTracker.isRecentsTask(mTopTask);
            }
        }

        /**
         * Returns {@link Task} array which can be used as a placeholder until the true object
         * is loaded by the model
         */
        public Task[] getPlaceholderTasks() {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                // TODO(346588978): Update this to return more than a single task once the callers
                //  are refactored
                if (mVisibleTasks.isEmpty()) {
                    return new Task[0];
                }
                final TaskInfo info = mVisibleTasks.getFirst().getTaskInfo1();
                return new Task[]{Task.from(new TaskKey(info), info, false)};
            } else {
                return mTopTask == null ? new Task[0]
                        : new Task[]{Task.from(new TaskKey(mTopTask), mTopTask, false)};
            }
        }

        /**
         * Returns {@link Task} array corresponding to the provided task ids which can be used as a
         * placeholder until the true object is loaded by the model
         */
        public Task[] getSplitPlaceholderTasks(int[] taskIds) {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                if (mVisibleTasks.isEmpty()
                        || mVisibleTasks.getFirst().getType() != TYPE_SPLIT) {
                    return new Task[0];
                }

                GroupedTaskInfo splitTask = mVisibleTasks.getFirst();
                Task[] result = new Task[taskIds.length];
                for (int i = 0; i < taskIds.length; i++) {
                    TaskInfo info = splitTask.getTaskById(taskIds[i]);
                    if (info == null) {
                        Log.w(TAG, "Requested task (" + taskIds[i] + ") not found");
                        return new Task[0];
                    }
                    result[i] = Task.from(new TaskKey(info), info, false);
                }
                return result;
            } else {
                if (mTopTask == null) {
                    return new Task[0];
                }
                Task[] result = new Task[taskIds.length];
                for (int i = 0; i < taskIds.length; i++) {
                    final int index = i;
                    int taskId = taskIds[i];
                    mAllCachedTasks.forEach(rti -> {
                        if (rti.taskId == taskId) {
                            result[index] = Task.from(new TaskKey(rti), rti, false);
                        }
                    });
                }
                return result;
            }
        }

        @Nullable
        public String getPackageName() {
            if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                // TODO(346588978): Update this to not make an assumption on a specific task info
                if (mTopGroupedTask == null) {
                    return null;
                }
                final TaskInfo info = mTopGroupedTask.getTaskInfo1();
                if (info.baseActivity == null) {
                    return null;
                }
                return info.baseActivity.getPackageName();
            } else {
                if (mTopTask == null || mTopTask.baseActivity == null) {
                    return null;
                }
                return mTopTask.baseActivity.getPackageName();
            }
        }
    }
}
