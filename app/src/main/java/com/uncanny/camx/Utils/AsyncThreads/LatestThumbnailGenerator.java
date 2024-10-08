package com.uncanny.camx.Utils.AsyncThreads;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;

import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@WorkerThread
public class LatestThumbnailGenerator implements Runnable{
    private static final String TAG = "LatestThumbnailGenerator";
    private static final List<String> IMAGE_FILES_EXTENSIONS = Arrays.asList("JPG", "JPEG", "DNG");
    private final Activity activity;
    private Bitmap bitmap;
    private ShapeableImageView thumbPreview;

    private Bitmap getBitmap(){
        if(bitmap==null)
            return Bitmap.createBitmap(96,96, Bitmap.Config.ARGB_8888);
        return bitmap;
    }

    public LatestThumbnailGenerator(Activity activity, ShapeableImageView thumbPreview){
        this.activity = activity;
        this.thumbPreview = thumbPreview;
    }

    @Override
    public void run() {
        String[] projection = new String[] {MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.MEDIA_TYPE};
        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                + " OR "
                + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

        final Cursor cursor = activity.getContentResolver().query(MediaStore.Files.getContentUri("external")
                , projection
                , selection, null
                , MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC");

        if (cursor.moveToFirst()) {
            do{
                if(cursor.getString(0).contains("DCIM/Camera")){
                    String imageLocation = cursor.getString(0);
                    File latestMedia = new File(imageLocation);

                    if (latestMedia.exists()) {
                        try {
                            if(fileIsImage(String.valueOf(latestMedia)))
                                bitmap = applyExifRotation(latestMedia.getAbsolutePath());
                            else
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                                    bitmap = ThumbnailUtils.createVideoThumbnail(latestMedia, new Size(96, 96), new CancellationSignal());
                                else
                                    bitmap = ThumbnailUtils.createVideoThumbnail(latestMedia.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
                        } catch (IOException e) {
                            Log.e(TAG, "mediaScan: "+e.getMessage());
                        }
                        finally {
                            activity.runOnUiThread(() -> thumbPreview.setImageBitmap(getBitmap()));
                        }
                        Log.e(TAG, "Latest media: "+latestMedia);
                    }
                    break;
                }
            }while (cursor.moveToNext());
        }
        cursor.close();
    }

    public static Bitmap applyExifRotation(String filePath) {
        ExifInterface exif;
//        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        Bitmap bitmap = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(filePath), 96, 96);
        try {
            exif = new ExifInterface(filePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotate = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
            if (rotate != 0) {
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private boolean fileIsImage(String file) {
        int index = file.lastIndexOf(46);
        return IMAGE_FILES_EXTENSIONS.contains(-1 == index ? "" : file.substring(index + 1).toUpperCase());
    }

}