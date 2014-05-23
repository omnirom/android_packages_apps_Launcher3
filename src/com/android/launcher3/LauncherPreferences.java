package com.android.launcher3;

import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;

public final class LauncherPreferences {
        public static final String KEY_WORKSPACE_ROWS = "pref_key_workspaceRows";
        public static final String KEY_WORKSPACE_COLS = "pref_key_workspaceCols";
        public static final String KEY_WORKSPACE_DEFAULT_PAGE = "pref_key_workspaceDefaultPage";
        public static final String KEY_SHOW_SEARCHBAR = "pref_key_showSearchBar";
        public static final String KEY_ICON_PACK = "pref_key_iconpack";
        public static final String KEY_ENABLE_HOTWORD = "pref_key_enableHotword";
        public static final String KEY_CUSTOM_HOTWORDS = "pref_key_customHotwords";

        private static final String TAG = "LauncherPreferences";

        /**
         * Main Preferences fragment
         */
        public static class PrefsFragment  extends PreferenceFragment {
            private Preference mIconpack;
            private Preference mCustomHotwords;
            private LauncherPreferencesActivity mContext;

            public PrefsFragment() {

            }

            public PrefsFragment(LauncherPreferencesActivity context) {
                mContext = context;
            }

            @Override
            public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);

                // Load the preferences from an XML resource
                addPreferencesFromResource(R.xml.preferences);

                SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

                DynamicGrid grid = LauncherAppState.getInstance().getDynamicGrid();

                if(grid != null) {
                        // initialize default values from current Profile
                        DeviceProfile prof = grid.getDeviceProfile();

                        SharedPreferences.Editor editor = prefs.edit();

                        if(prefs.getInt(KEY_WORKSPACE_ROWS, 0) < 1) {
                                Log.i(TAG,"Loading r default value from: "+grid.toString());
                                editor.putInt(KEY_WORKSPACE_ROWS, (int)prof.numRows);
                        }
                        if(prefs.getInt(KEY_WORKSPACE_COLS, 0) < 1) {
                                Log.i(TAG,"Loading c default value from: "+grid.toString());
                                editor.putInt(KEY_WORKSPACE_COLS, (int)prof.numColumns);
                        }

                        editor.apply();
                }
                else {
                        Log.w(TAG, "No DynamicGrid to get default values!");
                }
                mIconpack = findPreference(KEY_ICON_PACK);
                mCustomHotwords = findPreference(KEY_CUSTOM_HOTWORDS);

                if (mContext == null) {
                    mContext = (LauncherPreferencesActivity) getActivity();
                }
            }
            @Override
            public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                    Preference preference) {
                if (preference == mIconpack){
                    IconPackHelper.pickIconPack(mContext, false);
                    return true;
                } else if (preference == mCustomHotwords) {
                    getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, new HotwordCustomFragment())
                        .addToBackStack(null)
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .setBreadCrumbTitle(R.string.pref_hotwords_title)
                        .commit();
                }
                return false;
            }
        }

        private LauncherPreferences() {}

        public static boolean isLauncherPreference(String key) {
                return key.equals(KEY_WORKSPACE_ROWS)
                                || key.equals(KEY_WORKSPACE_COLS)
                                || key.equals(KEY_WORKSPACE_DEFAULT_PAGE)
                                || key.equals(KEY_SHOW_SEARCHBAR)
                                || key.equals(KEY_ICON_PACK)
                                || key.equals(KEY_ENABLE_HOTWORD)
                                || key.equals(KEY_CUSTOM_HOTWORDS);
        }
}
