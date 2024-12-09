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

package com.android.quickstep.recents.usecase

import android.graphics.Bitmap
import com.android.quickstep.recents.data.RecentTasksRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/** Use case for retrieving thumbnail. */
class GetThumbnailUseCase(private val taskRepository: RecentTasksRepository) {
    /** Returns the latest thumbnail associated with [taskId] if loaded, or null otherwise */
    fun run(taskId: Int): Bitmap? = runBlocking {
        taskRepository.getThumbnailById(taskId).firstOrNull()?.thumbnail
    }
}
