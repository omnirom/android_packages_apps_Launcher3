/*
 * Copyright (C) 2015 The OmniRom Project
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

import android.content.SharedPreferences;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;

import java.util.List;

public class SettingsActivity extends PreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onResume() {
        super.onResume();
        SettingsProvider.get(this).registerOnSharedPreferenceChangeListener(this);

        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        SettingsProvider.get(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preferences, target);
        updateHeaders(target);
    }

    private void updateHeaders(List<Header> headers) {
        /*for (Header header : headers) {
            if (header.id == R.id.application_version) {
                header.title = getString(R.string.application_name) + " "
                        + getString(R.string.application_version);
            }
        }*/
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        LauncherAppState.setSettingsChanged();
    }
}

