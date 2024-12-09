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

package com.android.quickstep

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.R
import com.android.launcher3.util.LauncherModelHelper
import com.android.systemui.contextualeducation.GestureType
import com.android.systemui.shared.system.InputConsumerController
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherSwipeHandlerV2Test {

    @Mock private lateinit var taskAnimationManager: TaskAnimationManager

    private lateinit var gestureState: GestureState
    @Mock private lateinit var inputConsumerController: InputConsumerController

    @Mock private lateinit var systemUiProxy: SystemUiProxy

    private lateinit var underTest: LauncherSwipeHandlerV2

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    private val launcherModelHelper = LauncherModelHelper()
    private val sandboxContext = launcherModelHelper.sandboxContext

    private val flingSpeed =
        -(sandboxContext.resources.getDimension(R.dimen.quickstep_fling_threshold_speed) + 1)

    @Before
    fun setup() {
        sandboxContext.putObject(SystemUiProxy.INSTANCE, systemUiProxy)
        val deviceState = mock(RecentsAnimationDeviceState::class.java)
        whenever(deviceState.rotationTouchHelper).thenReturn(mock(RotationTouchHelper::class.java))
        gestureState = spy(GestureState(OverviewComponentObserver(sandboxContext, deviceState), 0))

        underTest =
            LauncherSwipeHandlerV2(
                sandboxContext,
                deviceState,
                taskAnimationManager,
                gestureState,
                0,
                false,
                inputConsumerController
            )
        underTest.onGestureStarted(/* isLikelyToStartNewTask= */ false)
    }

    @Test
    fun goHomeFromAppByTrackpad_updateEduStats() {
        gestureState.setTrackpadGestureType(GestureState.TrackpadGestureType.THREE_FINGER)
        underTest.onGestureEnded(flingSpeed, PointF())
        verify(systemUiProxy)
            .updateContextualEduStats(
                /* isTrackpadGesture= */ eq(true),
                eq(GestureType.HOME.toString())
            )
    }

    @Test
    fun goHomeFromAppByTouch_updateEduStats() {
        underTest.onGestureEnded(flingSpeed, PointF())
        verify(systemUiProxy)
            .updateContextualEduStats(
                /* isTrackpadGesture= */ eq(false),
                eq(GestureType.HOME.toString())
            )
    }
}
