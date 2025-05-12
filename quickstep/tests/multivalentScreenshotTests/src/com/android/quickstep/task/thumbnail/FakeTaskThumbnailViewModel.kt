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

package com.android.quickstep.task.thumbnail

import android.graphics.Matrix
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.quickstep.task.viewmodel.TaskThumbnailViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTaskThumbnailViewModel : TaskThumbnailViewModel {
    override val dimProgress = MutableStateFlow(0f)
    override val splashAlpha = MutableStateFlow(0f)
    override val uiState = MutableStateFlow<TaskThumbnailUiState>(Uninitialized)

    override fun bind(taskId: Int) {
        // no-op
    }

    override fun getThumbnailPositionState(width: Int, height: Int, isRtl: Boolean) =
        Matrix.IDENTITY_MATRIX
}
