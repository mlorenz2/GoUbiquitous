package com.example.android.sunshine.app;

import android.net.Uri;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

public class SunshineWatchFaceUtil {
   /**
    * The path for the {@link DataItem} containing {@link SunshineWatchFace} configuration.
    */
   public static final String PATH_WITH_FEATURE = "/watch_face_config/Digital";

   /**
    * Callback interface to perform an action with the current config {@link DataMap} for {@link
    * SunshineWatchFace}.
    */
   public interface FetchConfigDataMapCallback {
      /**
       * Callback invoked with the current config {@link DataMap} for {@link SunshineWatchFace}.
       */
      void onConfigDataMapFetched(DataMap config);
   }

   private static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult> {

      private final FetchConfigDataMapCallback mCallback;

      public DataItemResultCallback(FetchConfigDataMapCallback callback) {
         mCallback = callback;
      }

      @Override
      public void onResult(DataApi.DataItemResult dataItemResult) {
         if (dataItemResult.getStatus()
               .isSuccess()) {
            if (dataItemResult.getDataItem() != null) {
               DataItem configDataItem = dataItemResult.getDataItem();
               DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
               DataMap config = dataMapItem.getDataMap();
               mCallback.onConfigDataMapFetched(config);
            } else {
               mCallback.onConfigDataMapFetched(new DataMap());
            }
         }
      }
   }

   /**
    * Asynchronously fetches the current config {@link DataMap} for {@link SunshineWatchFace} and
    * passes it to the given callback.
    * If the current config {@link DataItem} doesn't exist, it isn't created and the callback
    * receives an empty DataMap.
    */
   public static void fetchConfigDataMap(final GoogleApiClient client,
         final FetchConfigDataMapCallback callback) {
      Wearable.NodeApi.getLocalNode(client)
            .setResultCallback(new ResultCallback<NodeApi.GetLocalNodeResult>() {
               @Override
               public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                  String localNode = getLocalNodeResult.getNode()
                        .getId();
                  Uri uri = new Uri.Builder().scheme("wear")
                        .path(SunshineWatchFaceUtil.PATH_WITH_FEATURE)
                        .authority(localNode)
                        .build();
                  Wearable.DataApi.getDataItem(client, uri)
                        .setResultCallback(new DataItemResultCallback(callback));
               }
            });
   }

   /**
    * Overwrites the current config {@link DataItem}'s {@link DataMap} with {@code newConfig}. If
    * the config DataItem doesn't exist, it's created.
    */
   public static void putConfigDataItem(GoogleApiClient googleApiClient, DataMap newConfig) {
      PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WITH_FEATURE);
      putDataMapRequest.setUrgent();
      DataMap configToPut = putDataMapRequest.getDataMap();
      configToPut.putAll(newConfig);
      Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
            .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
               @Override
               public void onResult(DataApi.DataItemResult dataItemResult) {
               }
            });
   }
}
