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

package com.android.launcher3.settings;

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS;

import static com.android.launcher3.config.FeatureFlags.IS_STUDIO_BUILD;
import static com.android.launcher3.states.RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceGroup.PreferencePositionCallback;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.GridOption;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.uioverrides.flags.DeveloperOptionsFragment;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.DisplayController;

import org.omnirom.omnilib.utils.PackageUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends FragmentActivity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback,
        SharedPreferences.OnSharedPreferenceChangeListener{
    private static final String TAG = "SettingsActivity";

    /** List of fragments that can be hosted by this activity. */
    private static final List<String> VALID_PREFERENCE_FRAGMENTS =
            !Utilities.IS_DEBUG_DEVICE ? Collections.emptyList()
                    : Collections.singletonList(DeveloperOptionsFragment.class.getName());

    private static final String DEVELOPER_OPTIONS_KEY = "pref_developer_options";
    private static final String FLAGS_PREFERENCE_KEY = "flag_toggler";

    private static final String NOTIFICATION_DOTS_PREFERENCE_KEY = "pref_icon_badging";
    private static final String GRID_SIZE_PREFERENCE_KEY = "pref_grid";
    public static final String QSB_LOCATION_PREFERENCE_KEY = "pref_qsb_location";

    public static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    public static final String EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args";
    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    private static final String SEARCH_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String SHOW_LEFT_TAB_PREFERENCE_KEY = "pref_left_tab";

    @VisibleForTesting
    static final String EXTRA_FRAGMENT = ":settings:fragment";
    @VisibleForTesting
    static final String EXTRA_FRAGMENT_ARGS = ":settings:fragment_args";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        setActionBar(findViewById(R.id.action_bar));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_FRAGMENT) || intent.hasExtra(EXTRA_FRAGMENT_ARGS)
                || intent.hasExtra(EXTRA_FRAGMENT_ARG_KEY)) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            Bundle args = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS);
            if (args == null) {
                args = new Bundle();
            }

            String prefKey = intent.getStringExtra(EXTRA_FRAGMENT_ARG_KEY);
            if (!TextUtils.isEmpty(prefKey)) {
                args.putString(EXTRA_FRAGMENT_ARG_KEY, prefKey);
            }

            final FragmentManager fm = getSupportFragmentManager();
            final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(),
                    getPreferenceFragment());
            f.setArguments(args);
            // Display the fragment as the main content.
            fm.beginTransaction().replace(R.id.content_frame, f).commit();
        }
        LauncherPrefs.getPrefs(getApplicationContext())
                .registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Obtains the preference fragment to instantiate in this activity.
     *
     * @return the preference fragment class
     * @throws IllegalArgumentException if the fragment is unknown to this activity
     */
    private String getPreferenceFragment() {
        String preferenceFragment = getIntent().getStringExtra(EXTRA_FRAGMENT);
        String defaultFragment = getString(R.string.settings_fragment_name);

        if (TextUtils.isEmpty(preferenceFragment)) {
            return defaultFragment;
        } else if (!preferenceFragment.equals(defaultFragment)
                && !VALID_PREFERENCE_FRAGMENTS.contains(preferenceFragment)) {
            throw new IllegalArgumentException(
                    "Invalid fragment for this activity: " + preferenceFragment);
        } else {
            return preferenceFragment;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) { }

    private boolean startPreference(String fragment, Bundle args, String key) {
        if (Utilities.ATLEAST_P && getSupportFragmentManager().isStateSaved()) {
            // Sometimes onClick can come after onPause because of being posted on the handler.
            // Skip starting new preferences in that case.
            return false;
        }
        final FragmentManager fm = getSupportFragmentManager();
        final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(), fragment);
        if (f instanceof DialogFragment) {
            f.setArguments(args);
            ((DialogFragment) f).show(fm, key);
        } else {
            startActivity(new Intent(this, SettingsActivity.class)
                    .putExtra(EXTRA_FRAGMENT, fragment)
                    .putExtra(EXTRA_FRAGMENT_ARGS, args));
        }
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragmentCompat preferenceFragment, Preference pref) {
        return startPreference(pref.getFragment(), pref.getExtras(), pref.getKey());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        return startPreference(getString(R.string.settings_fragment_name), args, pref.getKey());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends PreferenceFragmentCompat {

        private String mHighLightKey;
        private boolean mPreferenceHighlighted = false;
        private Preference mDeveloperOptionPref;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final Bundle args = getArguments();
            mHighLightKey = args == null ? null : args.getString(EXTRA_FRAGMENT_ARG_KEY);
            if (rootKey == null && !TextUtils.isEmpty(mHighLightKey)) {
                rootKey = getParentKeyForPref(mHighLightKey);
            }

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setPreferencesFromResource(R.xml.launcher_preferences, rootKey);

            PreferenceScreen screen = getPreferenceScreen();
            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference preference = screen.getPreference(i);
                if (initPreference(preference)) {
                    if (IS_STUDIO_BUILD && preference == mDeveloperOptionPref) {
                        preference.setOrder(0);
                    }
                } else {
                    screen.removePreference(preference);
                }
            }

            if (getActivity() != null && !TextUtils.isEmpty(getPreferenceScreen().getTitle())) {
                if (getPreferenceScreen().getTitle().equals(
                        getResources().getString(R.string.search_pref_screen_title))){
                    DeviceProfile mDeviceProfile = InvariantDeviceProfile.INSTANCE.get(
                            getContext()).getDeviceProfile(getContext());
                    getPreferenceScreen().setTitle(mDeviceProfile.isMultiDisplay
                            || mDeviceProfile.isPhone ?
                            R.string.search_pref_screen_title :
                            R.string.search_pref_screen_title_tablet);
                }
                getActivity().setTitle(getPreferenceScreen().getTitle());
            }

            Preference showQsbWidget = findPreference(Utilities.QSB_SHOW);
            showQsbWidget.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    new Handler().postDelayed(() -> Utilities.restart(getActivity()), Utilities.WAIT_BEFORE_RESTART);
                    return true;
                }
            });

            Preference leftTabPage = findPreference(SHOW_LEFT_TAB_PREFERENCE_KEY);
            if (!isSearchInstalled()) {
                getPreferenceScreen().removePreference(leftTabPage);
            }
            leftTabPage.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    new Handler().postDelayed(() -> Utilities.restart(getActivity()), Utilities.WAIT_BEFORE_RESTART);
                    return true;
                }
            });

            final ListPreference grid = (ListPreference) findPreference(GRID_SIZE_PREFERENCE_KEY);
            InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(getContext());
            ArrayList<String> entries = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();
            for (GridOption gridOption : parseAllGridOptions(idp.deviceType)) {
                values.add(gridOption.name);
                entries.add(gridOption.numColumns + " x " + gridOption.numRows);
            }

            grid.setEntries(entries.toArray(new String[entries.size()]));
            grid.setEntryValues(values.toArray(new String[values.size()]));

            String currentGrid = idp.getCurrentGridName(getContext());
            int valueIndex = grid.findIndexOfValue(currentGrid);
            grid.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
            grid.setSummary(grid.getEntry());

            grid.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // Verify that this is a valid grid option
                    String gridName = (String) newValue;
                    GridOption match = null;
                    for (GridOption option : parseAllGridOptions(idp.deviceType)) {
                        if (option.name.equals(gridName)) {
                            match = option;
                            break;
                        }
                    }
                    if (match == null) {
                        return false;
                    }

                    InvariantDeviceProfile.INSTANCE.get(getContext())
                            .setCurrentGrid(getContext(), gridName);

                    int valueIndex = grid.findIndexOfValue(gridName);
                    grid.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
                    grid.setSummary(grid.getEntries()[valueIndex]);
                    return true;
                }
            });

            final ListPreference qsbLocation = (ListPreference) findPreference(QSB_LOCATION_PREFERENCE_KEY);
            valueIndex = qsbLocation.findIndexOfValue(qsbLocation.getValue());
            qsbLocation.setSummary(qsbLocation.getEntries()[valueIndex]);
            qsbLocation.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    new Handler().postDelayed(() -> Utilities.restart(getActivity()), Utilities.WAIT_BEFORE_RESTART);
                    return true;
                }
            });
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            View listView = getListView();
            final int bottomPadding = listView.getPaddingBottom();
            listView.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        bottomPadding + insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            });
            // Overriding Text Direction in the Androidx preference library to support RTL
            view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
        }

        protected String getParentKeyForPref(String key) {
            return null;
        }

        /**
         * Initializes a preference. This is called for every preference. Returning false here
         * will remove that preference from the list.
         */
        protected boolean initPreference(Preference preference) {
            switch (preference.getKey()) {
                case NOTIFICATION_DOTS_PREFERENCE_KEY:
                    return !WidgetsModel.GO_DISABLE_NOTIFICATION_DOTS;

                case ALLOW_ROTATION_PREFERENCE_KEY:
                    DisplayController.Info info =
                            DisplayController.INSTANCE.get(getContext()).getInfo();
                    if (info.isTablet(info.realBounds)) {
                        // Launcher supports rotation by default. No need to show this setting.
                        return false;
                    }
                    // Initialize the UI once
                    preference.setDefaultValue(RotationHelper.getAllowRotationDefaultValue(info));
                    return true;

                case FLAGS_PREFERENCE_KEY:
                    // Only show flag toggler UI if this build variant implements that.
                    return FeatureFlags.showFlagTogglerUi(getContext());

                case DEVELOPER_OPTIONS_KEY:
                    mDeveloperOptionPref = preference;
                    return updateDeveloperOption();
            }

            return true;
        }

        /**
         * Show if plugins are enabled or flag UI is enabled.
         * @return True if we should show the preference option.
         */
        private boolean updateDeveloperOption() {
            boolean showPreference = FeatureFlags.showFlagTogglerUi(getContext())
                    || PluginManagerWrapper.hasPlugins(getContext());
            if (mDeveloperOptionPref != null) {
                mDeveloperOptionPref.setEnabled(showPreference);
                if (showPreference) {
                    getPreferenceScreen().addPreference(mDeveloperOptionPref);
                } else {
                    getPreferenceScreen().removePreference(mDeveloperOptionPref);
                }
            }
            return showPreference;
        }

        @Override
        public void onResume() {
            super.onResume();

            updateDeveloperOption();

            if (isAdded() && !mPreferenceHighlighted) {
                PreferenceHighlighter highlighter = createHighlighter();
                if (highlighter != null) {
                    getView().postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS);
                    mPreferenceHighlighted = true;
                } else {
                    requestAccessibilityFocus(getListView());
                }
            }
        }

        private PreferenceHighlighter createHighlighter() {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return null;
            }

            RecyclerView list = getListView();
            PreferencePositionCallback callback = (PreferencePositionCallback) list.getAdapter();
            int position = callback.getPreferenceAdapterPosition(mHighLightKey);
            return position >= 0 ? new PreferenceHighlighter(
                    list, position, screen.findPreference(mHighLightKey))
                    : null;
        }

        private void requestAccessibilityFocus(@NonNull final RecyclerView rv) {
            rv.post(() -> {
                if (!rv.hasFocus() && rv.getChildCount() > 0) {
                    rv.getChildAt(0)
                            .performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS, null);
                }
            });
        }

        private List<GridOption> parseAllGridOptions(int deviceType) {
            List<GridOption> result = new ArrayList<>();
            try (XmlResourceParser parser = getContext().getResources().getXml(R.xml.device_profiles)) {
                final int depth = parser.getDepth();
                int type;
                while (((type = parser.next()) != XmlPullParser.END_TAG ||
                        parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                    if ((type == XmlPullParser.START_TAG)
                            && GridOption.TAG_NAME.equals(parser.getName())) {
                        GridOption gridOption = new GridOption(getContext(), Xml.asAttributeSet(parser));
                        if (gridOption.isEnabled(deviceType)) {
                            result.add(gridOption);
                        }
                    }
                }
            } catch (IOException | XmlPullParserException e) {
                Log.e(TAG, "Error parsing device profile", e);
                return Collections.emptyList();
            }
            return result;
        }

        private boolean isSearchInstalled() {
            return PackageUtils.isAvailableApp(SEARCH_PACKAGE, getActivity());
        }
    }
}
