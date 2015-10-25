package com.android.launcher3;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.util.Log;

public final class LauncherPreferences {
        public static final String KEY_WORKSPACE_ROWS = "pref_key_workspaceRows";
        public static final String KEY_WORKSPACE_COLS = "pref_key_workspaceCols";

        public static final String KEY_WORKSPACE_DEFAULT_PAGE = "pref_key_workspaceDefaultPage";

        public static final String KEY_SHOW_SEARCHBAR = "pref_key_showSearchBar";

        private static final String TAG = "LauncherPreferences";

        public static class PrefsFragment  extends PreferenceFragment {
            @Override
            public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);

                // Load the preferences from an XML resource
                addPreferencesFromResource(R.xml.preferences );

                SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
                InvariantDeviceProfile prof = LauncherAppState.getInstance().getInvariantDeviceProfile();

                if(prof != null) {
                        // initialize default values from current Profile                       
                        SharedPreferences.Editor editor = prefs.edit();

                        if(prefs.getInt(KEY_WORKSPACE_ROWS, 0) < 1) {
                                Log.i(TAG,"Loading r default value: "+prof.numRows);
                                editor.putInt(KEY_WORKSPACE_ROWS, prof.numRows);
                        }
                        if(prefs.getInt(KEY_WORKSPACE_COLS, 0) < 1) {
                                Log.i(TAG,"Loading c default value: "+prof.numColumns);
                                editor.putInt(KEY_WORKSPACE_COLS, prof.numColumns);
                        }

                        editor.apply();
                }
                else {
                        Log.w(TAG, "No DeviceProfile to get default values!");
            }
            }
        }

        private LauncherPreferences() {}

        public static boolean isLauncherPreference(String key) {
                return key.equals(KEY_WORKSPACE_ROWS)
                                || key.equals(KEY_WORKSPACE_COLS)
                                || key.equals(KEY_WORKSPACE_DEFAULT_PAGE)
                                || key.equals(KEY_SHOW_SEARCHBAR);
        }
}
