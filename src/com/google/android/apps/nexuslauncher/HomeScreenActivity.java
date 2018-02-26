package com.google.android.apps.nexuslauncher;

import static com.android.launcher3.Utilities.getDevicePrefs;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.LooperExecutor;

import com.google.android.apps.nexuslauncher.smartspace.SmartspaceController;

public class HomeScreenActivity extends com.android.launcher3.SettingsActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";
    public final static String SMARTSPACE_PREF = "pref_smartspace";
    public static final String KEY_SHOW_WEATHER_CLOCK = "pref_show_clock_weather";

    private static final String GOOGLE_NOW_PACKAGE = "com.google.android.googlequicksearchbox";

    private static final long WAIT_BEFORE_RESTART = 250;

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new HomeSettingsFragment()).commit();
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

    public static class HomeSettingsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        private Context mContext;
        ActionBar actionBar;
        private SwitchPreference mGoogleNowPanel;
        private PreferenceScreen mAtGlanceWidget;
        private ListPreference mShowClockWeather;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.homescreen_preferences);

            mContext = getActivity();

            actionBar=getActivity().getActionBar();
            assert actionBar != null;
            actionBar.setDisplayHomeAsUpEnabled(true);

            ContentResolver resolver = getActivity().getContentResolver();

            mGoogleNowPanel = (SwitchPreference) findPreference(ENABLE_MINUS_ONE_PREF);
            mAtGlanceWidget = (PreferenceScreen) findPreference(SMARTSPACE_PREF);
            mShowClockWeather = (ListPreference) findPreference(KEY_SHOW_WEATHER_CLOCK);

            findPreference(Utilities.BOTTOM_SEARCH_BAR_KEY).setOnPreferenceChangeListener(this);

            findPreference(Utilities.GRID_COLUMNS).setOnPreferenceChangeListener(this);
            findPreference(Utilities.GRID_ROWS).setOnPreferenceChangeListener(this);
            findPreference(Utilities.HOTSEAT_ICONS).setOnPreferenceChangeListener(this);

            mGoogleNowPanel.setTitle(getDisplayGoogleTitle());
            if (!isPackageInstalled(GOOGLE_NOW_PACKAGE, mContext)) {
                mGoogleNowPanel.setEnabled(false);
                mGoogleNowPanel.setSelectable(false);
                mAtGlanceWidget.setEnabled(false);
                mAtGlanceWidget.setSelectable(false);
            } else {
                mGoogleNowPanel.setEnabled(true);
                mGoogleNowPanel.setSelectable(true);
                mAtGlanceWidget.setEnabled(true);
                mAtGlanceWidget.setSelectable(true);
                mAtGlanceWidget.setOnPreferenceClickListener(this);
            }

            mShowClockWeather.setValue(getDevicePrefs(mContext).getString(KEY_SHOW_WEATHER_CLOCK, "0"));

            mShowClockWeather.setOnPreferenceChangeListener(this);
        }

        private String getDisplayGoogleTitle() {
            CharSequence charSequence = null;
            try {
                Resources resourcesForApplication = mContext.getPackageManager().getResourcesForApplication(GOOGLE_NOW_PACKAGE);
                int identifier = resourcesForApplication.getIdentifier("title_google_home_screen", "string", GOOGLE_NOW_PACKAGE);
                if (identifier != 0) {
                    charSequence = resourcesForApplication.getString(identifier);
                }
            }
            catch (PackageManager.NameNotFoundException ex) {
            }
            if (TextUtils.isEmpty(charSequence)) {
                charSequence = mContext.getString(R.string.title_google_app);
            }
            return mContext.getString(R.string.title_show_google_app, charSequence);
        }

        private boolean isPackageInstalled(String package_name, Context context) {
            try {
                PackageManager pm = context.getPackageManager();
                pm.getPackageInfo(package_name, PackageManager.GET_ACTIVITIES);
                return true;
            } catch (Exception e) {
                return false;
            }
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
                case Utilities.BOTTOM_SEARCH_BAR_KEY:
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    restart(mContext);
                    break;
                case KEY_SHOW_WEATHER_CLOCK:
                    String value = (String) newValue;
                    getDevicePrefs(mContext).edit().putString(KEY_SHOW_WEATHER_CLOCK, value).commit();
                    mShowClockWeather.setValue(value);
                    restart(mContext);
                    break;
                case Utilities.GRID_COLUMNS:
                case Utilities.GRID_ROWS:
                case Utilities.HOTSEAT_ICONS:
                    if (preference instanceof ListPreference) {
                        ((ListPreference) preference).setValue((String) newValue);
                    }
                    restart(mContext);
                    break;
            }
            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (SMARTSPACE_PREF.equals(preference.getKey())) {
                SmartspaceController.get(mContext).cZ();
                return true;
            }
            return false;
        }
    }

    public static void restart(final Context context) {
        ProgressDialog.show(context, null, context.getString(R.string.state_loading), true, false);
        new LooperExecutor(LauncherModel.getWorkerLooper()).execute(new Runnable() {
            @SuppressLint("ApplySharedPref")
            @Override
            public void run() {
                try {
                    Thread.sleep(WAIT_BEFORE_RESTART);
                } catch (Exception e) {
                }

                Intent intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setPackage(context.getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 50, pendingIntent);

                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
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
