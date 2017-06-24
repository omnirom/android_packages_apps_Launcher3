package com.android.launcher3;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconPackProvider {
    private static Map<String, IconPack> iconPacks = new ArrayMap<>();
    static final String ICON_MASK_TAG = "iconmask";
    static final String ICON_BACK_TAG = "iconback";
    static final String ICON_UPON_TAG = "iconupon";
    static final String ICON_SCALE_TAG = "scale";

    public static IconPack getIconPack(String packageName){
        return iconPacks.get(packageName);
    }

    public static IconPack loadAndGetIconPack(Context context){
        SharedPreferences prefs = Utilities.getPrefs(context);
        String packageName = prefs.getString("pref_iconPackPackage", "");
        if("".equals(packageName)){
            return null;
        }
        if(!iconPacks.containsKey(packageName)){
            loadIconPack(context, packageName);
        }
        return getIconPack(packageName);
    }

    public static void loadIconPack(Context context, String packageName) {
        if("".equals(packageName)){
            iconPacks.put("", null);
        }
        Map<String, String> appFilter;
        try {
            appFilter = parseAppFilter(getAppFilter(context, packageName));
        } catch (Exception e) {
            Toast.makeText(context, "Invalid IconPack", Toast.LENGTH_SHORT).show();
            iconPacks.put(packageName, null);
            return;
        }
        iconPacks.put(packageName, new IconPack(appFilter, context, packageName));
    }

    private static Map<String, String> parseAppFilter(XmlPullParser parser) throws Exception {
        Map<String, String> entries = new ArrayMap<>();
                
        Map<String, String> iconPackResources = new HashMap<String, String>();
        List<String> iconBackStrings = new ArrayList<String>();
        
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("item")) {
                String comp = parser.getAttributeValue(null, "component");
                String drawable = parser.getAttributeValue(null, "drawable");
                if (drawable != null && comp != null) {
                    entries.put(comp, drawable);
                }
                continue;
            }

            if (name.equalsIgnoreCase(ICON_BACK_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        iconBackStrings.add(parser.getAttributeValue(i));
                    }
                }
                continue;
            }

            if (name.equalsIgnoreCase(ICON_MASK_TAG) ||
                    name.equalsIgnoreCase(ICON_UPON_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    if (parser.getAttributeCount() > 0) {
                        icon = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), icon);
                continue;
            }

            if (name.equalsIgnoreCase(ICON_SCALE_TAG)) {
                String factor = parser.getAttributeValue(null, "factor");
                if (factor == null) {
                    if (parser.getAttributeCount() > 0) {
                        factor = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), factor);
                continue;
            }
        }
        return entries;
    }

    private static XmlPullParser getAppFilter(Context context, String packageName) {
        Resources res;
        try {
            res = context.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier("appfilter", "xml", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getXml(packageName, resourceId, null);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(context, "Failed to get AppFilter", Toast.LENGTH_SHORT).show();
        }
        return null;
    }
}
