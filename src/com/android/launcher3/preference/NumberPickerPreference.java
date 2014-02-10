/*
 * Copyright (C) 2015 The SlimRoms Project
 * part of this code is copyright to The Omnirom Project
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

package com.android.launcher3.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.launcher3.R;

public class NumberPickerPreference extends DialogPreference {
    private int mMin, mMax, mDefault;

    private String mMaxExternalKey, mMinExternalKey;
    private Preference mMaxExternalPreference, mMinExternalPreference;

    private boolean mEnabled;

    private NumberPicker mNumberPicker;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray prefType = context.obtainStyledAttributes(attrs,
                R.styleable.Preference, 0, 0);
        TypedArray numberPickerType = context.obtainStyledAttributes(attrs,
                R.styleable.NumberPickerPreference, 0, 0);

        mMaxExternalKey = numberPickerType.getString(R.styleable.NumberPickerPreference_maxExternal);
        mMinExternalKey = numberPickerType.getString(R.styleable.NumberPickerPreference_minExternal);

        mMax = prefType.getInt(R.styleable.Preference_max, 5);
        mMin = prefType.getInt(R.styleable.Preference_min, 0);

        mDefault = prefType.getInt(R.styleable.Preference_defaultValue, mMin);

        prefType.recycle();
        numberPickerType.recycle();
    }

    protected void onAttachedToActivity() {
        // External values
        if (mMaxExternalKey != null) {
            Preference maxPreference = findPreferenceInHierarchy(mMaxExternalKey);
            if (maxPreference != null) {
                if (maxPreference instanceof NumberPickerPreference) {
                    mMaxExternalPreference = maxPreference;
                }
            }
        }
        if (mMinExternalKey != null) {
            Preference minPreference = findPreferenceInHierarchy(mMinExternalKey);
            if (minPreference != null) {
                if (minPreference instanceof NumberPickerPreference) {
                    mMinExternalPreference = minPreference;
                }
            }
        }
    }

    public void setDefaultValue(int defaultValue) {
        mDefault = defaultValue;
    }

    @Override
    protected View onCreateDialogView() {
        int max = mMax;
        int min = mMin;

        // External values
        if (mMaxExternalKey != null && mMaxExternalPreference != null) {
            max = mMaxExternalPreference.getSharedPreferences().getInt(mMaxExternalKey, mMax);
        }
        if (mMinExternalKey != null && mMinExternalPreference != null) {
            min = mMinExternalPreference.getSharedPreferences().getInt(mMinExternalKey, mMin);
        }

        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.number_picker_dialog, null);

        mNumberPicker = (NumberPicker) view.findViewById(R.id.number_picker);

        // Initialize state
        mNumberPicker.setMaxValue(max);
        mNumberPicker.setMinValue(min);
        mNumberPicker.setValue(getPersistedInt(mDefault));
        mNumberPicker.setWrapSelectorWheel(false);
        mNumberPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        return view;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            persistInt(mNumberPicker.getValue());
        }
    }

}

