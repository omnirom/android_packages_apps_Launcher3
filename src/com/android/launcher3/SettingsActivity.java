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
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.apps.nexuslauncher.AboutDialog;

/**
 * Settings activity for Launcher.
 */
public class SettingsActivity extends Activity implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new LauncherSettingsFragment()).commit();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment, Preference preference) {
        Fragment instantiate = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        if (instantiate instanceof DialogFragment) {
            ((DialogFragment) instantiate).show(getFragmentManager(), preference.getKey());
        } else {
            getFragmentManager().beginTransaction().replace(android.R.id.content, instantiate).addToBackStack(preference.getKey()).commit();
        }
        return true;
    }

    public static class LauncherSettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        private Context mContext;

        @Override
        public void onCreate(Bundle bundle) {
            super.onCreate(bundle);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.launcher_preferences);

            setHasOptionsMenu(true);
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            menu.add(0, 0, 0, R.string.about_credits)
                    .setIcon(R.drawable.ic_dialog_alert)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case 0:
                    final AboutDialog dialog = new AboutDialog();
                    showAboutDialog(this, dialog);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, final Object newValue) {
            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            return false;
        }
    }

    private static void showAboutDialog(Fragment context, DialogFragment dialog) {
        FragmentTransaction ft = context.getChildFragmentManager().beginTransaction();
        Fragment prev = context.getChildFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        dialog.show(ft, "dialog");
    }
}
