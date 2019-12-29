package com.krisstof.imagelinearizer;

import android.graphics.Bitmap;
import android.util.Log;

final class FisheyeParameters {
  private static final String TAG = FisheyeParameters.class.getSimpleName();

  float hfovDeg = 185.f;
  float focalOffset = 2.7f;
  float principalPointXPx;
  float principalPointYPx;

  FisheyeParameters(final Bitmap fisheyeImage) {
    hfovDeg = 185.f;
    focalOffset = 2.7f;
    principalPointXPx = fisheyeImage.getWidth() / 2;
    principalPointYPx = fisheyeImage.getHeight() / 2;
  }

  FisheyeParameters(int imageResourceId) {
    switch (imageResourceId) {
      case R.drawable.srcimage_citycar_185deg_600x400:
        hfovDeg = 185.f;
        focalOffset = 2.7f;
        principalPointXPx = 300.f;
        principalPointYPx = 200.f;
        break;
      case R.drawable.srcimage_citywindow_195deg_1350x900:
        hfovDeg = 230.f;
        focalOffset = 1.5f;
        principalPointXPx = 675.f;
        principalPointYPx = 450.f;
        break;
      case R.drawable.srcimage_factoryhall_170deg_1100x733:
        hfovDeg = 150.f;
        focalOffset = 1.3f;
        principalPointXPx = 1100.f / 2.f;
        principalPointYPx = 733.f / 2.f;
        break;
      default:
        throw new IllegalArgumentException("Unknown image resource ID: " + imageResourceId);
    }
  }
}

