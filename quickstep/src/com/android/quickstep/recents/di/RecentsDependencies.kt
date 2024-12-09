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

package com.android.quickstep.recents.di

import android.content.Context
import android.util.Log
import android.view.View
import com.android.launcher3.util.coroutines.ProductionDispatchers
import com.android.quickstep.RecentsModel
import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate
import com.android.quickstep.recents.data.TaskVisualsChangedDelegateImpl
import com.android.quickstep.recents.data.TasksRepository
import com.android.quickstep.recents.usecase.GetThumbnailPositionUseCase
import com.android.quickstep.recents.usecase.GetThumbnailUseCase
import com.android.quickstep.recents.usecase.SysUiStatusNavFlagsUseCase
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.thumbnail.SplashAlphaUseCase
import com.android.quickstep.task.thumbnail.TaskThumbnailViewData
import com.android.quickstep.task.viewmodel.TaskContainerData
import com.android.quickstep.task.viewmodel.TaskOverlayViewModel
import com.android.quickstep.task.viewmodel.TaskThumbnailViewModel
import com.android.quickstep.task.viewmodel.TaskViewData
import com.android.quickstep.task.viewmodel.TaskViewModel
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.Task
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal typealias RecentsScopeId = String

class RecentsDependencies private constructor(private val appContext: Context) {
    private val scopes = mutableMapOf<RecentsScopeId, RecentsDependenciesScope>()

    init {
        startDefaultScope(appContext)
    }

    /**
     * This function initialised the default scope with RecentsView dependencies. These dependencies
     * are used multiple times and should be a singleton to share across Recents classes.
     */
    private fun startDefaultScope(appContext: Context) {
        createScope(DEFAULT_SCOPE_ID).apply {
            set(RecentsViewData::class.java.simpleName, RecentsViewData())
            val recentsCoroutineScope =
                CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineName("RecentsView"))
            set(CoroutineScope::class.java.simpleName, recentsCoroutineScope)
            val recentsModel = RecentsModel.INSTANCE.get(appContext)
            val taskVisualsChangedDelegate =
                TaskVisualsChangedDelegateImpl(
                    recentsModel,
                    recentsModel.thumbnailCache.highResLoadingState
                )
            set(TaskVisualsChangedDelegate::class.java.simpleName, taskVisualsChangedDelegate)

            // Create RecentsTaskRepository singleton
            val recentTasksRepository: RecentTasksRepository =
                with(recentsModel) {
                    TasksRepository(
                        this,
                        thumbnailCache,
                        iconCache,
                        taskVisualsChangedDelegate,
                        recentsCoroutineScope,
                        ProductionDispatchers
                    )
                }
            set(RecentTasksRepository::class.java.simpleName, recentTasksRepository)
        }
    }

    inline fun <reified T> inject(
        scopeId: RecentsScopeId = "",
        extras: RecentsDependenciesExtras = RecentsDependenciesExtras(),
        noinline factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
    ): T = inject(T::class.java, scopeId = scopeId, extras = extras, factory = factory)

    @Suppress("UNCHECKED_CAST")
    @JvmOverloads
    fun <T> inject(
        modelClass: Class<T>,
        scopeId: RecentsScopeId = DEFAULT_SCOPE_ID,
        extras: RecentsDependenciesExtras = RecentsDependenciesExtras(),
        factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
    ): T {
        val currentScopeId = scopeId.ifEmpty { DEFAULT_SCOPE_ID }
        val scope = scopes[currentScopeId] ?: createScope(currentScopeId)

        log("inject ${modelClass.simpleName} into ${scope.scopeId}", Log.INFO)
        var instance: T?
        synchronized(this) {
            instance = getDependency(scope, modelClass)
            log("found instance? $instance", Log.INFO)
            if (instance == null) {
                instance =
                    factory?.invoke(extras) as T ?: createDependency(modelClass, scopeId, extras)
                scope[modelClass.simpleName] = instance!!
            }
        }
        return instance!!
    }

    inline fun <reified T> provide(scopeId: RecentsScopeId = "", noinline factory: () -> T): T =
        provide(T::class.java, scopeId = scopeId, factory = factory)

    @JvmOverloads
    fun <T> provide(
        modelClass: Class<T>,
        scopeId: RecentsScopeId = DEFAULT_SCOPE_ID,
        factory: () -> T,
    ) = inject(modelClass, scopeId, factory = { factory.invoke() })

    private fun <T> getDependency(scope: RecentsDependenciesScope, modelClass: Class<T>): T? {
        var instance: T? = scope[modelClass.simpleName] as T?
        if (instance == null) {
            instance =
                scope.scopeIdsLinked.firstNotNullOfOrNull { scopeId ->
                    getScope(scopeId)[modelClass.simpleName]
                } as T?
        }
        if (instance != null) log("Found dependency: $instance", Log.INFO)
        return instance
    }

    fun getScope(scope: Any): RecentsDependenciesScope {
        val scopeId: RecentsScopeId = scope as? RecentsScopeId ?: scope.hashCode().toString()
        return getScope(scopeId)
    }

    fun getScope(scopeId: RecentsScopeId): RecentsDependenciesScope =
        scopes[scopeId] ?: createScope(scopeId)

    // TODO(b/353912757): Create a factory so we can prevent this method of growing indefinitely.
    //  Each class should be responsible for providing a factory function to create a new instance.
    @Suppress("UNCHECKED_CAST")
    private fun <T> createDependency(
        modelClass: Class<T>,
        scopeId: RecentsScopeId,
        extras: RecentsDependenciesExtras,
    ): T {
        log("createDependency ${modelClass.simpleName} with $scopeId and $extras", Log.WARN)
        val instance: Any =
            when (modelClass) {
                RecentTasksRepository::class.java -> {
                    with(RecentsModel.INSTANCE.get(appContext)) {
                        TasksRepository(
                            this,
                            thumbnailCache,
                            iconCache,
                            get(),
                            get(),
                            ProductionDispatchers
                        )
                    }
                }
                RecentsViewData::class.java -> RecentsViewData()
                TaskViewModel::class.java -> TaskViewModel(taskViewData = inject(scopeId, extras))
                TaskViewData::class.java -> {
                    val taskViewType = extras["TaskViewType"] as TaskViewType
                    TaskViewData(taskViewType)
                }
                TaskContainerData::class.java -> TaskContainerData()
                TaskThumbnailViewData::class.java -> TaskThumbnailViewData()
                TaskThumbnailViewModel::class.java ->
                    TaskThumbnailViewModel(
                        recentsViewData = inject(),
                        taskViewData = inject(scopeId, extras),
                        taskContainerData = inject(scopeId),
                        getThumbnailPositionUseCase = inject(),
                        tasksRepository = inject(),
                        splashAlphaUseCase = inject(scopeId),
                    )
                TaskOverlayViewModel::class.java -> {
                    val task = extras["Task"] as Task
                    TaskOverlayViewModel(
                        task = task,
                        recentsViewData = inject(),
                        recentTasksRepository = inject(),
                        getThumbnailPositionUseCase = inject()
                    )
                }
                GetThumbnailUseCase::class.java -> GetThumbnailUseCase(taskRepository = inject())
                SysUiStatusNavFlagsUseCase::class.java ->
                    SysUiStatusNavFlagsUseCase(taskRepository = inject())
                GetThumbnailPositionUseCase::class.java ->
                    GetThumbnailPositionUseCase(
                        deviceProfileRepository = inject(),
                        rotationStateRepository = inject(),
                        tasksRepository = inject()
                    )
                SplashAlphaUseCase::class.java ->
                    SplashAlphaUseCase(
                        recentsViewData = inject(),
                        taskContainerData = inject(scopeId),
                        taskThumbnailViewData = inject(scopeId),
                        tasksRepository = inject(),
                        rotationStateRepository = inject(),
                    )
                else -> {
                    log("Factory for ${modelClass.simpleName} not defined!", Log.ERROR)
                    error("Factory for ${modelClass.simpleName} not defined!")
                }
            }
        return instance as T
    }

    private fun createScope(scopeId: RecentsScopeId): RecentsDependenciesScope {
        return RecentsDependenciesScope(scopeId).also { scopes[scopeId] = it }
    }

    private fun log(message: String, @Log.Level level: Int = Log.DEBUG) {
        if (DEBUG) {
            when (level) {
                Log.WARN -> Log.w(TAG, message)
                Log.VERBOSE -> Log.v(TAG, message)
                Log.INFO -> Log.i(TAG, message)
                Log.ERROR -> Log.e(TAG, message)
                else -> Log.d(TAG, message)
            }
        }
    }

    companion object {
        private const val DEFAULT_SCOPE_ID = "RecentsDependencies::GlobalScope"
        private const val TAG = "RecentsDependencies"
        private const val DEBUG = false

        @Volatile private lateinit var instance: RecentsDependencies

        fun initialize(view: View): RecentsDependencies = initialize(view.context)

        fun initialize(context: Context): RecentsDependencies {
            synchronized(this) {
                if (!Companion::instance.isInitialized) {
                    instance = RecentsDependencies(context.applicationContext)
                }
            }
            return instance
        }

        fun getInstance(): RecentsDependencies {
            if (!Companion::instance.isInitialized) {
                throw UninitializedPropertyAccessException(
                    "Recents dependencies are not initialized. " +
                        "Call `RecentsDependencies.initialize` before using this container."
                )
            }
            return instance
        }

        fun destroy() {
            instance.scopes.clear()
            instance.startDefaultScope(instance.appContext)
        }
    }
}

inline fun <reified T> RecentsDependencies.Companion.inject(
    scope: Any = "",
    vararg extras: Pair<String, Any>,
    noinline factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
): Lazy<T> = lazy { get(scope, RecentsDependenciesExtras(extras), factory) }

inline fun <reified T> RecentsDependencies.Companion.get(
    scope: Any = "",
    extras: RecentsDependenciesExtras = RecentsDependenciesExtras(),
    noinline factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
): T {
    val scopeId: RecentsScopeId = scope as? RecentsScopeId ?: scope.hashCode().toString()
    return getInstance().inject(scopeId, extras, factory)
}

inline fun <reified T> RecentsDependencies.Companion.get(
    scope: Any = "",
    vararg extras: Pair<String, Any>,
    noinline factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
): T = get(scope, RecentsDependenciesExtras(extras), factory)

fun RecentsDependencies.Companion.getScope(scopeId: Any) = getInstance().getScope(scopeId)
