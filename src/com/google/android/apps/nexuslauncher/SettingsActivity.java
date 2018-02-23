package com.google.android.apps.nexuslauncher;

import static com.android.launcher3.Utilities.getDevicePrefs;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.app.ProgressDialog;
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
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.util.LooperExecutor;
import com.google.android.apps.nexuslauncher.AboutDialog;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceController;

public class SettingsActivity extends com.android.launcher3.SettingsActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    public final static String ICON_PACK_PREF = "pref_icon_pack";
    public final static String SHOW_PREDICTIONS_PREF = "pref_show_predictions";
    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";
    public final static String SMARTSPACE_PREF = "pref_smartspace";
    public static final String KEY_SHOW_WEATHER_CLOCK = "pref_show_clock_weather";

    private static final String GOOGLE_NOW_PACKAGE = "com.google.android.googlequicksearchbox";

    private static final long WAIT_BEFORE_RESTART = 250;

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new MySettingsFragment()).commit();
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

    public static class MySettingsFragment extends com.android.launcher3.SettingsActivity.LauncherSettingsFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        private Context mContext;

        private CustomIconPreference mIconPackPref;
        private SwitchPreference mAppSuggestions;
        private SwitchPreference mGoogleNowPanel;
        private PreferenceScreen mAtGlanceWidget;
        private ListPreference mShowClockWeather;

        @Override
        public void onCreate(Bundle bundle) {
            super.onCreate(bundle);

            mContext = getActivity();

            mIconPackPref = (CustomIconPreference) findPreference(ICON_PACK_PREF);
            mAppSuggestions = (SwitchPreference) findPreference(SHOW_PREDICTIONS_PREF);
            mGoogleNowPanel = (SwitchPreference) findPreference(ENABLE_MINUS_ONE_PREF);
            mAtGlanceWidget = (PreferenceScreen) findPreference(SMARTSPACE_PREF);
            mShowClockWeather = (ListPreference) findPreference(KEY_SHOW_WEATHER_CLOCK);

            findPreference(Utilities.BOTTOM_SEARCH_BAR_KEY).setOnPreferenceChangeListener(this);
            findPreference(Utilities.TOP_SEARCH_BAR_KEY).setOnPreferenceChangeListener(this);

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

            mIconPackPref.setOnPreferenceChangeListener(this);
            mAppSuggestions.setOnPreferenceChangeListener(this);
            mShowClockWeather.setOnPreferenceChangeListener(this);

            setHasOptionsMenu(true);
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
            mIconPackPref.reloadIconPacks();
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
            switch (preference.getKey()) {
                case ICON_PACK_PREF:
                    ProgressDialog.show(mContext,
                            null /* title */,
                            mContext.getString(R.string.state_loading),
                            true /* indeterminate */,
                            false /* cancelable */);

                    new LooperExecutor(LauncherModel.getWorkerLooper()).execute(new Runnable() {
                        @SuppressLint("ApplySharedPref")
                        @Override
                        public void run() {
                            // Clear the icon cache.
                            LauncherAppState.getInstance(mContext).getIconCache().clear();

                            // Wait for it
                            try {
                                Thread.sleep(1000);
                            } catch (Exception e) {
                                Log.e("SettingsActivity", "Error waiting", e);
                            }

                            if (Utilities.ATLEAST_MARSHMALLOW) {
                                // Schedule an alarm before we kill ourself.
                                Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_HOME)
                                        .setPackage(mContext.getPackageName())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                PendingIntent pi = PendingIntent.getActivity(mContext, 0,
                                        homeIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
                                getContext().getSystemService(AlarmManager.class).setExact(
                                        AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 50, pi);
                            }

                            // Kill process
                            android.os.Process.killProcess(android.os.Process.myPid());
                        }
                    });
                    return true;
                case SHOW_PREDICTIONS_PREF:
                    if ((boolean) newValue) {
                        return true;
                    }
                    SettingsActivity.SuggestionConfirmationFragment confirmationFragment = new SettingsActivity.SuggestionConfirmationFragment();
                    confirmationFragment.setTargetFragment(this, 0);
                    confirmationFragment.show(getFragmentManager(), preference.getKey());
                    break;
                case KEY_SHOW_WEATHER_CLOCK:
                    String value = (String) newValue;
                    getDevicePrefs(mContext).edit().putString(KEY_SHOW_WEATHER_CLOCK, value).commit();
                    mShowClockWeather.setValue(value);
                    restart(mContext);
                    break;
                case Utilities.BOTTOM_SEARCH_BAR_KEY:
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    restart(mContext);
                    break;
                case Utilities.TOP_SEARCH_BAR_KEY:
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    reloadTheme(mContext);
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

    private static void showAboutDialog(Fragment context, DialogFragment dialog) {
        FragmentTransaction ft = context.getChildFragmentManager().beginTransaction();
        Fragment prev = context.getChildFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        dialog.show(ft, "dialog");
    }

    public static void reloadTheme(Context context) {
        WallpaperColorInfo.getInstance(context).notifyChange(true);
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
                    Log.e("SettingsActivity", "Error waiting", e);
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
}
