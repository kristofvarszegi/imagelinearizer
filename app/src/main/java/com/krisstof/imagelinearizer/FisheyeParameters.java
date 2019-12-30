package com.krisstof.imagelinearizer;

import android.graphics.Bitmap;

final class FisheyeParameters {
  private static final String TAG = FisheyeParameters.class.getSimpleName();

  float hfovDeg;
  float focalOffset;
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
      case R.drawable.citycar_185deg_600x400:
        hfovDeg = 185.f;
        focalOffset = 2.7f;
        principalPointXPx = 300.f;
        principalPointYPx = 200.f;
        break;
      case R.drawable.citycorner_90deg_4000x5761:
        hfovDeg = 90.f;
        focalOffset = 1.5f;
        principalPointXPx = 4000.f / 2.f;
        principalPointYPx = 5761.f / 2.f;
        break;
      case R.drawable.citytop_90deg_4000x3000:
        hfovDeg = 90.f;
        focalOffset = 1.5f;
        principalPointXPx = 4000.f / 2.f;
        principalPointYPx = 3000.f / 2.f;
        break;
      case R.drawable.cityview_150deg_4256x2832:
        hfovDeg = 150.f;
        focalOffset = 1.5f;
        principalPointXPx = 4256.f / 2.f;
        principalPointYPx = 2832.f / 2.f;
        break;
      case R.drawable.factoryhall_170deg_5184x3456:
        hfovDeg = 170.f;
        focalOffset = 1.3f;
        principalPointXPx = 5184.f / 2.f;
        principalPointYPx = 3456.f / 2.f;
        break;
      case R.drawable.ladderboy_180_3025x2235:
        hfovDeg = 160.f;
        focalOffset = 1.3f;
        principalPointXPx = 3025.f / 2.f;
        principalPointYPx = 2235.f / 2.f;
        break;
      case R.drawable.libraryhallway_195deg_3910x2607:
        hfovDeg = 230.f;
        focalOffset = 1.5f;
        principalPointXPx = 3910.f / 2.f;
        principalPointYPx = 2607.f / 2.f;
        break;
      case R.drawable.librarytable_195deg_3960x2640:
        hfovDeg = 230.f;
        focalOffset = 1.5f;
        principalPointXPx = 3960.f / 2.f;
        principalPointYPx = 2640.f / 2.f;
        break;
      case R.drawable.librarywindow_195deg_3960x2640:
        hfovDeg = 230.f;
        focalOffset = 1.5f;
        principalPointXPx = 3960.f / 2.f;
        principalPointYPx = 2640.f / 2.f;
        break;
      case R.drawable.redcityroad_120deg_3000x2000:
        hfovDeg = 120.f;
        focalOffset = 1.3f;
        principalPointXPx = 3000.f / 2.f;
        principalPointYPx = 2000.f / 2.f;
        break;
      case R.drawable.trabi_120deg_5184x3456:
        hfovDeg = 120.f;
        focalOffset = 1.3f;
        principalPointXPx = 5184.f / 2.f;
        principalPointYPx = 3456.f / 2.f;
        break;
      default:
        throw new IllegalArgumentException("Unknown image resource ID: " + imageResourceId);
    }
  }
}

