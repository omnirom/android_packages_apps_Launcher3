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
import android.net.Uri;
import android.provider.CalendarContract;
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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.Launcher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class CalendarView extends FrameLayout implements CalendarClient.CalendarEventObserver {

    private static final String TAG = "Launcher3:CalendarView";
    private static final boolean DEBUG = true;
    private static final int SHOW_NUM_EVENTS = 2;

    private View mCalendarData;
    private ListView mEventList;
    private View mProgressContainer;
    private View mCalendarStatus;
    private CalendarClient mCalendarClient;
    private List<CalendarEventModel.EventInfo> mEventData;
    private EventListAdapter mEventAdapter;
    private LayoutInflater mInflater;

    private class EventListAdapter extends ArrayAdapter<CalendarEventModel.EventInfo> {

        public EventListAdapter(Context context, List<CalendarEventModel.EventInfo> values) {
            super(context, R.layout.event_item, values);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final CalendarEventModel.EventInfo event = mEventData.get(position);
            convertView = mInflater.inflate(R.layout.event_item, parent, false);
            TextView eventTitle = (TextView) convertView.findViewById(R.id.event_title);
            TextView eventWhen = (TextView) convertView.findViewById(R.id.event_when);
            eventTitle.setText(event.title);
            eventWhen.setText(event.when);
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Launcher launcher = Launcher.getLauncher(getContext());
                    Intent intent = getEventIntent(event.id, 0, 0, false);
                    if (intent != null) {
                        launcher.startActivitySafely(null, intent, null);
                    }
                }
            });
            return convertView;
        }
    }

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
        if (permsChecked) {
            mCalendarClient = new CalendarClient(getContext(), this);
            mCalendarClient.register();
            mCalendarClient.load();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mProgressContainer = findViewById(R.id.progress_container);
        mCalendarData = findViewById(R.id.calendar_data);
        mCalendarStatus = findViewById(R.id.calendar_status);
        mEventList = (ListView) findViewById(R.id.event_list);

        mCalendarStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Launcher launcher = Launcher.getLauncher(getContext());
                launcher.requestCalendarPermission();
            }
        });

        mCalendarData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Launcher launcher = Launcher.getLauncher(getContext());
                Intent intent = getEventIntent(0, 0, 0, false);
                if (intent != null) {
                    launcher.startActivitySafely(null, intent, null);
                }
            }
        });

        mCalendarStatus.setVisibility(View.GONE);
        mEventData = new ArrayList<CalendarEventModel.EventInfo>();
        mEventAdapter = new EventListAdapter(getContext(), mEventData);
        mEventList.setAdapter(mEventAdapter);
    }


    public void startProgress() {
        mCalendarData.setVisibility(View.GONE);
        mProgressContainer.setVisibility(View.VISIBLE);
    }

    public void stopProgress() {
        mProgressContainer.setVisibility(View.GONE);
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        checkPermissions();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mCalendarClient != null) {
            mCalendarClient.unregister();
            mCalendarClient = null;
        }
    }

    @Override
    public void eventUpdates(CalendarEventModel model) {
        if (DEBUG) {
            Log.d(TAG, "eventUpdates");
        }
        stopProgress();
        mEventData.clear();
        int i = 0;
        for (CalendarEventModel.EventInfo event : model.mEventInfos) {
            if (event.allDay) {
                continue;
            }
            mEventData.add(event);
            i++;
            if (i == SHOW_NUM_EVENTS) {
                break;
            }
        }
        mEventAdapter.notifyDataSetChanged();
    }

    private Intent getEventIntent(long id, long start, long end, boolean allDay) {
        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        if (id != 0) {
            builder.appendPath("events");
            builder.appendPath(String.valueOf(id));
        } else {
            builder.appendPath("time");
            builder.appendPath(Long.toString(start));
        }
        Intent fillInIntent = new Intent(Intent.ACTION_VIEW, builder.build());
        fillInIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start);
        fillInIntent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
        fillInIntent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay);

        return fillInIntent;
    }
}
