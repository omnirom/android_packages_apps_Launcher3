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

package com.android.launcher3.util

import android.net.Uri
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

/**
 * Provides a sandboxed [SettingsCache] for testing.
 *
 * Note that listeners registered to [cache] will never be invoked.
 */
class SettingsCacheSandbox {
    private val values = mutableMapOf<Uri, Int>()

    /** Fake cache that delegates [SettingsCache.getValue] to [values]. */
    val cache =
        mock<SettingsCache> {
            on { getValue(any<Uri>()) } doAnswer { mock.getValue(it.getArgument(0), 1) }
            on { getValue(any<Uri>(), any<Int>()) } doAnswer
                {
                    values.getOrDefault(it.getArgument(0), it.getArgument(1)) == 1
                }
        }

    operator fun get(key: Uri): Int? = values[key]

    operator fun set(key: Uri, value: Int) {
        values[key] = value
    }
}
