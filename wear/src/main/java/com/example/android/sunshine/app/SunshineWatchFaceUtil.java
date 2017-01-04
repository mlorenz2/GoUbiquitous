package com.example.android.sunshine.app;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Wearable;

public class SunshineWatchFaceUtil {

   public static final String PATH_WITH_FEATURE = "/watch_face_config/Digital";

   public static void sendWeatherRequestToPhone(final GoogleApiClient client) {
      Wearable.MessageApi.sendMessage(client, "", "/messagePath", null)
            .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
               @Override
               public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                  if (sendMessageResult.getStatus()
                        .isSuccess()) {
                     Log.d("Wear", "Message Sent!");
                  }
               }
            });
   }
}
