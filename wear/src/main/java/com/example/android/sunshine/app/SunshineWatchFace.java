/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.example.android.sunshine.app.SunshineWatchFaceUtil.PATH_WITH_FEATURE;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
   private static class EngineHandler extends Handler {
      private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

      public EngineHandler(SunshineWatchFace.Engine reference) {
         mWeakReference = new WeakReference<>(reference);
      }

      @Override
      public void handleMessage(Message msg) {
         SunshineWatchFace.Engine engine = mWeakReference.get();
         if (engine != null) {
            switch (msg.what) {
               case MSG_UPDATE_TIME:
                  engine.handleUpdateTimeMessage();
                  break;
            }
         }
      }
   }

   private class Engine extends CanvasWatchFaceService.Engine
         implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks,
         GoogleApiClient.OnConnectionFailedListener {
      final Handler mUpdateTimeHandler = new EngineHandler(this);
      private final String TAG = SunshineWatchFace.class.getSimpleName();
      int centerX;
      int centerY;
      SimpleDateFormat dateFormat;
      String dateText;
      Paint dateTextPaint;
      String highTemp = "";
      Paint highTempPaint;
      Paint iconPaint;
      String lowTemp = "Test";
      Paint lowTempPaint;
      boolean mAmbient;
      Paint mBackgroundPaint;
      Calendar mCalendar;
      final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
            mCalendar.setTimeZone(TimeZone.getDefault());
            invalidate();
         }
      };
      GoogleApiClient mGoogleApiClient =
            new GoogleApiClient.Builder(SunshineWatchFace.this).addConnectionCallbacks(this)
                  .addOnConnectionFailedListener(this)
                  .addApi(Wearable.API)
                  .build();
      /**
       * Whether the display supports fewer bits for each color in ambient mode. When true, we
       * disable anti-aliasing in ambient mode.
       */
      boolean mLowBitAmbient;
      boolean mRegisteredTimeZoneReceiver = false;
      Paint mTextPaint;
      float mXOffset;
      float mYOffset;
      Bitmap scaledBitmap = null;
      SimpleDateFormat timeFormat;
      String timeText;

      public void loadBitmapFromAsset(Asset asset) {
         if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
         }
         new AsyncTask<Asset, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Asset... assets) {
               ConnectionResult result =
                     mGoogleApiClient.blockingConnect(5000, TimeUnit.MILLISECONDS);
               if (!result.isSuccess()) {
                  return null;
               }
               InputStream assetInputStream =
                     Wearable.DataApi.getFdForAsset(mGoogleApiClient, assets[0])
                           .await()
                           .getInputStream();

               if (assetInputStream == null) {
                  Log.w(TAG, "Requested an unknown Asset.");
                  return null;
               }
               //               return Bitmap.createScaledBitmap(BitmapFactory.decodeStream
               // (assetInputStream), 50,
               //                     50, false);
               return BitmapFactory.decodeStream(assetInputStream);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
               super.onPostExecute(bitmap);
               scaledBitmap = bitmap;
               invalidate();
            }
         }.execute(asset);
      }

      @Override
      public void onAmbientModeChanged(boolean inAmbientMode) {
         super.onAmbientModeChanged(inAmbientMode);
         if (mAmbient != inAmbientMode) {
            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
               mTextPaint.setAntiAlias(!inAmbientMode);
               dateTextPaint.setAntiAlias(!inAmbientMode);
            }
            invalidate();
         }

         // Whether the timer should be running depends on whether we're visible (as well as
         // whether we're in ambient mode), so we may need to start or stop the timer.
         updateTimer();
      }

      @Override
      public void onApplyWindowInsets(WindowInsets insets) {
         super.onApplyWindowInsets(insets);

         // Load resources that have alternate values for round watches.
         Resources resources = SunshineWatchFace.this.getResources();
         boolean isRound = insets.isRound();
         mXOffset = resources.getDimension(
               isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
         float textSize = resources.getDimension(
               isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
         float textSizeDate = resources.getDimension(R.dimen.text_size_date);
         mTextPaint.setTextSize(textSize);
         dateTextPaint.setTextSize(textSizeDate);
         highTempPaint.setTextSize(textSizeDate);
         lowTempPaint.setTextSize(textSizeDate);
      }

      @Override
      public void onConnected(@Nullable Bundle bundle) {
         Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
         updateConfigDataItemAndUiOnStartup();
      }

      @Override
      public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

      }

      @Override
      public void onConnectionSuspended(int i) {

      }

      @Override
      public void onCreate(SurfaceHolder holder) {
         super.onCreate(holder);

         setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this).setCardPeekMode(
               WatchFaceStyle.PEEK_MODE_VARIABLE)
               .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
               .setShowSystemUiTime(false)
               .setAcceptsTapEvents(true)
               .build());
         Resources resources = SunshineWatchFace.this.getResources();

         mGoogleApiClient.connect();
         mYOffset = resources.getDimension(R.dimen.digital_y_offset);

         mBackgroundPaint = new Paint();
         mBackgroundPaint.setColor(resources.getColor(R.color.primary));

         mTextPaint = new Paint();
         mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

         dateTextPaint = new Paint();
         dateTextPaint = createTextPaint(resources.getColor(R.color.sub_text_color));

         highTempPaint = new Paint();
         highTempPaint = createTextPaint(resources.getColor(R.color.digital_text));

         lowTempPaint = new Paint();
         lowTempPaint = createTextPaint(resources.getColor(R.color.sub_text_color));

         mCalendar = Calendar.getInstance();
         dateFormat = new SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault());
         timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
      }

      @Override
      public void onDataChanged(DataEventBuffer dataEventBuffer) {
         for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
               continue;
            }

            DataItem dataItem = dataEvent.getDataItem();
            if (!dataItem.getUri()
                  .getPath()
                  .equals(PATH_WITH_FEATURE)) {
               continue;
            }

            DataMap dataMap = DataMapItem.fromDataItem(dataItem)
                  .getDataMap();

            Asset profileAsset = dataMap.getAsset("weatherIcon");
            loadBitmapFromAsset(profileAsset);

            highTemp = dataMap.getString("highTemp");
            lowTemp = dataMap.getString("lowTemp");

            invalidate();
         }
      }

      @Override
      public void onDestroy() {
         mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
         super.onDestroy();
      }

      @Override
      public void onDraw(Canvas canvas, Rect bounds) {
         // Draw the background.
         if (isInAmbientMode()) {
            canvas.drawColor(Color.BLACK);
         } else {
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
         }

         // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
         long now = System.currentTimeMillis();
         mCalendar.setTimeInMillis(now);

         timeText = timeFormat.format(now);
         centerX = bounds.centerX();
         centerY = bounds.centerY() - 20;
         dateText = dateFormat.format(now);
         canvas.drawText(timeText, centerX - (mTextPaint.measureText(timeText)) / 2, centerY,
               mTextPaint);
         canvas.drawText(dateText, centerX - (dateTextPaint.measureText(dateText)) / 2,
               centerY + 30, dateTextPaint);
         canvas.drawLine(centerX - 30, centerY + 50, centerX + 30, centerY + 50, dateTextPaint);

         canvas.drawText(highTemp, centerX+20, centerY + 80, highTempPaint);
         canvas.drawText(lowTemp, centerX+20, centerY + 110, lowTempPaint);

         if (scaledBitmap != null) {
            canvas.drawBitmap(scaledBitmap, null,
                  new Rect(centerX - 60, centerY + 60, centerX, centerY + 120), null);
         }
      }

      @Override
      public void onPropertiesChanged(Bundle properties) {
         super.onPropertiesChanged(properties);
         mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

         boolean burinInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
         dateTextPaint.setTypeface(burinInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
         highTempPaint.setTypeface(burinInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
      }

      /**
       * Captures tap event (and tap type) and toggles the background color if the user finishes a
       * tap.
       */
      @Override
      public void onTapCommand(int tapType, int x, int y, long eventTime) {
         switch (tapType) {
            case TAP_TYPE_TOUCH:
               // The user has started touching the screen.
               break;
            case TAP_TYPE_TOUCH_CANCEL:
               // The user has started a different gesture or otherwise cancelled the tap.
               break;
            case TAP_TYPE_TAP:
               // The user has completed the tap gesture.
               // TODO: Add code to handle the tap gesture.
               Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                     .show();
               break;
         }
         invalidate();
      }

      @Override
      public void onTimeTick() {
         super.onTimeTick();
         invalidate();
      }

      @Override
      public void onVisibilityChanged(boolean visible) {
         super.onVisibilityChanged(visible);

         if (visible) {
            registerReceiver();

            // Update time zone in case it changed while we weren't visible.
            mCalendar.setTimeZone(TimeZone.getDefault());
            invalidate();
         } else {
            unregisterReceiver();
         }

         // Whether the timer should be running depends on whether we're visible (as well as
         // whether we're in ambient mode), so we may need to start or stop the timer.
         updateTimer();
      }

      private Paint createTextPaint(int textColor) {
         Paint paint = new Paint();
         paint.setColor(textColor);
         paint.setTypeface(NORMAL_TYPEFACE);
         paint.setAntiAlias(true);
         return paint;
      }

      /**
       * Handle updating the time periodically in interactive mode.
       */
      private void handleUpdateTimeMessage() {
         invalidate();
         if (shouldTimerBeRunning()) {
            long timeMs = System.currentTimeMillis();
            long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
         }
      }

      private void registerReceiver() {
         if (mRegisteredTimeZoneReceiver) {
            return;
         }
         mRegisteredTimeZoneReceiver = true;
         IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
         SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
      }

      /**
       * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
       * only run when we're visible and in interactive mode.
       */
      private boolean shouldTimerBeRunning() {
         return isVisible() && !isInAmbientMode();
      }

      private void unregisterReceiver() {
         if (!mRegisteredTimeZoneReceiver) {
            return;
         }
         mRegisteredTimeZoneReceiver = false;
         SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
      }

      private void updateConfigDataItemAndUiOnStartup() {
         SunshineWatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
               new SunshineWatchFaceUtil.FetchConfigDataMapCallback() {
                  @Override
                  public void onConfigDataMapFetched(DataMap startupConfig) {
                     // If the DataItem hasn't been created yet or some keys are missing,
                     // use the default values.
                     SunshineWatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                     //                     updateUiForConfigDataMap(startupConfig);
                  }
               });
      }

      /**
       * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently or
       * stops it if it shouldn't be running but currently is.
       */
      private void updateTimer() {
         mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
         if (shouldTimerBeRunning()) {
            mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
         }
      }
   }

   private static final Typeface BOLD_TYPEFACE =
         Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
   /**
    * Update rate in milliseconds for interactive mode. We update once a second since seconds are
    * displayed in interactive mode.
    */
   private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
   /**
    * Handler message id for updating the time periodically in interactive mode.
    */
   private static final int MSG_UPDATE_TIME = 0;
   private static final Typeface NORMAL_TYPEFACE =
         Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

   @Override
   public Engine onCreateEngine() {
      return new Engine();
   }
}
