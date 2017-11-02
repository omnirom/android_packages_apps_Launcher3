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

public class CurrentWeatherView extends FrameLayout implements OmniJawsClient.OmniJawsObserver {

    static final String TAG = "Launcher3:CurrentWeatherView";
    static final boolean DEBUG = true;

    private ImageView mCurrentImage;
    private OmniJawsClient mWeatherClient;
    private View mCurrentView;
    private View mProgressContainer;
    private View mEmptyView;
    private ImageView mEmptyViewImage;

    public CurrentWeatherView(Context context) {
        this(context, null);
    }

    public CurrentWeatherView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CurrentWeatherView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (DEBUG) Log.d(TAG, "onAttachedToWindow");
        mWeatherClient = new OmniJawsClient(getContext());
        if (mWeatherClient.isOmniJawsServiceInstalled()) {
            setVisibility(View.VISIBLE);
            mWeatherClient.addObserver(this);
            queryAndUpdateWeather();
        } else {
            setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (DEBUG) Log.d(TAG, "onDetachedFromWindow");
        if (mWeatherClient.isOmniJawsServiceInstalled()) {
            mWeatherClient.removeObserver(this);
            mWeatherClient.cleanupObserver();
            mWeatherClient = null;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mProgressContainer = findViewById(R.id.progress_container);
        mCurrentView = findViewById(R.id.current);
        mCurrentImage  = (ImageView) findViewById(R.id.current_image);
        mEmptyView = findViewById(android.R.id.empty);
        mEmptyViewImage = (ImageView) findViewById(R.id.empty_weather_image);

        mEmptyViewImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mWeatherClient.isOmniJawsEnabled()) {
                    Launcher launcher = Launcher.getLauncher(getContext());
                    Intent intent = mWeatherClient.getSettingsIntent();
                    if (intent != null) {
                        launcher.startActivitySafely(null, intent, null);
                    }
                }
            }
        });

        // TODO maxwen - on click show details
        mCurrentView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mWeatherClient.isOmniJawsEnabled()) {
                    startProgress();
                    forceRefreshWeatherSettings();
                }
                return true;
            }
        });
    }

    public void updateWeatherData(OmniJawsClient.WeatherInfo weatherData) {
        if (DEBUG) Log.d(TAG, "updateWeatherData");
        mProgressContainer.setVisibility(View.GONE);

        if (weatherData == null || !mWeatherClient.isOmniJawsEnabled()) {
            setErrorView();
            if (mWeatherClient.isOmniJawsEnabled()) {
                mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_on);
            } else {
                mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_off);
            }
            return;
        }
        mEmptyView.setVisibility(View.GONE);
        mCurrentView.setVisibility(View.VISIBLE);

        Drawable d = mWeatherClient.getWeatherConditionImage(weatherData.conditionCode);
        d = overlay(mContext.getResources(), d, weatherData.temp, null, weatherData.tempUnits);
        mCurrentImage.setImageDrawable(d);
    }

    private Drawable overlay(Resources resources, Drawable image, String min, String max, String tempUnits) {
        if (image instanceof VectorDrawable) {
            image = applyTint(image);
        }
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));
        final float density = resources.getDisplayMetrics().density;
        final int footerHeight = Math.round(18 * density);
        final int imageWidth = image.getIntrinsicWidth();
        final int imageHeight = image.getIntrinsicHeight();
        final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        textPaint.setTypeface(font);
        textPaint.setColor(getTintColor());
        textPaint.setTextAlign(Paint.Align.LEFT);
        final int textSize= (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.getDisplayMetrics());
        textPaint.setTextSize(textSize);
        final int height = imageHeight + footerHeight;
        final int width = imageWidth;

        final Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmp);
        image.setBounds(0, 0, imageWidth, imageHeight);
        image.draw(canvas);

        String str = null;
        if (max != null) {
            str = min +"/"+max + tempUnits;
        } else {
            str = min + tempUnits;
        }
        Rect bounds = new Rect();
        textPaint.getTextBounds(str, 0, str.length(), bounds);
        canvas.drawText(str, width / 2 - bounds.width() / 2, height - textSize / 2, textPaint);

        BitmapDrawable d = shadow(resources, bmp);
        return d;
    }

    private Drawable applyTint(Drawable icon) {
        icon = icon.mutate();
        icon.setTint(getTintColor());
        return icon;
    }

    private int getTintColor() {
        return Color.WHITE;
        /*TypedArray array = mContext.obtainStyledAttributes(new int[]{android.R.attr.colorControlNormal});
        int color = array.getColor(0, 0);
        array.recycle();
        return color;*/
    }

    private void forceRefreshWeatherSettings() {
        mWeatherClient.updateWeather();
    }

    private void setErrorView() {
        mEmptyView.setVisibility(View.VISIBLE);
        mCurrentView.setVisibility(View.GONE);
    }

    @Override
    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
        mProgressContainer.setVisibility(View.GONE);
        setErrorView();

        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_off);
        } else {
            mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_on);
        }
    }

    public void startProgress() {
        mEmptyView.setVisibility(View.GONE);
        mCurrentView.setVisibility(View.GONE);
        mProgressContainer.setVisibility(View.VISIBLE);
    }

    public void stopProgress() {
        mProgressContainer.setVisibility(View.GONE);
    }

    @Override
    public void weatherUpdated() {
        if (DEBUG) Log.d(TAG, "weatherUpdated");
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        mWeatherClient.queryWeather();
        OmniJawsClient.WeatherInfo weatherData = mWeatherClient.getWeatherInfo();
        updateWeatherData(weatherData);
    }

    public static BitmapDrawable shadow(Resources resources, Bitmap b) {
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        BlurMaskFilter blurFilter = new BlurMaskFilter(5, BlurMaskFilter.Blur.OUTER);
        Paint shadowPaint = new Paint();
        shadowPaint.setColor(resources.getColor(R.color.default_shadow_color_no_alpha));
        shadowPaint.setMaskFilter(blurFilter);

        int[] offsetXY = new int[2];
        Bitmap b2 = b.extractAlpha(shadowPaint, offsetXY);

        Bitmap bmResult = Bitmap.createBitmap(b.getWidth(), b.getHeight(),
                Bitmap.Config.ARGB_8888);

        canvas.setBitmap(bmResult);
        canvas.drawBitmap(b2, offsetXY[0], offsetXY[1], null);
        canvas.drawBitmap(b, 0, 0, null);

        return new BitmapDrawable(resources, bmResult);
    }

    public void updateSettings() {
        mWeatherClient.loadCustomIconPackage();
        queryAndUpdateWeather();
    }
}
