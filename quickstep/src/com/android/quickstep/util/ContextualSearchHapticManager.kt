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

package com.android.quickstep.util

import android.content.Context
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.VibratorWrapper
import com.android.quickstep.DeviceConfigWrapper.Companion.get
import kotlin.math.pow

/** Manages haptics relating to Contextual Search invocations. */
class ContextualSearchHapticManager
internal constructor(@ApplicationContext private val context: Context) : SafeCloseable {

    private var searchEffect = createSearchEffect()
    private var contextualSearchStateManager = ContextualSearchStateManager.INSTANCE[context]

    private fun createSearchEffect() =
        if (
            context
                .getSystemService(Vibrator::class.java)!!
                .areAllPrimitivesSupported(Composition.PRIMITIVE_TICK)
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(Composition.PRIMITIVE_TICK, 1f)
                .compose()
        } else {
            // fallback for devices without composition support
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }

    /** Indicates that search has been invoked. */
    fun vibrateForSearch() {
        searchEffect.let { VibratorWrapper.INSTANCE[context].vibrate(it) }
    }

    /** Indicates that search will be invoked if the current gesture is maintained. */
    fun vibrateForSearchHint() {
        val navbarConfig = get()
        // Whether we should play the hint (ramp up) haptic
        val shouldVibrate: Boolean =
            if (
                context
                    .getSystemService(Vibrator::class.java)!!
                    .areAllPrimitivesSupported(Composition.PRIMITIVE_LOW_TICK)
            ) {
                if (contextualSearchStateManager.shouldPlayHapticOverride.isPresent) {
                    contextualSearchStateManager.shouldPlayHapticOverride.get()
                } else {
                    navbarConfig.enableSearchHapticHint
                }
            } else {
                false
            }

        if (shouldVibrate) {
            val startScale = navbarConfig.lpnhHapticHintStartScalePercent / 100f
            val endScale = navbarConfig.lpnhHapticHintEndScalePercent / 100f
            val scaleExponent = navbarConfig.lpnhHapticHintScaleExponent
            val iterations = navbarConfig.lpnhHapticHintIterations
            val delayMs = navbarConfig.lpnhHapticHintDelay
            val composition = VibrationEffect.startComposition()
            for (i in 0 until iterations) {
                val t = i / (iterations - 1f)
                val scale =
                    ((1 - t) * startScale + t * endScale)
                        .toDouble()
                        .pow(scaleExponent.toDouble())
                        .toFloat()
                if (i == 0) {
                    // Adds a delay before the ramp starts
                    composition.addPrimitive(Composition.PRIMITIVE_LOW_TICK, scale, delayMs)
                } else {
                    composition.addPrimitive(Composition.PRIMITIVE_LOW_TICK, scale)
                }
            }
            VibratorWrapper.INSTANCE[context].vibrate(composition.compose())
        }
    }

    override fun close() {}

    companion object {
        @JvmField val INSTANCE = MainThreadInitializedObject { ContextualSearchHapticManager(it) }
    }
}
