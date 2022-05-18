package com.android.launcher3;

import android.app.Activity;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.os.Bundle;

public class AllAppsShortcutActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = "android.intent.action.ALL_APPS";
        Intent shortcutIntent = new Intent();
        shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(action));
        shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.all_apps_label));
        shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, ShortcutIconResource.fromContext(this,
                R.mipmap.ic_apps_shortcut));
        setResult(RESULT_OK, shortcutIntent);

        finish();
    }
}
