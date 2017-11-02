/*
* Copyright (C) 2017 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.launcher3.topwidget;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.Launcher;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class CalendarView extends FrameLayout {

    static final String TAG = "Launcher3:CalendarView";
    static final boolean DEBUG = true;

    private View mCalendarData;
    private View mProgressContainer;
    private View mCalendarStatus;

    public CalendarView(Context context) {
        this(context, null);
    }

    public CalendarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CalendarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void checkPermissions() {
        Launcher launcher = Launcher.getLauncher(getContext());
        boolean permsChecked = launcher.isCalendarPermissionEnabled();
        mCalendarData.setVisibility(permsChecked ? View.VISIBLE : View.GONE);
        mCalendarStatus.setVisibility(permsChecked ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mProgressContainer = findViewById(R.id.progress_container);
        mCalendarData = findViewById(R.id.calendar_data);
        mCalendarStatus = findViewById(R.id.calendar_status);

        mCalendarStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Launcher launcher = Launcher.getLauncher(getContext());
                launcher.requestCalendarPermission();
            }
        });
        mCalendarStatus.setVisibility(View.GONE);
    }


    public void startProgress() {
        mCalendarData.setVisibility(View.GONE);
        mProgressContainer.setVisibility(View.VISIBLE);
    }

    public void stopProgress() {
        mProgressContainer.setVisibility(View.GONE);
    }
}
