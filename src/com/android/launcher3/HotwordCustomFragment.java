/*
 * Copyright (C) 2014 The OmniROM Project
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

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.util.cm.LockscreenTargetUtils;
import com.android.launcher3.ShortcutPickHelper.OnPickListener;

public class HotwordCustomFragment extends PreferenceFragment {
    static final String TAG = "HotwordCustomFragment";

    public static final String PREFS_HOTWORDS = "prefs_hotwords";
    public static final String PREFS_HOTWORDS_ENTRIES = "prefs_hotwords_entries";
    public static final String PREFS_PREFIX_HOTWORD = "hotword__";
    public static final String PREFS_PREFIX_ACTION = "action__";

    PreferenceGroup mPrefGroup;
    Map<String, HotwordEntryPreference> mPrefs;
    Button mPickerButton;
    ShortcutPickHelper mPicker;
    SharedPreferences mPrefStorage;
    String mEditKey;

    public HotwordCustomFragment() {
        mPrefs = new HashMap<String, HotwordEntryPreference>();
    }

    OnClickListener mHotwordClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            String key = (String) v.getTag();
            showCustomDialog(key);
        }
    };

    OnClickListener mDeleteClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final String key = (String) v.getTag();
            removeHotword(key);
            buildHotwordsList();
        }
    };

    void removeHotword(String key) {
        SharedPreferences.Editor editor = mPrefStorage.edit();
        Set<String> entries = mPrefStorage.getStringSet(PREFS_HOTWORDS_ENTRIES, null);
        entries.remove(key);
        editor.putStringSet(PREFS_HOTWORDS_ENTRIES, entries);
        editor.apply();
    }

    void buildHotwordsList() {
        Context context = getActivity();

        mPrefGroup.removeAll();
        mPrefs.clear();

        Set<String> entries = mPrefStorage.getStringSet(PREFS_HOTWORDS_ENTRIES, null);

        if (entries != null) {
            for (String entry : entries) {
                String hotword = mPrefStorage.getString(PREFS_PREFIX_HOTWORD + entry, "ERROR");
                String action = mPrefStorage.getString(PREFS_PREFIX_ACTION + entry, "ERROR");

                Intent intent = null;
                try {
                    intent = Intent.parseUri(action, 0);
                } catch (URISyntaxException e) {
                    Log.e(TAG, "Error while parsing intent: " + intent, e);
                    continue;
                }

                HotwordEntryPreference pref = new HotwordEntryPreference(context, entry, hotword,
                        intent, this);
                mPrefs.put(entry, pref);
                mPrefGroup.addPreference(pref);
                pref.setEnabled(true);
            }
        }
    }

    void recordHotword(CharSequence hotword, String intent) {
        SharedPreferences.Editor editor = mPrefStorage.edit();

        final String key = mEditKey == null ? Long.toString(System.currentTimeMillis()) : mEditKey;

        editor.putString(PREFS_PREFIX_HOTWORD + key, hotword.toString());
        editor.putString(PREFS_PREFIX_ACTION + key, intent);


        Set<String> entries = mPrefStorage.getStringSet(PREFS_HOTWORDS_ENTRIES, null);
        if (entries == null) {
            entries = new TreeSet<String>();
        }
        entries.add(key);
        editor.putStringSet(PREFS_HOTWORDS_ENTRIES, entries);

        editor.apply();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.hotword_customization);

        mPrefGroup = (PreferenceGroup) findPreference("hotword_custom_container");

        mPrefStorage = getActivity().getSharedPreferences(PREFS_HOTWORDS, 0);

        Preference addNew = findPreference("hotword_add");
        addNew.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showCustomDialog(null);
                return true;
            }
        });

        mPicker = new ShortcutPickHelper(getActivity(), new OnPickListener() {
            @Override
            public void shortcutPicked(String uri, String friendlyName,
                    boolean isApplication) {
                if (uri == null) {
                    return;
                }

                try {
                    Intent intent = Intent.parseUri(uri, 0);
                    Drawable icon = LockscreenTargetUtils.getDrawableFromIntent(getActivity(), intent);

                    mPickerButton.setCompoundDrawables(icon, null, null, null);
                    mPickerButton.setText(friendlyName);
                    mPickerButton.setTag(uri);
                } catch (URISyntaxException e) {
                    Log.wtf(TAG, "Invalid uri " + uri + " on pick");
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        buildHotwordsList();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        String shortcutName = null;
        if (data != null) {
            shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        }

        if (TextUtils.equals(shortcutName, "")) {
            mPickerButton.setText("");
            mPickerButton.setTag("");
            mPickerButton.setCompoundDrawables(null, null, null, null);
        } else if (requestCode != Activity.RESULT_CANCELED
                && resultCode != Activity.RESULT_CANCELED) {
            mPicker.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void showCustomDialog(String editEntry) {
        LayoutInflater inflater =
                (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View root = inflater.inflate(R.layout.dialog_custom_hotword, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(root);
        builder.setTitle(R.string.dialog_hotword_title);

        final EditText hotwordEdit = (EditText) root.findViewById(R.id.hotword);

        // OK button: save and update
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                recordHotword(hotwordEdit.getText().toString(), (String) mPickerButton.getTag());
                buildHotwordsList();
            }
        });

        // Cancel button: dismiss and don't save
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Listener on the action picker button
        mPickerButton = (Button) root.findViewById(R.id.action);
        mPickerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] names = new String[] { "" };
                ShortcutIconResource[] icons = new ShortcutIconResource[] {
                    ShortcutIconResource.fromContext(getActivity(), android.R.drawable.ic_delete)
                };
                mPicker.pickShortcut(names, icons, getId());
            }
        });

        // If we have data, fill it in
        if (editEntry != null) {
            HotwordEntryPreference entry = mPrefs.get(editEntry);

            mPickerButton.setTag(entry.action.toUri(0));
            mPickerButton.setText(entry.actionView.getText());

            hotwordEdit.setText(entry.getTitle());

            mEditKey = editEntry;
        } else {
            mEditKey = null;
        }

        builder.create().show();
    }

    class HotwordEntryPreference extends Preference {
        String key;
        HotwordCustomFragment fragment;
        TextView actionView;
        Intent action;

        public HotwordEntryPreference(Context ctx, String key, CharSequence title, Intent action,
                HotwordCustomFragment parent) {
            super(ctx);
            setLayoutResource(R.layout.preference_hotword_custom);
            setTitle(title);
            fragment = parent;
            this.action = action;
            this.key = key;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);

            ImageView remove = (ImageView) view
                    .findViewById(R.id.remove_hotword);
            remove.setOnClickListener(mDeleteClickListener);
            remove.setTag(key);

            View v = view.findViewById(R.id.hotword_pref_entry);
            v.setOnClickListener(mHotwordClickListener);
            v.setTag(key);

            actionView = (TextView) view.findViewById(R.id.action);
            setAction(action);
        }

        public void setAction(Intent action) {
            actionView.setText(mPicker.getFriendlyNameForUri(action.toUri(0)));
        }
    }
}
