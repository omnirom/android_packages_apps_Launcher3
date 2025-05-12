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

package com.android.quickstep.task.viewmodel

import android.graphics.Matrix
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState
import kotlinx.coroutines.flow.Flow

/** ViewModel for representing TaskThumbnails */
interface TaskThumbnailViewModel {
    /** Provides the level of dimming that the View should have */
    val dimProgress: Flow<Float>

    /** Provides the alpha of the splash icon */
    val splashAlpha: Flow<Float>

    /** Provides the UiState by which the task thumbnail can be represented */
    val uiState: Flow<TaskThumbnailUiState>

    /** Attaches this ViewModel to a specific task id for it to provide data from. */
    fun bind(taskId: Int)

    /** Returns a Matrix which can be applied to the snapshot */
    fun getThumbnailPositionState(width: Int, height: Int, isRtl: Boolean): Matrix
}
