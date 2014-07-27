package com.android.launcher3;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;

import java.io.InputStream;

public final class LauncherPreferences {
        public static final String KEY_WORKSPACE_ROWS = "pref_key_workspaceRows";
        public static final String KEY_WORKSPACE_COLS = "pref_key_workspaceCols";
        public static final String KEY_WORKSPACE_DEFAULT_PAGE = "pref_key_workspaceDefaultPage";
        public static final String KEY_SHOW_SEARCHBAR = "pref_key_showSearchBar";
        public static final String KEY_ICON_PACK = "pref_key_iconpack";
        public static final String KEY_ENABLE_HOTWORD = "pref_key_enableHotword";
        public static final String KEY_CUSTOM_HOTWORDS = "pref_key_customHotwords";
        public static final String KEY_DRAWER_ICON = "pref_key_drawer_icon";

        private static final int READ_ICON_CODE = 42;

        private static final String TAG = "LauncherPreferences";

        /**
         * Main Preferences fragment
         */
        public static class PrefsFragment  extends PreferenceFragment {
            private Preference mIconpack;
            private Preference mCustomHotwords;
            private Preference mDrawerIcon;
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
                mDrawerIcon = findPreference(KEY_DRAWER_ICON);

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
                } else if (preference == mDrawerIcon) {
                    pickDrawerIcon();
                    return true;
                }
                return false;
            }

            private void pickDrawerIcon() {
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

                CharSequence[] items = new CharSequence[]{
                    mContext.getString(R.string.drawer_icon_default),
                    mContext.getString(R.string.drawer_icon_custom)
                };
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            setAllAppsIcon(null);
                        } else {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("image/*");
                            getActivity().startActivityForResult(intent, READ_ICON_CODE);
                        }
                    }
                });
                builder.create().show();

            }

            public void onActivityResult(int requestCode, int resultCode,
                    Intent resultData) {
                if (requestCode == READ_ICON_CODE && resultCode == Activity.RESULT_OK) {
                    if (resultData != null) {
                        Uri uri = resultData.getData();
                        setAllAppsIcon(uri.toString());
                    }
                }
            }

            private void setAllAppsIcon(String uri) {
                SharedPreferences prefs = mContext.getSharedPreferences(Hotseat.PREFS_HOTSEAT, 0);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putString(Hotseat.PREF_HOTSEAT_ALLAPPS_IMAGE, uri);
                edit.apply();
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
                                || key.equals(KEY_CUSTOM_HOTWORDS)
                                || key.equals(KEY_DRAWER_ICON);
        }
}
