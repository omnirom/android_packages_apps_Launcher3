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

package com.android.quickstep.views

import com.android.quickstep.ViewUtils
import com.android.quickstep.recents.viewmodel.RecentsViewModel
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Helper for [RecentsView] to interact with the [RecentsViewModel]. */
class RecentsViewModelHelper(private val recentsViewModel: RecentsViewModel) {
    private lateinit var viewAttachedScope: CoroutineScope

    fun onAttachedToWindow() {
        viewAttachedScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineName("RecentsView"))
    }

    fun onDetachedFromWindow() {
        viewAttachedScope.cancel("RecentsView detaching from window")
    }

    fun switchToScreenshot(
        taskView: TaskView,
        updatedThumbnails: Map<Int, ThumbnailData>?,
        onFinishRunnable: Runnable,
    ) {
        // Update recentsViewModel and apply the thumbnailOverride ASAP, before waiting inside
        // viewAttachedScope.
        recentsViewModel.setRunningTaskShowScreenshot(true)
        viewAttachedScope.launch {
            recentsViewModel.waitForRunningTaskShowScreenshotToUpdate()
            recentsViewModel.waitForThumbnailsToUpdate(updatedThumbnails)
            ViewUtils.postFrameDrawn(taskView, onFinishRunnable)
        }
    }
}
