/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.quickstep.contextualeducation;

import com.android.launcher3.contextualeducation.ContextualEduStatsManager;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.quickstep.SystemUiProxy;
import com.android.systemui.contextualeducation.GestureType;

import javax.inject.Inject;

/**
 * A class to update contextual education data via {@link SystemUiProxy}
 */
@LauncherAppSingleton
public class SystemContextualEduStatsManager extends ContextualEduStatsManager {
    private final SystemUiProxy mSystemUiProxy;

    @Inject
    public SystemContextualEduStatsManager(SystemUiProxy systemUiProxy) {
        mSystemUiProxy = systemUiProxy;
    }

    @Override
    public void updateEduStats(boolean isTrackpadGesture, GestureType gestureType) {
        mSystemUiProxy.updateContextualEduStats(isTrackpadGesture,
                gestureType.name());
    }
}
