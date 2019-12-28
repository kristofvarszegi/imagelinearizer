package com.krisstof.imagelinearizer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.util.Log;

abstract class Utils {
  private static final String TAG = Utils.class.getSimpleName();

  static boolean isDeviceSupported(final Activity activity) {
    ConfigurationInfo configInfo = ((ActivityManager) activity.getSystemService(
        Context.ACTIVITY_SERVICE)).getDeviceConfigurationInfo();
    if (configInfo != null) {
      String openGlVersionString = configInfo.getGlEsVersion();
      return Double.parseDouble(openGlVersionString) >= MainActivity.MIN_OPENGLES_VERSION;
    } else {
      Log.w(TAG, "Activity's configuration info is null");
      return false;
    }
  }
}
