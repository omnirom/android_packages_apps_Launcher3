package com.android.launcher3;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.launcher3.LauncherPreferences.PrefsFragment;

public class LauncherPreferencesActivity extends Activity {
        private PrefsFragment mFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

	    // Display the fragment as the main content.
            mFragment = new PrefsFragment(this);
	    getFragmentManager().beginTransaction()
	            .replace(android.R.id.content, mFragment)
	            .commit();
	}

        @Override
        public void onActivityResult(int requestCode, int resultCode,
                Intent resultData) {
            mFragment.onActivityResult(requestCode, resultCode, resultData);
        }
}
