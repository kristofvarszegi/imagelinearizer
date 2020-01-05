/*
 * TODO
 * - Measure hfovs
 * - Solve OOM in ITs
 * - Implement ITs
 * - Free/Pro with productFlavors {...
 * - Screenshots for Play Store
 * - RELEASE
 *
 * Next release:
 * - As option for opening images
 * - Share linearized image
 *
 * Known issues
 * - Downsizing artifacts in output image for small output image sizes
 * - Edittext texts at same height as textviews
 */

package com.krisstof.imagelinearizer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallState;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = MainActivity.class.getSimpleName();

  private static final int UPDATEAPP_REQUESTCODE = 100;
  private static final int PICKIMAGEFROMGALLERY_REQUESTCODE = 200;
  private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {Utils.BMP_STR, Utils.JPEG_STR,
      Utils.JPG_STR, Utils.PNG_STR};
  private static final int WRITEEXTERNALSTORAGE_REQUESTCODE = 300;
  private static final float SMALLIMAGE_SIZERATIO_LANDSCAPE = 0.3f;
  private static final float SMALLIMAGE_SIZERATIO_PORTRAIT = 0.2f;
  private static final int[] MAX_IMAGE_SIZES_PX = new int[] {4092, 3600, 3072, 2560, 2048, 1600,
          1280, 1024, 640};
  //private static final float CAMERAPARAMETERSPANEL_WIDTH_RATIO_LANDSCAPE = 0.3f;
  //private static final float CAMERAPARAMETERSPANEL_HEIGHT_RATIO_LANDSCAPE = 0.8f;
  private static final float CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT = 1.f;
  private static final float CAMERAPARAMETERSPANEL_HEIGHT_RATIO_PORTRAIT = 0.3f;
  private static final int MAX_SRCIMAGETITLE_LENGTH = 32;

  private static final float SRCCAM_HFOVDEG_MAX = 235.0f;
  private static final float SRCCAM_FOCALLENGTH_MAX = 4.0f;
  private static final float DSTCAM_HFOVDEG_MAX = 170.0f;
  private static final float DSTCAM_YAW_SPAN_DEG = 180.f;
  private static final float DSTCAM_PITCH_SPAN_DEG = 180.f;
  private static final float DSTCAM_ROLL_SPAN_DEG = 180.f;
  private static final int PRINCIPALPOINT_SEEKBAR_DECIMALS = 2;
  private static final float GRIDOVERLAY_LINEWIDTH_PX = 2.f;
  private static final int GRIDOVERLAY_CELLCOUNT = 20;

  private static final int BASELINE_DISPLAYDENSITY_DP = 160;
  private static final String FLOAT_FORMAT_STR = "%.2f";

  enum RELEASE_TYPE {FREE, PRO}
  private static final class AppConfig {
    RELEASE_TYPE releaseType;
    int maxImageSizePx;

    AppConfig(RELEASE_TYPE releaseType, int maxImageSizePx) {
      this.releaseType = releaseType;
      this.maxImageSizePx = maxImageSizePx;
    }
  }

  private AppConfig mAppConfig;
  private Context mContext;
  private Thread mImageUpdaterThread;
  private Handler mImageUpdaterHandler;
  private Thread mImageSaverThread;
  private Handler mImageSaverHandler;
  private Semaphore mSemaphore;
  private String mDstImageFilename;
  private boolean mIsHelpVisible;

  private AppUpdateManager mAppUpdateManager;
  private InstallStateUpdatedListener mInstallStateUpdatedListener;

  private InterstitialAd mInterstitialAd;
  private boolean mIsInterstitialAdOpen;
  private String mImageSavedNotificationText;

  private Bitmap mSrcImage;
  private Bitmap mSmallSrcImage;
  private FisheyeParameters mSrcCamParameters;
  private Bitmap mDstImage;
  private Bitmap mDstOverlayImage;

  private DstCamParameters mDstCamParameters;

  private ImageTransformer mImageTransformer;
  private ImageView mSrcCamView;
  private ImageView mDstCamView;
  private ImageView mDstCamOverlayView;

  DstCamParameters getDstCamParameters() {
    return mDstCamParameters;
  }

  Bitmap getDstImage() { return mDstImage; }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    updateApp();

    final RELEASE_TYPE releaseType = RELEASE_TYPE.FREE;
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    final int maxImageSizePx = determineMaxImageSizePxBasedOnAvailableMemory(displayMetrics);

    if (maxImageSizePx > 0) {
      if (maxImageSizePx < MAX_IMAGE_SIZES_PX[0]) {
        final String maxImageSizeNotificationText = "Max. image size limited to " + maxImageSizePx
                + " x " + maxImageSizePx + " pixels to fit into available memory";
        Log.i(TAG, maxImageSizeNotificationText);
        Toast.makeText(this, maxImageSizeNotificationText, Toast.LENGTH_LONG).show();
      }
      mAppConfig = new AppConfig(releaseType, maxImageSizePx);
      mContext = getApplicationContext();
      initAds();
      mImageTransformer = new ImageTransformer(this);
      mIsHelpVisible = false;
      initUi();
      mSemaphore = new Semaphore(1, true);
      initThreads();
    } else {
      handleOutOfMemory("at init");
      finish();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    Log.d(TAG, "onConfigurationChanged(.)...");
    super.onConfigurationChanged(newConfig);
    mImageUpdaterHandler = null;
    mImageUpdaterThread.interrupt();
    initUi();
    initThreads();
    postRecalculateImagesIfHandlerIsCreated();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    if (requestCode == WRITEEXTERNALSTORAGE_REQUESTCODE
        && grantResults.length > 0
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "External storage write permission granted");
    } else {
      Toast.makeText(this, "Storage write permission needed for saving images",
          Toast.LENGTH_LONG).show();
    }
  }

  private void initUi() {
    initContentView();
    initMenuBar();

    initSrcImage();
    mSrcCamView = findViewById(R.id.imageViewSrcCam);

    if (mDstCamParameters == null) {
      mDstCamParameters = new DstCamParameters();
      mDstCamParameters.imageWidthPx = (int) ((float) mSrcImage.getWidth());
      mDstCamParameters.imageHeightPx = (int) ((float) mSrcImage.getHeight());
      //mDstCamParameters.geometry = DstCamParameters.CAMERA_GEOMETRY.EQUIRECTANGULAR;
      Log.d(TAG, "mDstImageSizePx in onCreate(.): " + mDstCamParameters.imageWidthPx + "x"
          + mDstCamParameters.imageHeightPx);
    }

    initCameraParametersUi();
    initStatusPanel();
    updateCameraParametersPanelVisibilities();
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

    initDstCamUi();
    initHelpPanel();
  }

  private void initThreads() {
    mDstCamOverlayView.post(new Runnable() {
      @Override
      public void run() {
        adjustCreatedUiComponents();
        mImageUpdaterThread = createImageUpdaterThread();
        mImageUpdaterThread.start();
      }
    });

    mDstCamOverlayView.post(new Runnable() {
      @Override
      public void run() {
        mImageSaverThread = createImageSaverThread();
        mImageSaverThread.start();
      }
    });
  }

  private void initContentView() {
    final int deviceOrientation = Utils.getDeviceOrientation(this);
    switch (deviceOrientation) {
      case ORIENTATION_LANDSCAPE:
        Log.d(TAG, "Device orientation is landscape");
        setContentView(R.layout.activity_main_landscapeorientation);
        break;
      case ORIENTATION_PORTRAIT:
        Log.d(TAG, "Device orientation is portrait");
        setContentView(R.layout.activity_main_portraitorientation);
        break;
      default:
        throw new RuntimeException("Invalid device orientation: " + deviceOrientation);
    }
  }

  private void initMenuBar() {
    findViewById(R.id.buttonLoadImage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requestToLoadImageFromGallery();
      }
    });
    findViewById(R.id.buttonSaveImage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (mInterstitialAd.isLoaded()) {
          mInterstitialAd.show();
        } else {
          Log.d(TAG, "Interstitial ad is not loaded yet");
        }
        mIsInterstitialAdOpen = true;
        initiateSavingImage();
      }
    });
    findViewById(R.id.buttonHelp).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showHelp();
      }
    });
  }

  private void initSrcImage() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    BitmapFactory.Options bitmapFactoryOptions = new BitmapFactory.Options();
    final float resourceDisplayDensity = getResources().getDisplayMetrics().density;
    Log.i(TAG, "Screen w, h, display density: " + displayMetrics.widthPixels + ", "
        + displayMetrics.heightPixels + ", " + resourceDisplayDensity);
    bitmapFactoryOptions.inDensity = (int) ((float) BASELINE_DISPLAYDENSITY_DP
        * resourceDisplayDensity);
    //final int imageResourceId = R.drawable.citycar_185deg_600x400;  // Pp = img center
    //final int imageResourceId = R.drawable.cityview_150deg_4256x2832;
    //final int imageResourceId = R.drawable.factoryhall_150deg_640x427;
    //final int imageResourceId = R.drawable.factoryhall_150deg_640x427__bmp;
    //final int imageResourceId = R.drawable.factoryhall_150deg_640x427__jpeg;
    //final int imageResourceId = R.drawable.factoryhall_150deg_640x427__png;
    //final int imageResourceId = R.drawable.factoryhall_150deg_5184x3456;
    //final int imageResourceId = R.drawable.ladderboy_180_3025x2235;
    //final int imageResourceId = R.drawable.libraryhallway_195deg_3910x2607;
    //final int imageResourceId = R.drawable.librarytable_195deg_640x427;  // Pp = img center
    final int imageResourceId = R.drawable.librarytable_195deg_1600x1066;  // Pp = img center
    //final int imageResourceId = R.drawable.librarytable_195deg_2048x1365;  // Pp = img center
    //final int imageResourceId = R.drawable.librarytable_195deg_3960x2640;  // Pp = img center
    //final int imageResourceId = R.drawable.redcityroad_120deg_3000x2000;  // Pp not img center
    TextView textViewSrcCamTitle = findViewById(R.id.textViewSrcCamTitle);
    textViewSrcCamTitle.setText(getString(R.string.srccam_title));
    if (mSrcImage == null) {
      Log.d(TAG, "Loading sample image...");
      try {
        mSrcImage = BitmapFactory.decodeResource(getResources(),
                imageResourceId, bitmapFactoryOptions)
                .copy(Bitmap.Config.ARGB_8888, false);
      } catch (OutOfMemoryError ome) {
        handleOutOfMemory(getResources().getString(R.string.at_loading_sample_image));
      }
      Log.d(TAG, "Sample image loaded");
      try {
        mSrcCamParameters = new FisheyeParameters(imageResourceId);
        Log.d(TAG, "Src cam pars at init: " + mSrcCamParameters.toString());
      } catch (IllegalArgumentException iae) {
        Toast.makeText(this, iae.getMessage(), Toast.LENGTH_LONG).show();
      }
      Log.d(TAG, "Src image size in onCreate(.)...: " + mSrcImage.getWidth() + " x "
          + mSrcImage.getHeight());
      final String srcImageSizeText = mSrcImage.getWidth()
          + " x " + mSrcImage.getHeight() + " " + getResources().getString(R.string.px);
      ((TextView) findViewById(R.id.textViewSrcCamImageSize)).setText(srcImageSizeText);
    }
  }

  private void initCameraParametersUi() {
    if (Utils.getDeviceOrientation(this) == Configuration.ORIENTATION_LANDSCAPE) {
      ((TextView) findViewById(R.id.textViewSrcCamParsTitle)).setLines(2);
    }
    if (Utils.getDeviceOrientation(this) == Configuration.ORIENTATION_LANDSCAPE) {
      ((TextView) findViewById(R.id.textViewSrcCamHfovDeg)).setLines(2);
    }
    SeekBar seekBarSrcCamHfovDeg = findViewById(R.id.seekBarSrcCamHfovDeg);
    seekBarSrcCamHfovDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSrcCamParameters.hfovDeg = seekBarScaleToValueScale(seekBar, progress, SRCCAM_HFOVDEG_MAX);
        ((TextView) findViewById(R.id.textViewSrcCamHfovDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.srccam_hfovdeg_label) + ": " + FLOAT_FORMAT_STR + "°",
            mSrcCamParameters.hfovDeg));
        postRecalculateImagesIfHandlerIsCreated();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    SeekBar seekBarSrcCamFocalOffset = findViewById(R.id.seekBarSrcCamFocalOffset);
    seekBarSrcCamFocalOffset.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSrcCamParameters.focalOffset = seekBarScaleToValueScale(seekBar, progress, SRCCAM_FOCALLENGTH_MAX);
        ((TextView) findViewById(R.id.textViewSrcCamFocalOffset)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.srccam_focaloffset_label)
                + ": " + FLOAT_FORMAT_STR, mSrcCamParameters.focalOffset));
        postRecalculateImagesIfHandlerIsCreated();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    SeekBar seekBarSrcCamPrincipalPointXPx = findViewById(R.id.seekBarSrcCamPrincipalPointXPx);
    ((SeekBar) findViewById(R.id.seekBarSrcCamPrincipalPointXPx)).setMax(
        (int) ((double) mSrcImage.getWidth() * Math.pow(10., PRINCIPALPOINT_SEEKBAR_DECIMALS)));
    seekBarSrcCamPrincipalPointXPx.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSrcCamParameters.principalPointXPx = seekBarScaleToValueScale(seekBar, progress,
            mSrcImage.getWidth());
        ((TextView) findViewById(R.id.textViewSrcCamPrincipalPointX)).setText(
            String.format(Locale.getDefault(), getResources().getString(
                R.string.srccam_principalpointx_label) + ": " + FLOAT_FORMAT_STR,
                mSrcCamParameters.principalPointXPx));
        postRecalculateImagesIfHandlerIsCreated();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    SeekBar seekBarSrcCamPrincipalPointYPx = findViewById(R.id.seekBarSrcCamPrincipalPointYPx);
    ((SeekBar) findViewById(R.id.seekBarSrcCamPrincipalPointYPx)).setMax(
        (int) ((double) mSrcImage.getHeight() * Math.pow(10., PRINCIPALPOINT_SEEKBAR_DECIMALS)));
    seekBarSrcCamPrincipalPointYPx.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSrcCamParameters.principalPointYPx = seekBarScaleToValueScale(seekBar, progress,
            mSrcImage.getHeight());
        ((TextView) findViewById(R.id.textViewSrcCamPrincipalPointY)).setText(String.format(
            Locale.getDefault(), getResources().getString(
                R.string.srccam_principalpointy_label) + ": " + FLOAT_FORMAT_STR + "",
            mSrcCamParameters.principalPointYPx));
        postRecalculateImagesIfHandlerIsCreated();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });

    if (Utils.getDeviceOrientation(this) == Configuration.ORIENTATION_LANDSCAPE) {
      ((TextView) findViewById(R.id.textViewDstCamParsTitle)).setLines(2);
    }
    Spinner spinnerDstCamGeometry = findViewById(R.id.spinnerDstCamGeometry);
    spinnerDstCamGeometry.setAdapter(new ArrayAdapter<>(this,
        R.layout.spinner_item, getResources().getStringArray(R.array.dst_cam_geometry_choices)));
    spinnerDstCamGeometry.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
        mDstCamParameters.geometry = cameraGeometry((String) parentView.getItemAtPosition(position),
            mContext);
        postRecalculateImagesIfHandlerIsCreated();
        //Toast.makeText(this, getResources().getString(R.string.setting_dstcam_geometry),
        //    Toast.LENGTH_SHORT).show());
      }

      @Override
      public void onNothingSelected(AdapterView<?> parentView) {
      }
    });
    spinnerDstCamGeometry.setSelection(mDstCamParameters.getGeometryId());
    SeekBar seekBarDstCamHfovDeg = findViewById(R.id.seekBarDstCamHfovDeg);
    seekBarDstCamHfovDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mDstCamParameters.hfovDeg = seekBarScaleToValueScale(seekBar, progress, DSTCAM_HFOVDEG_MAX);
        ((TextView) findViewById(R.id.textViewDstCamHfovDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.dstcam_hfovdeg_label) + ": " + FLOAT_FORMAT_STR + "°",
            mDstCamParameters.hfovDeg));
        postRecalculateImagesIfHandlerIsCreated();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    EditText editTextDstImageWidthPx = findViewById(R.id.editTextDstImageWidthPx);
    editTextDstImageWidthPx.addTextChangedListener(new TextWatcher() {
      @Override
      public void afterTextChanged(Editable s) {
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        final Integer readValue = readValueIfTextIsValid(
            (EditText) findViewById(R.id.editTextDstImageWidthPx), mDstCamParameters.imageWidthPx,
            false);
        if (readValue != null) {
          mDstCamParameters.imageWidthPx = readValue;
          postRecalculateImagesIfHandlerIsCreated();
        }
      }
    });
    editTextDstImageWidthPx.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
          final Integer readValue = readValueIfTextIsValid((EditText) v,
              mDstCamParameters.imageWidthPx, true);
          if (readValue != null) {
            mDstCamParameters.imageWidthPx = readValue;
            FrameLayout layoutAll = findViewById(R.id.layoutMainContent);
            if (layoutAll != null) {
              //if (constraintLayoutAll.getWidth() > 0 && constraintLayoutAll.getHeight() > 0) {
              postRecalculateImagesIfHandlerIsCreated();
            }
          }
          Log.d(TAG, "Hiding keyboard...");
          Utils.hideKeyboard(v, mContext);
        }
      }
    });
    editTextDstImageWidthPx.setBackgroundColor(getResources().getColor(
        R.color.halfTransparentBlack));
    EditText editTextDstImageHeightPx = findViewById(R.id.editTextDstImageHeightPx);
    editTextDstImageHeightPx.addTextChangedListener(new TextWatcher() {
      @Override
      public void afterTextChanged(Editable s) {
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        final Integer readValue = readValueIfTextIsValid(
            (EditText) findViewById(R.id.editTextDstImageHeightPx), mDstCamParameters.imageHeightPx,
            false);
        if (readValue != null) {
          mDstCamParameters.imageHeightPx = readValue;
          postRecalculateImagesIfHandlerIsCreated();
        }
      }
    });
    editTextDstImageHeightPx.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
          final Integer readValue = readValueIfTextIsValid((EditText) v,
              mDstCamParameters.imageHeightPx, true);
          if (readValue != null) {
            mDstCamParameters.imageHeightPx = readValue;
            postRecalculateImagesIfHandlerIsCreated();
          }
          Log.d(TAG, "Hiding keyboard...");
          Utils.hideKeyboard(v, mContext);
        }
      }
    });
    editTextDstImageHeightPx.setBackgroundColor(getResources().getColor(
        R.color.halfTransparentBlack));

    if (Utils.getDeviceOrientation(this) == Configuration.ORIENTATION_LANDSCAPE) {
      ((TextView) findViewById(R.id.textViewDstCamRollDeg)).setLines(2);
    }
    SeekBar seekBarDstCamRollDeg = findViewById(R.id.seekBarDstCamRollDeg);
    seekBarDstCamRollDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mDstCamParameters.rollDeg = seekBarScaleToValueScale(seekBar, progress,
            DSTCAM_ROLL_SPAN_DEG) - 0.5f * DSTCAM_ROLL_SPAN_DEG;
        ((TextView) findViewById(R.id.textViewDstCamRollDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.dstcam_rolldeg_label)
                + ": " + FLOAT_FORMAT_STR + "°", mDstCamParameters.rollDeg));
        postRecalculateImagesIfHandlerIsCreated();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    if (Utils.getDeviceOrientation(this) == Configuration.ORIENTATION_LANDSCAPE) {
      ((TextView) findViewById(R.id.textViewDstCamPitchDeg)).setLines(2);
    }
    SeekBar seekBarDstCamPitchDeg = findViewById(R.id.seekBarDstCamPitchDeg);
    seekBarDstCamPitchDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mDstCamParameters.pitchDeg = seekBarScaleToValueScale(seekBar, progress,
            DSTCAM_PITCH_SPAN_DEG) - 0.5f * DSTCAM_PITCH_SPAN_DEG;
        ((TextView) findViewById(R.id.textViewDstCamPitchDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.dstcam_pitchdeg_label)
                + ": " + FLOAT_FORMAT_STR + "°", mDstCamParameters.pitchDeg));
        postRecalculateImagesIfHandlerIsCreated();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    if (Utils.getDeviceOrientation(this) == Configuration.ORIENTATION_LANDSCAPE) {
      ((TextView) findViewById(R.id.textViewDstCamYawDeg)).setLines(2);
    }
    SeekBar seekBarDstCamYawDeg = findViewById(R.id.seekBarDstCamYawDeg);
    seekBarDstCamYawDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mDstCamParameters.yawDeg = seekBarScaleToValueScale(seekBar, progress, DSTCAM_YAW_SPAN_DEG)
            - 0.5f * DSTCAM_YAW_SPAN_DEG;
        ((TextView) findViewById(R.id.textViewDstCamYawDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.dstcam_yawdeg_label)
                + ": " + FLOAT_FORMAT_STR + "°", mDstCamParameters.yawDeg));
        postRecalculateImagesIfHandlerIsCreated();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });

    Switch switchAdvancedParameters = findViewById(R.id.switchAdvancedParameters);
    switchAdvancedParameters.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            updateCameraParametersPanelVisibilities();
          }
        });
    Switch switchDstImageGridOverlay = findViewById(R.id.switchDstImageGridOverlay);
    switchDstImageGridOverlay.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            postRecalculateImagesIfHandlerIsCreated();
          }
        });
  }

  private void initStatusPanel() {
    ((TextView) findViewById(R.id.textViewStatus)).setText(
        getResources().getString(R.string.loading));
    findViewById(R.id.buttonInstallAppUpdate).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        installAppUpdate(v);
      }
    });
    findViewById(R.id.buttonDontInstallAppUpdate).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dontInstallAppUpdate(v);
      }
    });
    findViewById(R.id.layoutStatusPanel).setVisibility(View.INVISIBLE);
  }

  private void initHelpPanel() {
    ((TextView) findViewById(R.id.textViewHelp)).setMovementMethod(new ScrollingMovementMethod());
    findViewById(R.id.buttonCloseHelp).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        findViewById(R.id.layoutHelpPanel).setVisibility(View.GONE);
        mIsHelpVisible = false;
      }
    });
    if (mIsHelpVisible) {
      findViewById(R.id.layoutHelpPanel).setVisibility(View.VISIBLE);
    } else {
      findViewById(R.id.layoutHelpPanel).setVisibility(View.GONE);
    }
  }

  private Thread createImageUpdaterThread() {
    return new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        mImageUpdaterHandler = new Handler() {
        };
        Looper.loop();
      }
    };
  }

  private Thread createImageSaverThread() {
    return new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        mImageSaverHandler = new Handler() {
        };
        Looper.loop();
      }
    };
  }

  void adjustCreatedUiComponents() {
    SeekBar seekBarSrcCamHfovDeg = findViewById(R.id.seekBarSrcCamHfovDeg);
    seekBarSrcCamHfovDeg.setProgress(valueScaleToSeekBarScale(seekBarSrcCamHfovDeg,
        mSrcCamParameters.hfovDeg, SRCCAM_HFOVDEG_MAX));
    SeekBar seekBarSrcCamFocalOffset = findViewById(R.id.seekBarSrcCamFocalOffset);
    seekBarSrcCamFocalOffset.setProgress(valueScaleToSeekBarScale(seekBarSrcCamFocalOffset,
        mSrcCamParameters.focalOffset, SRCCAM_FOCALLENGTH_MAX));
    //Log.d(TAG, "Setting principal point seekbars to " + mSrcCamParameters.principalPointXPx + ", "
    //    + mSrcCamParameters.principalPointYPx + " (max: " + mSrcImage.getWidth() + ", "
    //    + mSrcImage.getHeight() + ")");
    SeekBar seekBarSrcCamPrincipalPointXPx = findViewById(R.id.seekBarSrcCamPrincipalPointXPx);
    seekBarSrcCamPrincipalPointXPx.setProgress(valueScaleToSeekBarScale(
        seekBarSrcCamPrincipalPointXPx, mSrcCamParameters.principalPointXPx, mSrcImage.getWidth()));
    SeekBar seekBarSrcCamPrincipalPointYPx = findViewById(R.id.seekBarSrcCamPrincipalPointYPx);
    seekBarSrcCamPrincipalPointYPx.setProgress(valueScaleToSeekBarScale(
        seekBarSrcCamPrincipalPointYPx, mSrcCamParameters.principalPointYPx, mSrcImage.getHeight()));

    SeekBar seekBarDstCamHfovDeg = findViewById(R.id.seekBarDstCamHfovDeg);
    seekBarDstCamHfovDeg.setProgress(
        valueScaleToSeekBarScale(seekBarDstCamHfovDeg,
            mDstCamParameters.hfovDeg, DSTCAM_HFOVDEG_MAX));
    EditText editTextDstImageWidthPx = findViewById(R.id.editTextDstImageWidthPx);
    editTextDstImageWidthPx.setText(String.format(Locale.US, "%d",
        mDstCamParameters.imageWidthPx));
    EditText editTextDstImageHeightPx = findViewById(R.id.editTextDstImageHeightPx);
    editTextDstImageHeightPx.setText(String.format(Locale.US, "%d",
        mDstCamParameters.imageHeightPx));
    SeekBar seekBarDstCamRollDeg = findViewById(R.id.seekBarDstCamRollDeg);
    seekBarDstCamRollDeg.setProgress(valueScaleToSeekBarScale(seekBarDstCamRollDeg,
        mDstCamParameters.rollDeg + 0.5f * DSTCAM_ROLL_SPAN_DEG, DSTCAM_ROLL_SPAN_DEG));
    SeekBar seekBarDstCamPitchDeg = findViewById(R.id.seekBarDstCamPitchDeg);
    seekBarDstCamPitchDeg.setProgress(valueScaleToSeekBarScale(seekBarDstCamPitchDeg,
        mDstCamParameters.pitchDeg + 0.5f * DSTCAM_PITCH_SPAN_DEG, DSTCAM_PITCH_SPAN_DEG));
    SeekBar seekBarDstCamYawDeg = findViewById(R.id.seekBarDstCamYawDeg);
    seekBarDstCamYawDeg.setProgress(valueScaleToSeekBarScale(seekBarDstCamYawDeg,
        mDstCamParameters.yawDeg + 0.5f * DSTCAM_YAW_SPAN_DEG, DSTCAM_YAW_SPAN_DEG));

    final int deviceOrientation = Utils.getDeviceOrientation(this);
    switch (deviceOrientation) {
      case ORIENTATION_LANDSCAPE:
        adjustViewsToLandscapeOrientation();
        break;
      case ORIENTATION_PORTRAIT:
        adjustViewsToPortraitOrientation();
        break;
      default:
        throw new RuntimeException("Invalid device orientation: " + deviceOrientation);
    }

    ((AdView) findViewById(R.id.adView)).setAdListener(new AdListener() {
      @Override
      public void onAdLoaded() {
        Log.d(TAG, "Banner ad loaded");
      }

      @Override
      public void onAdFailedToLoad(int errorCode) {
        Log.d(TAG, "Failed to load banner ad: " + errorCode);
      }

      @Override
      public void onAdOpened() {
        Log.d(TAG, "Banner ad opened");
      }

      @Override
      public void onAdLeftApplication() {
        Log.d(TAG, "Banner ad left application");
      }

      @Override
      public void onAdClosed() {
        Log.d(TAG, "Banner ad closed");
      }
    });

    TextView textViewStatus = findViewById(R.id.textViewStatus);
    textViewStatus.setText(getResources().getString(R.string.status));
    textViewStatus.setVisibility(View.INVISIBLE);
  }

  Integer readValueIfTextIsValid(EditText editText, int existingValue,
                                 boolean resetTextIfZero) {
    Log.d(TAG, "Edit field text: " + editText);
    Integer retVal = null;
    final String text = editText.getText().toString();
    boolean needToResetText = false;
    if (text.length() > 0) {
      try {
        int newDstImageHeightPx = Integer.parseInt(text);
        if (newDstImageHeightPx > 0 && newDstImageHeightPx <= mAppConfig.maxImageSizePx) {
          retVal = newDstImageHeightPx;
          Log.d(TAG, "Edit text's number value: " + retVal);
          if (text.charAt(0) == '0') {
            editText.setText(String.format(Locale.US, "%d", retVal));
          }
        } else {
          needToResetText = true;
          if (newDstImageHeightPx > mAppConfig.maxImageSizePx) {
            Toast.makeText(this, "Image width and height can be max. "
                    + mAppConfig.maxImageSizePx + " pixels",
                Toast.LENGTH_LONG).show();
          } else if (!resetTextIfZero) {
            needToResetText = false;
          }
        }
      } catch (NumberFormatException nfe) {
        Log.d(TAG, Utils.exceptionToStackTraceString(nfe));
        needToResetText = true;
      }
    } else {
      needToResetText = true;
    }
    if (needToResetText) {
      Log.d(TAG, "Resetting EditText from " + text + " to existing value: " + existingValue);
      editText.setText(String.format(Locale.US, "%d", existingValue));
    }
    return retVal;
  }

  private void adjustViewsToLandscapeOrientation() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

    //LinearLayout layout = findViewById(R.id.linearLayoutCameraParameters);
    //ViewGroup.LayoutParams layoutsLayoutparams = layout.getLayoutParams();
    //layoutsLayoutparams.width = (int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_LANDSCAPE
    //    * (float) displayMetrics.widthPixels);
    //layoutsLayoutparams.height = (int) (CAMERAPARAMETERSPANEL_HEIGHT_RATIO_LANDSCAPE
    //    * (float) displayMetrics.heightPixels);
    //layout.setLayoutParams(layoutsLayoutparams);

    //ScrollView scrollViewCameraParameters = findViewById(R.id.scrollViewCameraParameters);
    /* No
    scrollViewCameraParameters.setMinimumWidth((int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT
        * (float) displayMetrics.widthPixels));
    scrollViewCameraParameters.setMinimumHeight((int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT
        * (float) displayMetrics.widthPixels));*/
    /* No
    ScrollView.LayoutParams scrollViewsLayoutparams
        = scrollViewCameraParameters.getLayoutParams();*/
    //scrollViewCameraParameters.getLayoutParams().width =
    //    (int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_LANDSCAPE * (float) displayMetrics.widthPixels);
    //scrollViewCameraParameters.getLayoutParams().height =
    //    (int) (CAMERAPARAMETERSPANEL_HEIGHT_RATIO_LANDSCAPE * (float) displayMetrics.heightPixels);
  }

  private void adjustViewsToPortraitOrientation() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

    /*ImageView imageViewDstCam = findViewById(R.id.imageViewDstCam);
    Log.d(TAG, "imageViewDstCam layout w, h: " + imageViewDstCam.getLayoutParams().width + ", "
        + imageViewDstCam.getLayoutParams().height);
    int newWidth = displayMetrics.widthPixels;
    int newHeight = (int) ((float) displayMetrics.widthPixels
        * (float) mDstImage.getHeight() / (float) mDstImage.getWidth());
    Log.d(TAG, "Setting imageViewDstCam layout w, h to: " + newWidth + ", " + newHeight);
    imageViewDstCam.getLayoutParams().width = newWidth;
    imageViewDstCam.getLayoutParams().height = newHeight;
    Log.d(TAG, "imageViewDstCam w, h: " + imageViewDstCam.getLayoutParams().width + ", "
        + imageViewDstCam.getLayoutParams().height);*/

    LinearLayout layout = findViewById(R.id.linearLayoutCameraParameters);
    ViewGroup.LayoutParams layoutsLayoutParams = layout.getLayoutParams();
    layoutsLayoutParams.width = (int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT
        * (float) displayMetrics.widthPixels);
    layoutsLayoutParams.height = (int) (CAMERAPARAMETERSPANEL_HEIGHT_RATIO_PORTRAIT
        * (float) displayMetrics.heightPixels);
    layout.setLayoutParams(layoutsLayoutParams);

    ScrollView scrollViewCameraParameters = findViewById(R.id.scrollViewCameraParameters);
    scrollViewCameraParameters.getLayoutParams().width =
        (int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT * (float) displayMetrics.widthPixels);
    scrollViewCameraParameters.getLayoutParams().height =
        (int) (CAMERAPARAMETERSPANEL_HEIGHT_RATIO_PORTRAIT * (float) displayMetrics.heightPixels);
    /* No
    scrollViewCameraParameters.setMinimumWidth((int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT
        * (float) displayMetrics.widthPixels));
    scrollViewCameraParameters.setMinimumHeight((int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT
        * (float) displayMetrics.widthPixels));*/
    /* No
    ScrollView.LayoutParams scrollViewsLayoutparams
        = scrollViewCameraParameters.getLayoutParams();*/
  }

  private void updateCameraParametersPanelVisibilities() {
    final int visibility;
    if (((Switch) findViewById(R.id.switchAdvancedParameters)).isChecked()) {
      visibility = View.VISIBLE;
    } else {
      visibility = View.GONE;
    }
    findViewById(R.id.textViewSrcCamPrincipalPointX).setVisibility(visibility);
    findViewById(R.id.seekBarSrcCamPrincipalPointXPx).setVisibility(visibility);
    findViewById(R.id.textViewSrcCamPrincipalPointY).setVisibility(visibility);
    findViewById(R.id.seekBarSrcCamPrincipalPointYPx).setVisibility(visibility);
    findViewById(R.id.textViewDstCamRollDeg).setVisibility(visibility);
    findViewById(R.id.seekBarDstCamRollDeg).setVisibility(visibility);
    findViewById(R.id.textViewDstCamPitchDeg).setVisibility(visibility);
    findViewById(R.id.seekBarDstCamPitchDeg).setVisibility(visibility);
    findViewById(R.id.textViewDstCamYawDeg).setVisibility(visibility);
    findViewById(R.id.seekBarDstCamYawDeg).setVisibility(visibility);
  }

  private void initDstCamUi() {
    mDstCamView = findViewById(R.id.imageViewDstCam);
    mDstCamOverlayView = findViewById(R.id.imageViewDstCamOverlay);
  }

  private void showHelp() {
    findViewById(R.id.layoutHelpPanel).setVisibility(View.VISIBLE);
    mIsHelpVisible = true;
  }

  private void initAds() {
    if (mAppConfig.releaseType == RELEASE_TYPE.FREE) {
      // TODO If you need to obtain consent from users in the European Economic Area (EEA), set any request-specific flags (such as tagForChildDirectedTreatment or tag_for_under_age_of_consent), or otherwise take action before loading ads, ensure you do so before initializing the Mobile Ads SDK.
      MobileAds.initialize(this, new OnInitializationCompleteListener() {
        @Override
        public void onInitializationComplete(InitializationStatus initializationStatus) {
        }
      });
    }
    mInterstitialAd = new InterstitialAd(this);
    mInterstitialAd.setAdUnitId("ca-app-pub-3940256099942544/1033173712");
    mInterstitialAd.setAdListener(new AdListener() {
      @Override
      public void onAdLoaded() {
      }

      @Override
      public void onAdFailedToLoad(int errorCode) {
      }

      @Override
      public void onAdOpened() {
        mIsInterstitialAdOpen = true;
      }

      @Override
      public void onAdClicked() {
      }

      @Override
      public void onAdLeftApplication() {
      }

      @Override
      public void onAdClosed() {
        if (!mImageSavedNotificationText.isEmpty()) {
          notifyUserThatImageIsSaved();
        }
        mIsInterstitialAdOpen = false;
        mInterstitialAd.loadAd(new AdRequest.Builder().build());
      }
    });
    mInterstitialAd.loadAd(new AdRequest.Builder().build());
    mIsInterstitialAdOpen = false;
    mImageSavedNotificationText = "";
  }

  void notifyUserThatImageIsSaved() {
    Toast.makeText(mContext, mImageSavedNotificationText, Toast.LENGTH_LONG).show();
    mImageSavedNotificationText = "";
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "onDestroy()...");
    if (mImageUpdaterHandler != null) {
      mImageUpdaterHandler.removeCallbacksAndMessages(null);
    }
    if (mSrcImage != null) {
      //mSrcImage.recycle();
      mSrcImage = null;
      System.gc();
    }
    if (mDstImage != null) {
      //mDstImage.recycle();
      mDstImage = null;
      System.gc();
    }
    super.onDestroy();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (requestCode == UPDATEAPP_REQUESTCODE) {
      if (resultCode != RESULT_OK) {
        Log.e(TAG, getResources().getString(R.string.appupdate_download_failed));
        Toast.makeText(this, getResources().getString(R.string.appupdate_download_failed),
            Toast.LENGTH_SHORT).show();
      }
    }
    if (requestCode == PICKIMAGEFROMGALLERY_REQUESTCODE && resultCode == Activity.RESULT_OK) {
      try {
        final Uri imageUri = intent.getData();
        loadImageWhichTheUserPickedFromGallery(imageUri);
      } catch (Exception e) {
        Log.e(TAG, Utils.exceptionToStackTraceString(e));
      }
    }
  }

  private void loadImageWhichTheUserPickedFromGallery(final Uri imageUri) {
    //String srcImageTitle = imageUri.toString();
    String srcImageTitle = Utils.getFilepathAtUri(this, imageUri);
    boolean imageFileSupported;
    if (srcImageTitle == null) {
      Log.d(TAG, "srcImageTitle is null, setting Uri as string instead");
      srcImageTitle = imageUri.toString();
      imageFileSupported = true;
    } else {
      String srcImageExtension = "";
      final int lastDotIndex = srcImageTitle.lastIndexOf('.');
      if (lastDotIndex > 0) {
        srcImageExtension = srcImageTitle.substring(lastDotIndex + 1);
      }
      Log.d(TAG, "SUPPORTED_IMAGE_EXTENSIONS: "
          + Arrays.toString(SUPPORTED_IMAGE_EXTENSIONS) + "; ext: \'" + srcImageExtension
          + "\'");
      if (Utils.arrayContains(SUPPORTED_IMAGE_EXTENSIONS, srcImageExtension)) {
        imageFileSupported = true;
      } else {
        imageFileSupported = false;
        Toast.makeText(this, "Image file extension \'" + srcImageExtension
            + "\" is not supported", Toast.LENGTH_LONG).show();
      }
    }
    if (imageFileSupported) {
      Log.d(TAG, "srcImageTitle: " + srcImageTitle);
      if (srcImageTitle.length() > MAX_SRCIMAGETITLE_LENGTH) {
        srcImageTitle = "\u2026" + srcImageTitle.substring(
            srcImageTitle.length() - MAX_SRCIMAGETITLE_LENGTH - 1);
        Log.d(TAG, "Shortened srcImageTitle: " + srcImageTitle);
      }

      TextView textViewStatus = findViewById(R.id.textViewStatus);
      textViewStatus.setText(getResources().getString(R.string.loading_image));
      textViewStatus.setVisibility(View.VISIBLE);

      final Bitmap loadedImage;
      try {
        loadedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(),
                imageUri).copy(Bitmap.Config.ARGB_8888, false);
        Log.d(TAG, "loadedImage size in onActivityResult(.): " + loadedImage.getWidth()
                + "x" + loadedImage.getHeight());
        if (mSrcImage.getWidth() > 0 && mSrcImage.getHeight() > 0
                && mSrcImage.getWidth() <= mAppConfig.maxImageSizePx
                && mSrcImage.getHeight() <= mAppConfig.maxImageSizePx) {
          if (mSrcImage != null) {
            //mSrcImage.recycle();
            mSrcImage = null;
            System.gc();
          }
          mSrcImage = loadedImage;
          mSrcCamParameters = new FisheyeParameters(mSrcImage);
          //final float pixelDensity = 1.0f;
          // From gallery the bitmap size tells the actual image resolution, don't know why
          mDstCamParameters.imageWidthPx = mSrcImage.getWidth();
          mDstCamParameters.imageHeightPx = mSrcImage.getHeight();
          Log.d(TAG, "mDstImageSizePx in onActivityResult(.): "
                  + mDstCamParameters.imageWidthPx + "x" + mDstCamParameters.imageHeightPx);
          postRecalculateImagesIfHandlerIsCreated();

          ((TextView) findViewById(R.id.textViewSrcCamTitle)).setText(srcImageTitle);
          final String srcImageSizeText = mSrcImage.getWidth()
                  + " x " + mSrcImage.getHeight() + " " + getResources().getString(R.string.px);
          ((TextView) findViewById(R.id.textViewSrcCamImageSize)).setText(srcImageSizeText);
          SeekBar seekBarSrcCamPrincipalPointXPx = findViewById(R.id.seekBarSrcCamPrincipalPointXPx);
          seekBarSrcCamPrincipalPointXPx.setProgress(
                  valueScaleToSeekBarScale(seekBarSrcCamPrincipalPointXPx,
                          mSrcCamParameters.principalPointXPx, mSrcImage.getWidth()));
          SeekBar seekBarSrcCamPrincipalPointYPx = findViewById(R.id.seekBarSrcCamPrincipalPointYPx);
          seekBarSrcCamPrincipalPointYPx.setProgress(
                  valueScaleToSeekBarScale(seekBarSrcCamPrincipalPointYPx,
                          mSrcCamParameters.principalPointYPx, mSrcImage.getHeight()));
          ((EditText) findViewById(R.id.editTextDstImageWidthPx)).setText(
                  String.format(Locale.US, "%d", mDstCamParameters.imageWidthPx));
          ((EditText) findViewById(R.id.editTextDstImageHeightPx)).setText(
                  String.format(Locale.US, "%d", mDstCamParameters.imageHeightPx));
        } else {
          Toast.makeText(this, "Input image must be max. "
                          + mAppConfig.maxImageSizePx + " x " + mAppConfig.maxImageSizePx
                          + " pixels (and min. 1x1 pixels)",
                  Toast.LENGTH_LONG).show();
        }
        textViewStatus.setText(getResources().getString(R.string.status));
        textViewStatus.setVisibility(View.INVISIBLE);
      } catch (OutOfMemoryError ome) {
        handleOutOfMemory(getResources().getString(R.string.at_loading_imagepickedbyuser));
      } catch (Exception e) {
        Log.e(TAG, Utils.exceptionToStackTraceString(e));
      }
    }
  }

  private void requestToLoadImageFromGallery() {
    Intent intent = new Intent(Intent.ACTION_PICK);
    intent.setType("image/*");
    ArrayList<String> mimeTypes = new ArrayList<>();
    for (String sie : SUPPORTED_IMAGE_EXTENSIONS) {
      mimeTypes.add("image/" + sie);
    }
    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
    startActivityForResult(intent, PICKIMAGEFROMGALLERY_REQUESTCODE);
  }

  private void initiateSavingImage() {
    if (mDstImage != null) {
      Log.d(TAG, "Saving image to gallery...");
      if (ContextCompat.checkSelfPermission(this,
          Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        Log.i(TAG, "App does not have write permission");
        ActivityCompat.requestPermissions(this, new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITEEXTERNALSTORAGE_REQUESTCODE);
      } else {
        Log.i(TAG, "App has write permission");
        mDstImageFilename = getResources().getString(R.string.app_name) + "_"
            + Utils.getDateTimeStr() + "." + Utils.PNG_STR;
        LinearLayout layoutStatusPanel = findViewById(R.id.layoutStatusPanel);
        TextView textViewStatus = findViewById(R.id.textViewStatus);
        textViewStatus.setText(getResources().getString(R.string.saving_image));
        layoutStatusPanel.setVisibility(View.VISIBLE);

        Toast.makeText(this, getResources().getString(R.string.saving_image),
            Toast.LENGTH_SHORT).show();
        mImageSaverHandler.post(createSaveImageToGalleryRunnable());

        layoutStatusPanel.setVisibility(View.INVISIBLE);
        textViewStatus.setText(getResources().getString(R.string.status));
      }
    }
  }

  private Runnable createSaveImageToGalleryRunnable() {
    return new Runnable() {
      @Override
      public void run() {
        try {
          mSemaphore.acquire();
          saveImageToGallery();
          mSemaphore.release();
        } catch (InterruptedException ie) {
          Log.e(TAG, Utils.exceptionToStackTraceString(ie));
        }
      }
    };
  }

  private void saveImageToGallery() {
    try {
      switch (mAppConfig.releaseType) {
        case FREE:
          Utils.saveImageToExternalStorage(this, mDstImage, mDstImageFilename);
          mImageSavedNotificationText = "Image \'" + mDstImageFilename
                  + "\' saved to gallery with watermark.\n\n"
              + getString(R.string.buy_pro_to_remove_watemark)
              + "\n\n" + getResources().getString(R.string.may_take_time_to_appear_in_gallery);
          if (!mIsInterstitialAdOpen) {
            notifyUserThatImageIsSaved();
          }
          break;
        case PRO:
          Utils.saveImageToExternalStorage(this, mDstImage, mDstImageFilename);
          mImageSavedNotificationText = "Image \'" + mDstImageFilename + "\' saved to gallery.\n\n"
              + getResources().getString(R.string.may_take_time_to_appear_in_gallery);
          notifyUserThatImageIsSaved();
        break;
      }
    } catch (RuntimeException re) {
        Toast.makeText(this, re.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  void recalculateUpdatedImages() {
    if (mDstImage != null) {
      //mDstImage.recycle();
      mDstImage = null;
      System.gc();
    }
    try {
      mDstImage = mImageTransformer.transform(mSrcImage, mSrcCamParameters, mDstCamParameters);
      Log.d(TAG, "Image transformed");
      if (mAppConfig.releaseType == RELEASE_TYPE.FREE) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        //Log.d(TAG, "Adding watermark to image...");
        Utils.addWatermark(mDstImage, getResources().getString(
                R.string.free_version), displayMetrics);
        //Log.d(TAG, "Added watermark to image");
      }
    } catch (OutOfMemoryError ome) {
      handleOutOfMemory(getResources().getString(R.string.at_transforming_image));
    } catch (Exception e) {
      Log.e(TAG, "Failed to update images: " + Utils.exceptionToStackTraceString(e));
    }
  }

  void updateImagesInUi() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    if (mSmallSrcImage != null) {
      //mSmallSrcImage.recycle();
      mSmallSrcImage = null;
      System.gc();
    }
    int smallSrcImageWidth, smallSrcImageHeight;
    if (displayMetrics.widthPixels <= displayMetrics.heightPixels) {
      smallSrcImageHeight = (int) (SMALLIMAGE_SIZERATIO_PORTRAIT
              * (float) displayMetrics.heightPixels);
      smallSrcImageWidth = mSrcImage.getWidth() * smallSrcImageHeight / mSrcImage.getHeight();
    } else {
      smallSrcImageWidth = (int) (SMALLIMAGE_SIZERATIO_LANDSCAPE
              * (float) displayMetrics.widthPixels);
      smallSrcImageHeight = mSrcImage.getHeight() * smallSrcImageWidth / mSrcImage.getWidth();
    }
    try {
      mSmallSrcImage = Bitmap.createScaledBitmap(mSrcImage, smallSrcImageWidth,
              smallSrcImageHeight, false);
      mSrcCamView.setImageBitmap(mSmallSrcImage);
    } catch (OutOfMemoryError ome) {
      handleOutOfMemory(getResources().getString(R.string.at_creating_smallsrcimage));
    }

    mDstCamView.setImageBitmap(mDstImage);
      FrameLayout textLayoutAllPortrait = findViewById(R.id.layoutMainContent);
      if (textLayoutAllPortrait != null) {
        //if (constraintLayoutAll.getWidth() > 0 && constraintLayoutAll.getHeight() > 0) {
        final float dstImageWPerH = (float) mDstImage.getWidth() / (float) mDstImage.getHeight();
        final float allWPerH = (float) textLayoutAllPortrait.getWidth()
                / (float) textLayoutAllPortrait.getHeight();
        ImageView imageViewDstCam = findViewById(R.id.imageViewDstCam);
        ViewGroup.LayoutParams layoutParams = imageViewDstCam.getLayoutParams();
        if (dstImageWPerH > allWPerH) {
          layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
          layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
          layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        imageViewDstCam.setLayoutParams(layoutParams);
      } else {
        Log.d(TAG, "Top-level layout is null");
      }

      if (((Switch) findViewById(R.id.switchDstImageGridOverlay)).isChecked()) {
        //Log.d(TAG, "  mDstCamView w, h: " + mDstCamView.getWidth() + ", "
        //    + mDstCamView.getHeight());
        if (mDstCamView.getWidth() > 0 && mDstCamView.getHeight() > 0) {
          int gridCellSizePx = Math.max((int) ((float) Math.max(mDstCamView.getWidth(), mDstCamView.getHeight())
                  / (float) GRIDOVERLAY_CELLCOUNT), 2);
          final int deviceOrientation = Utils.getDeviceOrientation(this);
          if (mDstOverlayImage != null) {
            //mDstOverlayImage.recycle();
            mDstOverlayImage = null;
            System.gc();
          }
          int dstOverlayImageWidth, dstOverlayImageHeight;
          switch (deviceOrientation) {
            case ORIENTATION_LANDSCAPE:
              dstOverlayImageWidth = (int) ((float) mDstCamView.getHeight()
                      * (float) mDstImage.getWidth() / (float) mDstImage.getHeight());
              dstOverlayImageHeight = mDstCamView.getHeight();
              break;
            case ORIENTATION_PORTRAIT:
              dstOverlayImageWidth = mDstCamView.getWidth();
              dstOverlayImageHeight = (int) ((float) mDstCamView.getWidth() * (float) mDstImage.getHeight()
                      / (float) mDstImage.getWidth());
              break;
            default:
              throw new RuntimeException("Invalid device orientation: " + deviceOrientation);
          }
          try {
            //Log.d(TAG, "Creating grid overlay image...");
            mDstOverlayImage = Utils.createGridOverlayImage(dstOverlayImageWidth,
                    dstOverlayImageHeight, gridCellSizePx, GRIDOVERLAY_LINEWIDTH_PX,
                    getResources().getColor(R.color.colorAccent),
                    getResources().getColor(R.color.colorPrimary));
            //Log.d(TAG, "Created grid overlay image");
            mDstCamOverlayView.setImageBitmap(mDstOverlayImage);
            mDstCamOverlayView.setVisibility(View.VISIBLE);
          } catch (OutOfMemoryError ome) {
            handleOutOfMemory(getResources().getString(R.string.at_creating_dstoverlayimage));
          } catch (Exception e) {
            Log.e(TAG, "Failed to create grid overlay image: "
                    + Utils.exceptionToStackTraceString(e));
          }
        }
        //Log.d(TAG, "  mDstCamOverlayView w, h: " + mDstCamOverlayView.getWidth() + ", "
        //    + mDstCamOverlayView.getHeight());
      } else {
        findViewById(R.id.imageViewDstCamOverlay).setVisibility(View.INVISIBLE);
      }

    if (mAppConfig.releaseType == RELEASE_TYPE.FREE) {
      ((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
    }
  }

  private Runnable createRecalculateImagesRunnable() {
    return new Runnable() {
      @Override
      public void run() {
          try {
            //Log.d(TAG, "recalculateUpdatedImages acquiring semaphore...");
            mSemaphore.acquire();
            //Log.d(TAG, "recalculateUpdatedImages acquired semaphore");
            recalculateUpdatedImages();
            runOnUiThread(createUpdateImagesInUiRunnable());
            //Log.d(TAG, "recalculateUpdatedImages releasing semaphore...");
            mSemaphore.release();
            //Log.d(TAG, "recalculateUpdatedImages released semaphore");
          } catch (InterruptedException ie) {
            Log.e(TAG, Utils.exceptionToStackTraceString(ie));
          }
      }
    };
  }

  private Runnable createUpdateImagesInUiRunnable() {
    return new Runnable() {
      @Override
      public void run() {
        //Log.d(TAG, "updateImagesInUi trying to acquire semaphore...");
        if (mSemaphore.tryAcquire()) {
          //Log.d(TAG, "updateImagesInUi acquired semaphore");
          updateImagesInUi();
          //Log.d(TAG, "updateImagesInUi releasing semaphore...");
          mSemaphore.release();
          //Log.d(TAG, "updateImagesInUi released semaphore");
        } /*else {
          Log.d(TAG, "updateImagesInUi could not acquire semaphore");
        }*/
      }
    };
  }

  private void postRecalculateImagesIfHandlerIsCreated() {
    if (mImageUpdaterHandler != null) {
      mImageUpdaterHandler.removeCallbacksAndMessages(null);
      mImageUpdaterHandler.post(createRecalculateImagesRunnable());
    }
  }

  static float seekBarScaleToValueScale(final SeekBar seekBar, int progress, float valueMax) {
    return ((float) progress / (float) seekBar.getMax()) * valueMax;
  }

  static int valueScaleToSeekBarScale(final SeekBar seekBar, float value, float valueMax) {
    return (int) ((value / valueMax) * (float) seekBar.getMax());
  }

  static DstCamParameters.CAMERA_GEOMETRY cameraGeometry(final String geometryStr,
                                                         final Context context) {
    if (geometryStr.equals(context.getResources().getString(R.string.pinhole_geometry))) {
      return DstCamParameters.CAMERA_GEOMETRY.PINHOLE;
    } else if (geometryStr.equals(context.getResources().getString(
        R.string.equirectangular_geometry))) {
      return DstCamParameters.CAMERA_GEOMETRY.EQUIRECTANGULAR;
    } else {
      throw new IllegalArgumentException("Invalid camera geometry string: " + geometryStr);
    }
  }

  // https://stackoverflow.com/questions/55939853/how-to-work-with-androids-in-app-update-api/56154757
  void updateApp() {
    mAppUpdateManager = AppUpdateManagerFactory.create(this);
    mInstallStateUpdatedListener = new InstallStateUpdatedListener() {
      @Override
      public void onStateUpdate(InstallState state) {
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
          askUserToCompleteUpdate();
        } else if (state.installStatus() == InstallStatus.INSTALLED) {
          if (mAppUpdateManager != null) {
            mAppUpdateManager.unregisterListener(mInstallStateUpdatedListener);
          }
        } else {
          Log.i(TAG, "InstallStateUpdatedListener state: " + state.installStatus());
        }
      }
    };
    mAppUpdateManager.registerListener(mInstallStateUpdatedListener);

    mAppUpdateManager.getAppUpdateInfo().addOnSuccessListener(
        new OnSuccessListener<AppUpdateInfo>() {
          @Override
          public void onSuccess(AppUpdateInfo appUpdateInfo) {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
              try {
                mAppUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.FLEXIBLE,
                    MainActivity.this, UPDATEAPP_REQUESTCODE);
              } catch (IntentSender.SendIntentException sie) {
                Log.e(TAG, Utils.exceptionToStackTraceString(sie));
              }
            } else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
              askUserToCompleteUpdate();
            } else {
              Log.w(TAG, "Unhandled branch in appUpdateInfoTask");
            }
          }
        });

  }

  private void askUserToCompleteUpdate() {
    TextView textViewStatus = findViewById(R.id.textViewStatus);
    textViewStatus.setText(getResources().getString(R.string.appupdate_readytoinstall));
    textViewStatus.setVisibility(View.VISIBLE);
    findViewById(R.id.buttonInstallAppUpdate).setVisibility(View.VISIBLE);
    findViewById(R.id.buttonDontInstallAppUpdate).setVisibility(View.VISIBLE);
    findViewById(R.id.layoutStatusPanel).setVisibility(View.VISIBLE);
  }

  private void installAppUpdate(View view) {
    if (mAppUpdateManager != null) {
      mAppUpdateManager.completeUpdate();
    }
    findViewById(R.id.buttonInstallAppUpdate).setVisibility(View.GONE);
    findViewById(R.id.buttonDontInstallAppUpdate).setVisibility(View.GONE);
    findViewById(R.id.layoutStatusPanel).setVisibility(View.GONE);
    TextView textViewStatus = findViewById(R.id.textViewStatus);
    textViewStatus.setVisibility(View.GONE);
    textViewStatus.setText(getResources().getString(R.string.status));
  }

  private void dontInstallAppUpdate(View view) {
    findViewById(R.id.buttonInstallAppUpdate).setVisibility(View.GONE);
    findViewById(R.id.buttonDontInstallAppUpdate).setVisibility(View.GONE);
    findViewById(R.id.layoutStatusPanel).setVisibility(View.GONE);
    TextView textViewStatus = findViewById(R.id.textViewStatus);
    textViewStatus.setVisibility(View.GONE);
    textViewStatus.setText(getResources().getString(R.string.status));
  }

  private static int determineMaxImageSizePxBasedOnAvailableMemory(
          final DisplayMetrics displayMetrics) {
    Log.i(TAG, Utils.getMemoryUsageInfoText());
    final int availableHeapSizeMbytes = Utils.getAvailableHeapSizeMbytes();
    for (int maxImageSize : MAX_IMAGE_SIZES_PX) {
      final int requiredHeapSizeMbytesForThisImageSize =
              requiredHeapSizeMbytes(maxImageSize, displayMetrics);
      Log.i(TAG, "Required memory heap size for image size " + maxImageSize + ": "
              + requiredHeapSizeMbytesForThisImageSize + " MB");
      if (availableHeapSizeMbytes >= requiredHeapSizeMbytesForThisImageSize) {
        Log.i(TAG, "Found max. image size with which app fits into memory: " + maxImageSize);
        return maxImageSize;
      }
    }
    return -1;
  }

  private static int requiredHeapSizeMbytes(int maxImageSizePx,
                                            final DisplayMetrics displayMetrics) {
    final float justToMakeSureRatio = 1.1f;
    final float maxSrcImageSizeMbytes = (float) (maxImageSizePx * maxImageSizePx
            * Utils.SIZEOF_UCHAR4) / (float) (1024 * 1024);
    final float maxDstImageSizeMbytes = maxSrcImageSizeMbytes;
    final float maxSmallSrcImageSizeMbytes = (int) (Math.max(SMALLIMAGE_SIZERATIO_LANDSCAPE,
            SMALLIMAGE_SIZERATIO_PORTRAIT) * (float) maxSrcImageSizeMbytes);
    final int maxDstOverlayImageSizePx = Math.max(displayMetrics.widthPixels,
            displayMetrics.heightPixels);
    final float maxDstOverlayImageSizeMbytes = (float) (maxDstOverlayImageSizePx
            * maxDstOverlayImageSizePx * Utils.SIZEOF_UCHAR4) / (float) (1024 * 1024);
    final float allImagesSizeMbytes = maxSrcImageSizeMbytes + maxSmallSrcImageSizeMbytes
            + maxDstImageSizeMbytes + maxDstOverlayImageSizeMbytes;
    return (int) Math.ceil(allImagesSizeMbytes * justToMakeSureRatio);
  }

  private void handleOutOfMemory(final String debugInfoText) {
    Log.d(TAG, "handleOutOfMemory(.)...");
    Toast.makeText(this, getResources().getString(R.string.not_enough_free_memory),
            Toast.LENGTH_LONG).show();
    Log.e(TAG, getResources().getString(R.string.not_enough_free_memory) + " ("
            + debugInfoText + ")");
  }
}