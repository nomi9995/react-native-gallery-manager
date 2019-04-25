/**
 * Created by pentarex on 26.01.18.
 */

package org.pentarex.rngallerymanager;

import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import java.util.Map;
import java.io.ByteArrayOutputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.net.Uri;
import java.io.IOException;
import java.io.FileNotFoundException;

public class RNGalleryManagerModule extends ReactContextBaseJavaModule {

    public static final String RNGALLERY_MANAGER = "RNGalleryManager";
    private static ReactApplicationContext reactContext = null;


    public RNGalleryManagerModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;
    }

    @Override
    public String getName() {
        return RNGALLERY_MANAGER;
    }


    @ReactMethod
    public void getAssets(final ReadableMap params, final Promise promise) {
        if (isJellyBeanOrLater()) {
            promise.reject(new Exception("Version of Android must be > JellyBean"));
            return;
        }

        String requestedType = "all";
        if (params.hasKey("type")) {
            requestedType = params.getString("type");
        }

        Integer limit = 10;
        if (params.hasKey("limit")) {
            limit = params.getInt("limit");
        }
        Integer startFrom = 0;
        if (params.hasKey("startFrom")) {
            startFrom = params.getInt("startFrom");
        }
        String albumName = null;
        if (params.hasKey("albumName")) {
            albumName = params.getString("albumName");
        }

        WritableMap response = new WritableNativeMap();

        Cursor gallery = null;
        try {
            gallery = GalleryCursorManager.getAssetCursor(requestedType, albumName, reactContext);
            WritableArray assets = new WritableNativeArray();

            if(gallery.getCount() <= startFrom ) {
                promise.reject("gallery index out of bound", "");
                return;
            } else {
                response.putInt("totalAssets", gallery.getCount());
                boolean hasMore = gallery.getCount() > startFrom + limit;
                response.putBoolean("hasMore", hasMore);
                if(hasMore) {
                    response.putInt("next", startFrom + limit);
                } else {
                    response.putInt("next", gallery.getCount());
                }
                gallery.moveToPosition(startFrom);
            }

            do {
                WritableMap asset = getAsset(gallery);
                assets.pushMap(asset);
                if (gallery.getPosition() == (startFrom + limit) - 1) break;
            } while (gallery.moveToNext());

            response.putArray("assets", assets);

            promise.resolve(response);

        } catch (SecurityException ex) {
            System.err.println(ex);
        } finally {
            if (gallery != null) gallery.close();
        }
    }


    @ReactMethod
    public void getAlbums(final Promise promise) {
        if (isJellyBeanOrLater()) {
            promise.reject(new Exception("Version of Android must be > JellyBean"));
            return;
        }

        WritableMap response = new WritableNativeMap();


        Cursor gallery = null;
        try {
            gallery = GalleryCursorManager.getAlbumCursor(reactContext);
            WritableArray albums = new WritableNativeArray();
            response.putInt("totalAlbums", gallery.getCount());
            gallery.moveToFirst();
            do {
                WritableMap album = getAlbum(gallery);
                albums.pushMap(album);
            } while (gallery.moveToNext());

            response.putArray("albums", albums);

            promise.resolve(response);

        } catch (SecurityException ex) {
            System.err.println(ex);
        } finally {
            if (gallery != null) gallery.close();
        }

    }


    private String makeConversion(String uri, int width, int height)
    {
        Bitmap bitmap=null;
        try
        {
        bitmap= MediaStore.Images.Media.getBitmap(reactContext.getContentResolver(), Uri.parse(uri));
        }
        catch (IOException e)
        {
        }


        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        if (width != 0 && height != 0)
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
        else
            bitmap = Bitmap.createBitmap(bitmap);

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private WritableMap getAsset(Cursor gallery) {
        WritableMap asset = new WritableNativeMap();
        int mediaType = gallery.getInt(gallery.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE));
        String mimeType = gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE));
        String creationDate = gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED));
        String fileName = gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME));
        Double height = gallery.getDouble(gallery.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT));
        Double width = gallery.getDouble(gallery.getColumnIndex(MediaStore.Files.FileColumns.WIDTH));
        String uri = "file://" + gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.DATA));
        String lowQualityUri = "file://" + gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.DATA));
        Double id = gallery.getDouble(gallery.getColumnIndex(MediaStore.Files.FileColumns._ID));


        asset.putString("mimeType", mimeType);
        asset.putString("creationDate", creationDate);
        asset.putDouble("height", height);
        asset.putDouble("width", width);
        asset.putString("filename", fileName);
        asset.putDouble("id", id);
        asset.putString("uri", uri);
        asset.putString("lowQualityUri", "data:image/png;base64,"+makeConversion(lowQualityUri,200,200));

        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
            asset.putDouble("duration", 0);
            asset.putString("type", "image");

        } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            Double duration = gallery.getDouble(gallery.getColumnIndex(MediaStore.Video.VideoColumns.DURATION));
            asset.putDouble("duration", duration / 1000);
            asset.putString("type", "video");
        }
        return asset;
    }

    private WritableMap getAlbum(Cursor gallery) {
        WritableMap album = new WritableNativeMap();
        String albumName = gallery.getString(gallery.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME));
        int assetCount = gallery.getInt(gallery.getColumnIndex("assetCount"));
        album.putString("title", albumName);
        album.putInt("assetCount", assetCount);
        return album;
    }


    private Boolean isJellyBeanOrLater() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN;
    }
}
