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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DetailedWeatherView extends FrameLayout {

    static final String TAG = "DetailedWeatherView";
    static final boolean DEBUG = false;

    private ImageView mCurrentImage;
    private ImageView mForecastImage0;
    private ImageView mForecastImage1;
    private ImageView mForecastImage2;
    private ImageView mForecastImage3;
    private ImageView mForecastImage4;
    private TextView mForecastText0;
    private TextView mForecastText1;
    private TextView mForecastText2;
    private TextView mForecastText3;
    private TextView mForecastText4;
    private boolean mShowCurrent = true;
    private View mCurrentView;
    private TextView mCurrentText;
    private View mWeatherLine;
    private TopWidgetView mTopWidget;

    public DetailedWeatherView(Context context) {
        this(context, null);
    }

    public DetailedWeatherView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DetailedWeatherView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setTopWidgetView(TopWidgetView topWidget) {
        mTopWidget = topWidget;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mForecastImage0  = (ImageView) findViewById(R.id.forecast_image_0);
        mForecastImage1  = (ImageView) findViewById(R.id.forecast_image_1);
        mForecastImage2  = (ImageView) findViewById(R.id.forecast_image_2);
        mForecastImage3  = (ImageView) findViewById(R.id.forecast_image_3);
        mForecastImage4  = (ImageView) findViewById(R.id.forecast_image_4);
        mForecastText0 = (TextView) findViewById(R.id.forecast_text_0);
        mForecastText1 = (TextView) findViewById(R.id.forecast_text_1);
        mForecastText2 = (TextView) findViewById(R.id.forecast_text_2);
        mForecastText3 = (TextView) findViewById(R.id.forecast_text_3);
        mForecastText4 = (TextView) findViewById(R.id.forecast_text_4);
        mCurrentView = findViewById(R.id.current);
        mCurrentImage  = (ImageView) findViewById(R.id.current_image);
        mCurrentText = (TextView) findViewById(R.id.current_text);
        mWeatherLine = findViewById(R.id.current_weather);

        if (!mShowCurrent) {
            mCurrentView.setVisibility(View.GONE);
        }

        mWeatherLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTopWidget.showDetailedWeather(false);
            }
        });
    }

    public void updateWeatherData(OmniJawsClient weatherClient, OmniJawsClient.WeatherInfo weatherData) {
        if (DEBUG) Log.d(TAG, "updateWeatherData");

        if (weatherData == null) {
            return;
        }
        mWeatherLine.setVisibility(View.VISIBLE);

        SimpleDateFormat sdf = new SimpleDateFormat("EE");
        Calendar cal = Calendar.getInstance();
        String dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        Drawable d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(0).conditionCode);
        d = overlay(mContext.getResources(), d, weatherData.forecasts.get(0).low, weatherData.forecasts.get(0).high,
                weatherData.tempUnits);
        mForecastImage0.setImageDrawable(d);
        mForecastText0.setText(dayShort);

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(1).conditionCode);
        d = overlay(mContext.getResources(), d, weatherData.forecasts.get(1).low, weatherData.forecasts.get(1).high,
                weatherData.tempUnits);
        mForecastImage1.setImageDrawable(d);
        mForecastText1.setText(dayShort);

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(2).conditionCode);
        d = overlay(mContext.getResources(), d, weatherData.forecasts.get(2).low, weatherData.forecasts.get(2).high,
                weatherData.tempUnits);
        mForecastImage2.setImageDrawable(d);
        mForecastText2.setText(dayShort);

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(3).conditionCode);
        d = overlay(mContext.getResources(), d, weatherData.forecasts.get(3).low, weatherData.forecasts.get(3).high,
                weatherData.tempUnits);
        mForecastImage3.setImageDrawable(d);
        mForecastText3.setText(dayShort);

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(4).conditionCode);
        d = overlay(mContext.getResources(), d, weatherData.forecasts.get(4).low, weatherData.forecasts.get(4).high,
                weatherData.tempUnits);
        mForecastImage4.setImageDrawable(d);
        mForecastText4.setText(dayShort);

        if (mShowCurrent) {
            d = weatherClient.getWeatherConditionImage(weatherData.conditionCode);
            d = overlay(mContext.getResources(), d, weatherData.temp, null, weatherData.tempUnits);
            mCurrentImage.setImageDrawable(d);
            mCurrentText.setText(mContext.getResources().getText(R.string.omnijaws_current_text));
        }
    }

    private Drawable overlay(Resources resources, Drawable image, String min, String max, String tempUnits) {
        if (image instanceof VectorDrawable) {
            image = applyTint(image);
        }
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));
        final float density = resources.getDisplayMetrics().density;
        final int footerHeight = Math.round(14 * density);
        final int imageWidth = image.getIntrinsicWidth();
        final int imageHeight = image.getIntrinsicHeight();
        final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        textPaint.setTypeface(font);
        textPaint.setColor(getTintColor());
        textPaint.setTextAlign(Paint.Align.LEFT);
        final int textSize= (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.getDisplayMetrics());
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
}
