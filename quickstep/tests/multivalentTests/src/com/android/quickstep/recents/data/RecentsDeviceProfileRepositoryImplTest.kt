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

package com.android.quickstep.recents.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.FakeInvariantDeviceProfileTest
import com.android.quickstep.views.RecentsViewContainer
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [RecentsDeviceProfileRepositoryImpl] */
@RunWith(AndroidJUnit4::class)
class RecentsDeviceProfileRepositoryImplTest : FakeInvariantDeviceProfileTest() {
    private val recentsViewContainer = mock<RecentsViewContainer>()

    private val systemUnderTest = RecentsDeviceProfileRepositoryImpl(recentsViewContainer)

    @Test
    fun deviceProfileMappedCorrectly() {
        initializeVarsForTablet()
        val tabletDeviceProfile = newDP()
        whenever(recentsViewContainer.deviceProfile).thenReturn(tabletDeviceProfile)

        assertThat(systemUnderTest.getRecentsDeviceProfile())
            .isEqualTo(RecentsDeviceProfile(isLargeScreen = true))
    }
}
