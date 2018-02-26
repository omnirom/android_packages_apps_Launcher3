package com.google.android.apps.nexuslauncher;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.view.MenuItem;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dynamicui.WallpaperColorInfo;

public class AppDrawerActivity extends com.android.launcher3.SettingsActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    public final static String SHOW_PREDICTIONS_PREF = "pref_show_predictions";
    private static final String HIDDEN_APPS = "hidden_app";

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new AppDrawerFragment()).commit();
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

    public static class AppDrawerFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        private Context mContext;
        ActionBar actionBar;
        private SwitchPreference mAppSuggestions;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.appdrawer_preferences);

            mContext = getActivity();
            actionBar=getActivity().getActionBar();
            assert actionBar != null;
            actionBar.setDisplayHomeAsUpEnabled(true);

            mAppSuggestions = (SwitchPreference) findPreference(SHOW_PREDICTIONS_PREF);
            mAppSuggestions.setOnPreferenceChangeListener(this);

            findPreference(Utilities.TOP_SEARCH_BAR_KEY).setOnPreferenceChangeListener(this);

            Preference hiddenApp = findPreference(Utilities.KEY_HIDDEN_APPS);
            hiddenApp.setOnPreferenceClickListener(
                preference -> {
                    Intent intent = new Intent("com.android.launcher3.hideapps.HIDE_APPS")
                            .setPackage(getActivity().getPackageName());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getActivity().startActivity(intent);
                    return false;
            });
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }

        @Override
        public boolean onPreferenceChange(Preference preference, final Object newValue) {
            switch (preference.getKey()) {
                case SHOW_PREDICTIONS_PREF:
                    if ((boolean) newValue) {
                        return true;
                    }
                    AppDrawerActivity.SuggestionConfirmationFragment confirmationFragment = new AppDrawerActivity.SuggestionConfirmationFragment();
                    confirmationFragment.setTargetFragment(this, 0);
                    confirmationFragment.show(getFragmentManager(), preference.getKey());
                    break;
                case Utilities.TOP_SEARCH_BAR_KEY:
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    reloadTheme(mContext);
                    break;
            }
            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            return false;
        }
    }

    public static class SuggestionConfirmationFragment extends DialogFragment implements DialogInterface.OnClickListener {
        public void onClick(final DialogInterface dialogInterface, final int n) {
            if (getTargetFragment() instanceof PreferenceFragment) {
                Preference preference = ((PreferenceFragment) getTargetFragment()).findPreference(SHOW_PREDICTIONS_PREF);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setChecked(false);
                }
            }
        }

        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_disable_suggestions_prompt)
                    .setMessage(R.string.msg_disable_suggestions_prompt)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.label_turn_off_suggestions, this).create();
        }
    }

    public static void reloadTheme(Context context) {
        WallpaperColorInfo.getInstance(context).notifyChange(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
