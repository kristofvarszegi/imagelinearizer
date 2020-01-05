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
      case R.drawable.citycorner_90deg_2844x4096:
        hfovDeg = 90.f;
        focalOffset = 1.5f;
        principalPointXPx = 2844.f / 2.f;
        principalPointYPx = 4096.f / 2.f;
        break;
      case R.drawable.citytop_90deg_4096x3072:
        hfovDeg = 90.f;
        focalOffset = 1.5f;
        principalPointXPx = 4096.f / 2.f;
        principalPointYPx = 3072.f / 2.f;
        break;
      case R.drawable.cityview_150deg_4096x2726:
        hfovDeg = 150.f;
        focalOffset = 1.15f;
        principalPointXPx = 4096.f / 2.f;
        principalPointYPx = 2726.f / 2.f;
        break;
      case R.drawable.factoryhall_150deg_640x427__bmp:
      case R.drawable.factoryhall_150deg_640x427__jpeg:
      case R.drawable.factoryhall_150deg_640x427__png:
      case R.drawable.factoryhall_150deg_640x427:
        hfovDeg = 150.f;
        focalOffset = 1.4f;
        principalPointXPx = 640.f / 2.f;
        principalPointYPx = 427.f / 2.f;
        break;
      case R.drawable.factoryhall_150deg_4096x2731:
        hfovDeg = 150.f;
        focalOffset = 1.4f;
        principalPointXPx = 4096.f / 2.f;
        principalPointYPx = 2731.f / 2.f;
        break;
      case R.drawable.ladderboy_180_3025x2235:
        hfovDeg = 160.f;
        focalOffset = 1.3f;
        principalPointXPx = 3025.f / 2.f;
        principalPointYPx = 2235.f / 2.f;
        break;
      case R.drawable.libraryhallway_195deg_3910x2607:
        hfovDeg = 185.f;
        focalOffset = 1.5f;
        principalPointXPx = 3910.f / 2.f;
        principalPointYPx = 2607.f / 2.f;
        break;
      case R.drawable.librarytable_195deg_640x427:
        hfovDeg = 189.5f;
        focalOffset = 1.5f;
        principalPointXPx = 640.f / 2.f;
        principalPointYPx = 427.f / 2.f;
        break;
      case R.drawable.librarytable_195deg_1600x1066:
        hfovDeg = 189.5f;
        focalOffset = 1.5f;
        principalPointXPx = 1600.f / 2.f;
        principalPointYPx = 1066.f / 2.f;
        break;
      case R.drawable.librarytable_195deg_2048x1365:
        hfovDeg = 189.5f;
        focalOffset = 1.5f;
        principalPointXPx = 2048.f / 2.f;
        principalPointYPx = 1365.f / 2.f;
        break;
      case R.drawable.librarytable_195deg_3960x2640:
        hfovDeg = 189.5f;
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
        hfovDeg = 150.f;
        focalOffset = 1.3f;
        principalPointXPx = 3000.f / 2.f;
        principalPointYPx = 2000.f / 2.f;
        break;
      case R.drawable.trabi_120deg_4096x2731:
        hfovDeg = 120.f;
        focalOffset = 1.3f;
        principalPointXPx = 4096.f / 2.f;
        principalPointYPx = 2731.f / 2.f;
        break;
      default:
        throw new IllegalArgumentException("Unknown image resource ID: " + imageResourceId);
    }
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("  HFoV [deg]: ").append(hfovDeg);
    buf.append("  Focal offset: ").append(focalOffset);
    buf.append("  Principal point [px]: ").append(principalPointXPx).append(", ")
            .append(principalPointYPx);
    return buf.toString();
  }
}

