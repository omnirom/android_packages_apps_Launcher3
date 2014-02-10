/*
 * Copyright (C) 2015 The SlimRoms Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SettingsProvider implements SettingsKeys {

    public static SharedPreferences get(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static SharedPreferences.Editor put(Context context) {
        return get(context).edit();
    }

    public static int getCellCountX(Context context, String key, int def) {
        String[] values = get(context).getString(key, "0|" + def).split("\\|");
        try {
            return Integer.parseInt(values[1]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static void putCellCountX(Context context, String key, int value) {
        String[] values = get(context).getString(key, "0|0").split("\\|");
        put(context).putString(key, values[0] + "|" + value);
    }

    public static int getCellCountY(Context context, String key, int def) {
        String[] values = get(context).getString(key, def + "|0").split("\\|");
        try {
            return Integer.parseInt(values[0]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static void putCellCountY(Context context, String key, int value) {
        String[] values = get(context).getString(key, "0|0").split("\\|");
        put(context).putString(key, value + "|" + values[1]);
    }

    public static String getString(Context context, String key, String defValue) {
        return get(context).getString(key, defValue);
    }

    public static void putString(Context context, String key, String value) {
        put(context).putString(key, value).commit();
    }

    public static boolean getBoolean(Context context, String key, boolean defValue) {
        return get(context).getBoolean(key, defValue);
    }

    public static void putBoolean(Context context, String key, boolean value) {
        put(context).putBoolean(key, value).commit();
    }

    public static long getLong(Context context, String key, long defValue) {
        return get(context).getLong(key, defValue);
    }

    public static void putLong(Context context, String key, long value) {
        put(context).putLong(key, value).commit();
    }

    public static int getInt(Context context, String key, int defValue) {
        return get(context).getInt(key, defValue);
    }

    public static void putInt(Context context, String key, int value) {
        put(context).putInt(key, value);
    }

    public static boolean shouldFinish(String key) {
        return !key.equals(DEFAULT_HOMESCREEN);
    }
}

