/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3;

import android.app.Activity;
import android.app.ActionBar;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.provider.Settings.System;
import android.support.v4.os.BuildCompat;
import android.view.MenuItem;

import com.android.launcher3.graphics.IconShapeOverride;

import java.util.List;
import java.util.ArrayList;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends Activity {

    private static final String ICON_BADGING_PREFERENCE_KEY = "pref_icon_badging";
    // TODO: use Settings.Secure.NOTIFICATION_BADGING
    private static final String NOTIFICATION_BADGING = "notification_badging";
    private static final String DEFAULT_WEATHER_ICON_PACKAGE = "org.omnirom.omnijaws";
    private static final String DEFAULT_WEATHER_ICON_PREFIX = "outline";
    private static final String CHRONUS_ICON_PACK_INTENT = "com.dvtonder.chronus.ICON_PACK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP|ActionBar.DISPLAY_SHOW_TITLE);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new LauncherSettingsFragment())
                .commit();
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends PreferenceFragment {

        private SystemDisplayRotationLockObserver mRotationLockObserver;
        private IconBadgingObserver mIconBadgingObserver;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.launcher_preferences);

            ContentResolver resolver = getActivity().getContentResolver();

            // Setup allow rotation preference
            Preference rotationPref = findPreference(Utilities.ALLOW_ROTATION_PREFERENCE_KEY);
            if (getResources().getBoolean(R.bool.allow_rotation)) {
                // Launcher supports rotation by default. No need to show this setting.
                getPreferenceScreen().removePreference(rotationPref);
            } else {
                mRotationLockObserver = new SystemDisplayRotationLockObserver(rotationPref, resolver);

                // Register a content observer to listen for system setting changes while
                // this UI is active.
                resolver.registerContentObserver(
                        Settings.System.getUriFor(System.ACCELEROMETER_ROTATION),
                        false, mRotationLockObserver);

                // Initialize the UI once
                mRotationLockObserver.onChange(true);
                rotationPref.setDefaultValue(Utilities.getAllowRotationDefaultValue(getActivity()));
            }

            Preference iconBadgingPref = findPreference(ICON_BADGING_PREFERENCE_KEY);
            if (!BuildCompat.isAtLeastO()) {
                getPreferenceScreen().removePreference(
                        findPreference(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY));
                getPreferenceScreen().removePreference(iconBadgingPref);
            } else {
                // Listen to system notification badge settings while this UI is active.
                mIconBadgingObserver = new IconBadgingObserver(iconBadgingPref, resolver);
                resolver.registerContentObserver(
                        Settings.Secure.getUriFor(NOTIFICATION_BADGING),
                        false, mIconBadgingObserver);
                mIconBadgingObserver.onChange(true);
            }

            final ListPreference iconShape = (ListPreference) findPreference(Utilities.ICON_SHAPE_PREFERENCE_KEY);
            iconShape.setSummary(iconShape.getEntry());
            iconShape.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int index = iconShape.findIndexOfValue((String) newValue);
                    iconShape.setSummary(iconShape.getEntries()[index]);
                    return true;
                }
            });

            final ListPreference iconPack = (ListPreference) findPreference(Utilities.WEATHER_ICON_PACK_PREFERENCE_KEY) ;

            String settingHeaderPackage = Utilities.getWeatherIconPack(getActivity());
            if (settingHeaderPackage == null) {
                settingHeaderPackage = DEFAULT_WEATHER_ICON_PACKAGE + "." + DEFAULT_WEATHER_ICON_PREFIX;
            }

            List<String> entries = new ArrayList<String>();
            List<String> values = new ArrayList<String>();
            getAvailableWeatherIconPacks(entries, values);
            iconPack.setEntries(entries.toArray(new String[entries.size()]));
            iconPack.setEntryValues(values.toArray(new String[values.size()]));

            int valueIndex = iconPack.findIndexOfValue(settingHeaderPackage);
            if (valueIndex == -1) {
                // no longer found
                settingHeaderPackage = DEFAULT_WEATHER_ICON_PACKAGE + "." + DEFAULT_WEATHER_ICON_PREFIX;
                valueIndex = iconPack.findIndexOfValue(settingHeaderPackage);
            }
            iconPack.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
            iconPack.setSummary(iconPack.getEntry());
            iconPack.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int valueIndex = iconPack.findIndexOfValue((String)newValue);
                    iconPack.setSummary(iconPack.getEntries()[valueIndex]);
                    return true;
                }
            });

            final ListPreference eventsPeriod = (ListPreference) findPreference(Utilities.SHOW_EVENTS_PERIOD_PREFERENCE_KEY);
            eventsPeriod.setSummary(eventsPeriod.getEntry());
            eventsPeriod.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int index = eventsPeriod.findIndexOfValue((String) newValue);
                    eventsPeriod.setSummary(eventsPeriod.getEntries()[index]);
                    return true;
                }
            });
        }

        @Override
        public void onDestroy() {
            if (mRotationLockObserver != null) {
                getActivity().getContentResolver().unregisterContentObserver(mRotationLockObserver);
                mRotationLockObserver = null;
            }
            if (mIconBadgingObserver != null) {
                getActivity().getContentResolver().unregisterContentObserver(mIconBadgingObserver);
                mIconBadgingObserver = null;
            }
            super.onDestroy();
        }

        private void getAvailableWeatherIconPacks(List<String> entries, List<String> values) {
            Intent i = new Intent();
            PackageManager packageManager = getActivity().getPackageManager();
            i.setAction("org.omnirom.WeatherIconPack");
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                String packageName = r.activityInfo.packageName;
                String label = r.activityInfo.loadLabel(getActivity().getPackageManager()).toString();
                if (label == null) {
                    label = r.activityInfo.packageName;
                }
                if (entries.contains(label)) {
                    continue;
                }
                if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                    values.add(0, r.activityInfo.name);
                } else {
                    values.add(r.activityInfo.name);
                }

                if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                    entries.add(0, label);
                } else {
                    entries.add(label);
                }
            }
            i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(CHRONUS_ICON_PACK_INTENT);
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                String packageName = r.activityInfo.packageName;
                String label = r.activityInfo.loadLabel(getActivity().getPackageManager()).toString();
                if (label == null) {
                    label = r.activityInfo.packageName;
                }
                if (entries.contains(label)) {
                    continue;
                }
                values.add(packageName + ".weather");

                entries.add(label);
            }
        }
    }

    /**
     * Content observer which listens for system auto-rotate setting changes, and enables/disables
     * the launcher rotation setting accordingly.
     */
    private static class SystemDisplayRotationLockObserver extends ContentObserver {

        private final Preference mRotationPref;
        private final ContentResolver mResolver;

        public SystemDisplayRotationLockObserver(
                Preference rotationPref, ContentResolver resolver) {
            super(new Handler());
            mRotationPref = rotationPref;
            mResolver = resolver;
        }

        @Override
        public void onChange(boolean selfChange) {
            boolean enabled = Settings.System.getInt(mResolver,
                    Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
            mRotationPref.setEnabled(enabled);
            mRotationPref.setSummary(enabled
                    ? R.string.allow_rotation_desc : R.string.allow_rotation_blocked_desc);
        }
    }

    /**
     * Content observer which listens for system badging setting changes,
     * and updates the launcher badging setting subtext accordingly.
     */
    private static class IconBadgingObserver extends ContentObserver {

        private final Preference mBadgingPref;
        private final ContentResolver mResolver;

        public IconBadgingObserver(Preference badgingPref, ContentResolver resolver) {
            super(new Handler());
            mBadgingPref = badgingPref;
            mResolver = resolver;
        }

        @Override
        public void onChange(boolean selfChange) {
            boolean enabled = Settings.Secure.getInt(mResolver, NOTIFICATION_BADGING, 1) == 1;
            mBadgingPref.setSummary(enabled
                    ? R.string.icon_badging_desc_on
                    : R.string.icon_badging_desc_off);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }
}
