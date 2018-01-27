package com.google.android.apps.nexuslauncher;

import android.content.res.Configuration;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.ComponentKeyMapper;
import com.google.android.libraries.launcherclient.GoogleNow;

import java.util.List;

public class NexusLauncherActivity extends Launcher {
    private NexusLauncher mLauncher;

    public NexusLauncherActivity() {
        mLauncher = new NexusLauncher(this);
    }

    public void overrideTheme(boolean isDark, boolean supportsDarkText, boolean forceDark, boolean forceLight) {
        int flags = Utilities.getDevicePrefs(this).getInt("pref_persistent_flags", 0);
        int orientFlag = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 16 : 8;
        boolean useGoogleInOrientation = (orientFlag & flags) != 0;

        if (forceDark || (useGoogleInOrientation && isDark && !forceLight)) {
            setTheme(R.style.GoogleSearchLauncherThemeDark);
        // else if forcelight or wallpaper based use light theme or light theme plus dark text if needed by the wallpaper
        } else if (useGoogleInOrientation && supportsDarkText && Utilities.ATLEAST_NOUGAT) {
            setTheme(R.style.GoogleSearchLauncherThemeDarkText);
        } else if (useGoogleInOrientation) {
            setTheme(R.style.GoogleSearchLauncherTheme);
        // if !useGoogleInOrientation fallback to Launcher.overrideTheme
        } else {
            super.overrideTheme(isDark, supportsDarkText, forceDark, forceLight);
        }
    }

    public List<ComponentKeyMapper<AppInfo>> getPredictedApps() {
        return mLauncher.fA.getPredictedApps();
    }

    public GoogleNow getGoogleNow() {
        return mLauncher.fy;
    }
}
