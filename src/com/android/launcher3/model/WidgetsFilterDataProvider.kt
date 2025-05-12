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

package com.android.launcher3.model

import android.content.Context
import androidx.annotation.WorkerThread
import com.android.launcher3.R
import com.android.launcher3.util.ResourceBasedOverride
import java.util.function.Predicate

/** Helper for the widgets model to load the filters that can be applied to available widgets. */
open class WidgetsFilterDataProvider(val context: Context) : ResourceBasedOverride {
    /**
     * Start regular periodic refresh of widget filtering data starting now (if not started
     * already).
     */
    @WorkerThread
    open fun initPeriodicDataRefresh(callback: WidgetsFilterLoadedCallback? = null) {
        // no-op
    }

    /**
     * Returns a filter that should be applied to the widget predictions.
     *
     * @return null if no filter needs to be applied
     */
    @WorkerThread open fun getPredictedWidgetsFilter(): Predicate<WidgetItem>? = null

    /**
     * Returns a filter that should be applied to the widgets list to see which widgets can be shown
     * by default.
     *
     * @return null if no separate "default" list is supported
     */
    @WorkerThread open fun getDefaultWidgetsFilter(): Predicate<WidgetItem>? = null

    /** Called when filter data provider is no longer needed. */
    open fun destroy() {}

    companion object {
        /** Returns a new instance of the [WidgetsFilterDataProvider] based on resource override. */
        fun newInstance(context: Context?): WidgetsFilterDataProvider {
            return ResourceBasedOverride.Overrides.getObject(
                WidgetsFilterDataProvider::class.java,
                context,
                R.string.widgets_filter_data_provider_class,
            )
        }
    }
}

/** Interface for the model callback to be invoked when filters are loaded. */
interface WidgetsFilterLoadedCallback {
    /** Method called back when widget filters are loaded */
    fun onWidgetsFilterLoaded()
}
