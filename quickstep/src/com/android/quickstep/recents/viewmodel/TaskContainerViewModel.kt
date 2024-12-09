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

package com.android.quickstep.recents.viewmodel

import android.graphics.Bitmap
import com.android.quickstep.recents.usecase.GetThumbnailUseCase
import com.android.quickstep.recents.usecase.SysUiStatusNavFlagsUseCase
import com.android.quickstep.task.thumbnail.SplashAlphaUseCase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class TaskContainerViewModel(
    private val sysUiStatusNavFlagsUseCase: SysUiStatusNavFlagsUseCase,
    private val getThumbnailUseCase: GetThumbnailUseCase,
    private val splashAlphaUseCase: SplashAlphaUseCase,
) {
    fun getThumbnail(taskId: Int): Bitmap? = getThumbnailUseCase.run(taskId)

    fun getSysUiStatusNavFlags(taskId: Int) =
        sysUiStatusNavFlagsUseCase.getSysUiStatusNavFlags(taskId)

    fun shouldShowThumbnailSplash(taskId: Int): Boolean =
        (runBlocking { splashAlphaUseCase.execute(taskId).firstOrNull() } ?: 0f) > 0f
}
