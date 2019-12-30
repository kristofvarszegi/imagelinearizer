package com.krisstof.imagelinearizer;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

abstract class Utils {
  private static final String TAG = Utils.class.getSimpleName();
  private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  static int getDeviceOrientation(DisplayMetrics displayMetrics) {
    if (displayMetrics.widthPixels <= displayMetrics.heightPixels) {
      return Configuration.ORIENTATION_PORTRAIT;
    } else {
      return Configuration.ORIENTATION_LANDSCAPE;
    }
  }

  static Bitmap reduceImageToFit(final Bitmap image, int maxImageWidthPx,
                                 int maxImageHeightPx) {
    int reducedImageWidthPx = image.getWidth();
    int reducedImageHeightPx = image.getHeight();
    if (reducedImageWidthPx > maxImageWidthPx) {
      reducedImageWidthPx = maxImageWidthPx;
      reducedImageHeightPx = (int) ((float) reducedImageWidthPx * (float) image.getHeight()
          / (float) image.getWidth());
    }
    if (reducedImageHeightPx > maxImageHeightPx) {
      reducedImageHeightPx = maxImageHeightPx;
      reducedImageWidthPx = (int) ((float) reducedImageHeightPx * (float) image.getWidth()
          / (float) image.getHeight());
    }
    return Bitmap.createScaledBitmap(image, reducedImageWidthPx, reducedImageHeightPx, true);
  }

  static void saveImageToExternalStorage(Context context, final Bitmap image,
                                         final String imageFilename) {
    String picturesPath = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES).toString();
    File imageFile = new File(picturesPath, imageFilename);
    if (imageFile.exists()) {
      Toast.makeText(context, "Image file \'" + imageFilename + "\' already exists",
          Toast.LENGTH_LONG).show();
    } else {
      try {
        FileOutputStream outFStream = new FileOutputStream(imageFile);
        image.compress(Bitmap.CompressFormat.JPEG, 90, outFStream);
        outFStream.flush();
        outFStream.close();
      } catch (Exception e) {
        Log.e(TAG, Utils.stackTraceToString(e));
      }
      MediaScannerConnection.scanFile(context, new String[]{imageFile.toString()}, null,
          new MediaScannerConnection.OnScanCompletedListener() {
            public void onScanCompleted(String path, Uri uri) {
              if (uri == null) {
                Log.e(TAG, "Failed to scan media");
              } else {
                Log.d(TAG, "Scanned \'" + path + "\' -> " + uri);
              }
            }
          });
    }
  }

  static <T> boolean arrayContains(T[] elements, T elementOfInterest) {
    if (elements == null) {
      return false;
    }
    for (T element : elements) {
      if (element.equals(elementOfInterest)) {
        return true;
      }
    }
    return false;
  }

  static String getDateTimeStr() {
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATETIME_FORMAT, Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    Date now = Calendar.getInstance().getTime();
    return dateFormat.format(now);
  }

  static String stackTraceToString(Exception exception) {
    StringWriter stringWriter = new StringWriter();
    exception.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString();
  }

  static String getFilepathAtUri(Context context, final Uri uri) {
    String[] projection = {MediaStore.MediaColumns.DATA};
    Cursor metaCursor = context.getContentResolver().query(uri, projection, null, null, null);
    String filepath = null;
    if (metaCursor != null) {
      try {
        if (metaCursor.moveToFirst()) {
          filepath = metaCursor.getString(0);
        }
      } finally {
        metaCursor.close();
      }
    }
    return filepath;
  }

  static Bitmap createGridOverlayImage(int imageWidthPx, int imageHeightPx, int cellSizePx,
                                       float lineWidthPx, int centerColor, int nonCenterColor) {
    Bitmap gridOverlayImage = Bitmap.createBitmap(imageWidthPx, imageHeightPx,
        Bitmap.Config.ARGB_8888);
    gridOverlayImage.eraseColor(Color.argb(0, 0, 0, 0));
    Canvas canvas = new Canvas(gridOverlayImage);
    Paint gridLinesPaint = new Paint();
    gridLinesPaint.setColor(centerColor);
    gridLinesPaint.setStyle(Paint.Style.STROKE);
    gridLinesPaint.setStrokeWidth(lineWidthPx);
    gridLinesPaint.setAntiAlias(true);
    canvas.drawLine(gridOverlayImage.getWidth() / 2, 0, gridOverlayImage.getWidth() / 2,
        gridOverlayImage.getHeight(), gridLinesPaint);
    canvas.drawLine(0, gridOverlayImage.getHeight() / 2, gridOverlayImage.getWidth(),
        gridOverlayImage.getHeight() / 2, gridLinesPaint);
    gridLinesPaint.setColor(nonCenterColor);
    for (int i = 1; i <= (gridOverlayImage.getWidth() / 2) / cellSizePx; ++i) {
      final float xL1 = gridOverlayImage.getWidth() / 2.f - i * cellSizePx;
      if (xL1 >= 0.f && xL1 < (float) gridOverlayImage.getWidth()) {
        canvas.drawLine(xL1, 0, xL1, gridOverlayImage.getHeight(), gridLinesPaint);
      }
      final float xL2 = gridOverlayImage.getWidth() / 2.f + i * cellSizePx;
      if (xL2 >= 0.f && xL2 < (float) gridOverlayImage.getWidth()) {
        canvas.drawLine(xL2, 0, xL2, gridOverlayImage.getHeight(), gridLinesPaint);
      }
    }
    for (int i = 1; i <= (gridOverlayImage.getHeight() / 2) / cellSizePx; ++i) {
      final float yL1 = gridOverlayImage.getHeight() / 2.f - i * cellSizePx;
      if (yL1 >= 0.f && yL1 < (float) gridOverlayImage.getHeight()) {
        canvas.drawLine(0, yL1, gridOverlayImage.getWidth(), yL1, gridLinesPaint);
      }
      final float yL2 = gridOverlayImage.getHeight() / 2.f + i * cellSizePx;
      if (yL2 >= 0.f && yL2 < (float) gridOverlayImage.getHeight()) {
        canvas.drawLine(0, yL2, gridOverlayImage.getWidth(), yL2, gridLinesPaint);
      }
    }
    return gridOverlayImage;
  }
}
