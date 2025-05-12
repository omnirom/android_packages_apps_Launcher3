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

package com.android.launcher3

import android.content.Context
import com.android.launcher3.util.Executors.MAIN_EXECUTOR

/** Emulates Launcher preferences for a test environment. */
class FakeLauncherPrefs(private val context: Context) : LauncherPrefs() {
    private val prefsMap = mutableMapOf<String, Any>()
    private val listeners = mutableSetOf<LauncherPrefChangeListener>()

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(item: ContextualItem<T>): T {
        return prefsMap.getOrDefault(item.sharedPrefKey, item.defaultValueFromContext(context)) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(item: ConstantItem<T>): T {
        return prefsMap.getOrDefault(item.sharedPrefKey, item.defaultValue) as T
    }

    override fun put(vararg itemsToValues: Pair<Item, Any>) = putSync(*itemsToValues)

    override fun <T : Any> put(item: Item, value: T) = putSync(item to value)

    override fun putSync(vararg itemsToValues: Pair<Item, Any>) {
        itemsToValues
            .map { (i, v) -> i.sharedPrefKey to v }
            .forEach { (k, v) ->
                prefsMap[k] = v
                notifyChange(k)
            }
    }

    override fun addListener(listener: LauncherPrefChangeListener, vararg items: Item) {
        listeners.add(listener)
    }

    override fun removeListener(listener: LauncherPrefChangeListener, vararg items: Item) {
        listeners.remove(listener)
    }

    override fun has(vararg items: Item) = items.all { it.sharedPrefKey in prefsMap }

    override fun remove(vararg items: Item) = removeSync(*items)

    override fun removeSync(vararg items: Item) {
        items
            .filter { it.sharedPrefKey in prefsMap }
            .forEach {
                prefsMap.remove(it.sharedPrefKey)
                notifyChange(it.sharedPrefKey)
            }
    }

    override fun close() = Unit

    private fun notifyChange(key: String) {
        // Mimics SharedPreferencesImpl#notifyListeners main thread dispatching.
        MAIN_EXECUTOR.execute { listeners.forEach { it.onPrefChanged(key) } }
    }
}
