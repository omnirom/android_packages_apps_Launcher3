/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.overlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.android.launcher3.Launcher;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks;

import com.google.android.libraries.gsa.launcherclient.LauncherClientCallbacks;

import java.io.PrintWriter;

/**
 * Implements {@link LauncherOverlay} and passes all the corresponding events to {@link
 * LauncherClient}. {@see setClient}
 *
 * <p>Implements {@link LauncherClientCallbacks} and sends all the corresponding callbacks to {@link
 * Launcher}.
 */
public class OverlayCallbackImpl
        implements LauncherOverlay, LauncherClientCallbacks, LauncherOverlayManager,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public OverlayCallbackImpl(Launcher launcher) {
    }

    @Override
    public void onDeviceProvideChanged() {
    }

    @Override
    public void onAttachedToWindow() {
    }

    @Override
    public void onDetachedFromWindow() {
    }

    @Override
    public void dump(String prefix, PrintWriter w) {
    }

    @Override
    public void openOverlay() {
    }

    @Override
    public void hideOverlay(boolean animate) {
    }

    @Override
    public void hideOverlay(int duration) {
    }

    @Override
    public void onActivityStarted() {
    }

    @Override
    public void onActivityResumed() {
    }

    @Override
    public void onActivityPaused() {
    }

    @Override
    public void onActivityStopped() {
    }

    @Override
    public void onActivityDestroyed() {
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    }

    @Override
    public void onServiceStateChanged(boolean overlayAttached, boolean hotwordActive) {
    }

    @Override
    public void onOverlayScrollChanged(float progress) {
    }

    @Override
    public void onScrollInteractionBegin() {
    }

    @Override
    public void onScrollInteractionEnd() {
    }

    @Override
    public void onScrollChange(float progress, boolean rtl) {
    }

    @Override
    public void setOverlayCallbacks(LauncherOverlayCallbacks callbacks) {
    }
}
