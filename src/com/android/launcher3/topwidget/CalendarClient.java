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

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


public class CalendarClient {
    private static final String TAG = "Launcher3:CalendarClient";
    private static final boolean DEBUG = true;

    private static final int EVENT_MAX_COUNT = 100;
    private static final int UPDATE_THROTTLE = 500;
    private static final long UPDATE_TIME_NO_EVENTS = DateUtils.HOUR_IN_MILLIS * 6;

    private static final String EVENT_SORT_ORDER = Instances.START_DAY + " ASC, "
            + Instances.START_MINUTE + " ASC, " + Instances.END_DAY + " ASC, "
            + Instances.END_MINUTE + " ASC LIMIT " + EVENT_MAX_COUNT;

    private static final String EVENT_SELECTION = Calendars.VISIBLE + "=1";
    private static final String EVENT_SELECTION_HIDE_DECLINED = Calendars.VISIBLE + "=1 AND "
            + Instances.SELF_ATTENDEE_STATUS + "!=" + Attendees.ATTENDEE_STATUS_DECLINED;

    private static final String[] EVENT_PROJECTION = new String[] {
        Instances.ALL_DAY,
        Instances.BEGIN,
        Instances.END,
        Instances.TITLE,
        Instances.EVENT_LOCATION,
        Instances.EVENT_ID,
        Instances.START_DAY,
        Instances.END_DAY,
        Instances.DISPLAY_COLOR, // If SDK < 16, set to Instances.CALENDAR_COLOR.
        Instances.SELF_ATTENDEE_STATUS,
    };

    private static final String UPDATE_BROADCAST  = "org.omnirom.omniextras.calendar.UPDATE";
    private static final String UPDATE_DATA_TYPE = "vnd.android.data/update";
    private CalendarFactory mFactory;

    public interface CalendarEventObserver {
        public void eventUpdates(CalendarEventModel model);
    }

    public static class CalendarFactory extends BroadcastReceiver implements
             Loader.OnLoadCompleteListener<Cursor> {

        private Context mContext;
        private CalendarEventModel mModel;
        private static Object mLock = new Object();
        private static volatile int mSerialNum = 0;
        private int mLastSerialNum = -1;
        private CursorLoader mLoader;
        private final Handler mHandler = new Handler();
        private static final AtomicInteger currentVersion = new AtomicInteger(0);
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private CalendarEventObserver mObserver;

        private final ContentObserver mCalendarObserver = new ContentObserver(mHandler) {
            @Override
            public boolean deliverSelfNotifications() {
                return true;
            }

            @Override
            public void onChange(boolean selfChange) {
                reloadEvents();
            }
        };

        private Runnable createUpdateLoaderRunnable(final String selection, final int version) {
            return new Runnable() {
                @Override
                public void run() {
                    // If there is a newer load request in the queue, skip loading.
                    if (mLoader != null && version >= currentVersion.get()) {
                        Uri uri = createLoaderUri();
                        mLoader.setUri(uri);
                        mLoader.setSelection(selection);
                        synchronized (mLock) {
                            mLastSerialNum = ++mSerialNum;
                        }
                        mLoader.forceLoad();
                    }
                }
            };
        }

        protected CalendarFactory(Context context, CalendarEventObserver observer) {
            mContext = context;
            mObserver = observer;
        }

        private long getSearchDuration() {
            // TODO maxwen - make config
            return 14 * DateUtils.DAY_IN_MILLIS;
        }

        private void initLoader(String selection) {
            if (!isPermissionEnabled()) {
                Log.e(TAG, "initLoader blocked because of missing permission");
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "Querying for events...");
            }
            // Search for events from now until some time in the future
            Uri uri = createLoaderUri();
            mLoader = new CursorLoader(mContext, uri, EVENT_PROJECTION, selection, null,
                    EVENT_SORT_ORDER);
            mLoader.setUpdateThrottle(UPDATE_THROTTLE);
            synchronized (mLock) {
                mLastSerialNum = ++mSerialNum;
            }
            mLoader.registerListener(0, this);
            mLoader.startLoading();

        }

        private String queryForSelection() {
            return EVENT_SELECTION_HIDE_DECLINED;
        }

        private Uri createLoaderUri() {
            long now = System.currentTimeMillis();
            // Add a day on either side to catch all-day events
            long begin = now - DateUtils.DAY_IN_MILLIS;
            long end = now + getSearchDuration() + DateUtils.DAY_IN_MILLIS;

            Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI, Long.toString(begin) + "/" + end);
            return uri;
        }

        private void buildAppWidgetModel(Cursor cursor, String timeZone) {
            mModel = new CalendarEventModel(mContext, timeZone);
            mModel.buildFromCursor(cursor, timeZone);
        }

        private long calculateUpdateTime(CalendarEventModel model, long now, String timeZone) {
            // Make sure an update happens at midnight or earlier
            long minUpdateTime = getNextMidnightTimeMillis(timeZone);
            for (CalendarEventModel.EventInfo event : model.mEventInfos) {
                final long start;
                final long end;
                start = event.start;
                end = event.end;

                // We want to update widget when we enter/exit time range of an event.
                if (now < start) {
                    minUpdateTime = Math.min(minUpdateTime, start);
                } else if (now < end) {
                    minUpdateTime = Math.min(minUpdateTime, end);
                }
            }
            return minUpdateTime;
        }

        private static long getNextMidnightTimeMillis(String timezone) {
            Time time = new Time();
            time.setToNow();
            time.monthDay++;
            time.hour = 0;
            time.minute = 0;
            time.second = 0;
            long midnightDeviceTz = time.normalize(true);

            time.timezone = timezone;
            time.setToNow();
            time.monthDay++;
            time.hour = 0;
            time.minute = 0;
            time.second = 0;
            long midnightHomeTz = time.normalize(true);

            return Math.min(midnightDeviceTz, midnightHomeTz);
        }

        @Override
        public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null) {
                return;
            }
            // If a newer update has happened since we started clean up and
            // return
            synchronized (mLock) {
                if (cursor.isClosed()) {
                    Log.wtf(TAG, "Got a closed cursor from onLoadComplete");
                    return;
                }

                if (mLastSerialNum != mSerialNum) {
                    return;
                }

                final long now = System.currentTimeMillis();
                String tz = Time.getCurrentTimezone();

                // Copy it to a local static cursor.
                MatrixCursor matrixCursor = matrixCursorFromCursor(cursor);
                try {
                    buildAppWidgetModel(matrixCursor, tz);
                } finally {
                    if (matrixCursor != null) {
                        matrixCursor.close();
                    }

                    if (cursor != null) {
                        cursor.close();
                    }
                }

                // Schedule an alarm to wake ourselves up for the next update.
                long triggerTime = calculateUpdateTime(mModel, now, tz);
                if (DEBUG) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
                    Log.d(TAG, "next update = " + sdf.format(triggerTime));
                }

                // If no next-update calculated
                // update about six hours from now.
                if (triggerTime < now) {
                    triggerTime = now + UPDATE_TIME_NO_EVENTS;
                }

                final AlarmManager alertManager = (AlarmManager) mContext
                        .getSystemService(Context.ALARM_SERVICE);
                final PendingIntent pendingUpdate = getUpdateIntent(mContext);

                alertManager.cancel(pendingUpdate);
                alertManager.set(AlarmManager.RTC, triggerTime, pendingUpdate);

                if (mObserver != null) {
                    mObserver.eventUpdates(mModel);
                }
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG)
                Log.d(TAG, "onReceive: " + intent.toString());
            String action = intent.getAction();

            if (action.equals(UPDATE_BROADCAST)
                    || Intent.ACTION_DATE_CHANGED.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                    || Intent.ACTION_LOCALE_CHANGED.equals(action)
                    || Intent.ACTION_SCREEN_ON.equals(action)) {
                mContext = context;
                reloadEvents();
            }
        }

        private void reloadEvents() {
            executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        final String selection = queryForSelection();
                        mHandler.post(createUpdateLoaderRunnable(selection, currentVersion.incrementAndGet()));
                    }
            });
        }

        private boolean isPermissionEnabled() {
            if (mContext.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED ||
                mContext.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                        != PackageManager.PERMISSION_GRANTED ||
                mContext.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
            }
            return true;
        }

        public void load() {
            String selection = queryForSelection();
            initLoader(selection);
        }

        private PendingIntent getUpdateIntent(Context context) {
            Intent intent = new Intent(UPDATE_BROADCAST);
            intent.setDataAndType(CalendarContract.CONTENT_URI, UPDATE_DATA_TYPE);
            return PendingIntent.getBroadcast(context, 0 /* no requestCode */, intent,
                    0 /* no flags */);
        }

        private void register() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(UPDATE_BROADCAST);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            mContext.registerReceiver(this, filter);
            mContext.getContentResolver().registerContentObserver(CalendarContract.Events.CONTENT_URI, true, mCalendarObserver);
        }

        private void unregister() {
            try {
                mContext.unregisterReceiver(this);
                mContext.getContentResolver().unregisterContentObserver(mCalendarObserver);
            } catch (Exception e){
            }
        }

        private MatrixCursor matrixCursorFromCursor(Cursor cursor) {
            if (cursor == null) {
                return null;
            }

            String[] columnNames = cursor.getColumnNames();
            if (columnNames == null) {
                columnNames = new String[] {};
            }
            MatrixCursor newCursor = new MatrixCursor(columnNames);
            int numColumns = cursor.getColumnCount();
            String data[] = new String[numColumns];
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                for (int i = 0; i < numColumns; i++) {
                    data[i] = cursor.getString(i);
                }
                newCursor.addRow(data);
            }
            return newCursor;
        }
    }

    public CalendarClient(Context context, CalendarEventObserver observer) {
        mFactory = new CalendarFactory(context, observer);
    }

    public void register() {
        mFactory.register();
    }

    public void load() {
        mFactory.load();
    }

    public void unregister() {
        mFactory.unregister();
    }
}
