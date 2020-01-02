/*
 * TODO
 * - Measure hfovs
 * - Check for updates and update
 * - Help
 * - Tests
 * - RELEASE
 *
 * Known issues
 * - Grid overlay doesn't follow dst image while a text field is changed, only after the text field losing focus
 */

package com.krisstof.imagelinearizer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = MainActivity.class.getSimpleName();

  private static final int PICKIMAGEFROMGALLERY_REQUESTCODE = 100;
  private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {Utils.BMP_STR, Utils.JPEG_STR,
      Utils.JPG_STR, Utils.PNG_STR};
  private static final int WRITEEXTERNALSTORAGE_REQUESTCODE = 200;
  private static final float SMALLIMAGE_SIZE_RATIO = 0.2f;
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
  private static final float GRIDOVERLAY_THICKLINEWIDTH_PX = 2.f;
  private static final int GRIDOVERLAY_CELLCOUNT = 20;

  private static final int MIN_IMAGE_SIZE_PX = 1;
  private static final int MAX_IMAGE_SIZE_PX = 7680;
  private static final int BASELINE_DISPLAYDENSITY_DP = 160;
  private static final String FLOAT_FORMAT_STR = "%.2f";

  enum RELEASE_TYPE {FREE, PRO}

  private static final class ReleaseConfig {
    RELEASE_TYPE releaseType;
    int[] maxSavedImageSizePx;

    ReleaseConfig(RELEASE_TYPE releaseType, final int[] maxSavedImageSizePx) {
      if (maxSavedImageSizePx.length != 2) {
        throw new IllegalArgumentException("Max. image size must have 2 elements but it has "
            + maxSavedImageSizePx.length);
      }
      this.releaseType = releaseType;
      this.maxSavedImageSizePx = new int[]{maxSavedImageSizePx[0], maxSavedImageSizePx[1]};
    }
  }

  private static final ReleaseConfig FREE_RELEASECONFIG = new ReleaseConfig(RELEASE_TYPE.FREE,
      new int[]{320, 320});
  private static final ReleaseConfig PRO_RELEASECONFIG = new ReleaseConfig(RELEASE_TYPE.PRO,
      new int[]{MAX_IMAGE_SIZE_PX, MAX_IMAGE_SIZE_PX});
  //private static final ReleaseConfig RELEASECONFIG = FREE_RELEASECONFIG;
  private static final ReleaseConfig RELEASECONFIG = PRO_RELEASECONFIG;

  private Context mContext;
  private Thread mImageUpdaterThread;
  private Handler mImageUpdaterHandler;

  private Bitmap mSrcImage;
  private FisheyeParameters mSrcCamParameters;
  private Bitmap mDstImage;

  private DstCamParameters mDstCamParameters;

  private ImageTransformer mImageTransformer;
  private ImageView mSrcCamView;
  private ImageView mDstCamView;
  private ImageView mDstCamOverlayView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mContext = getApplicationContext();
    mImageTransformer = new ImageTransformer(this);
    mImageUpdaterThread = new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        mImageUpdaterHandler = new Handler() {
        };
        Looper.loop();
      }
    };
    mImageUpdaterThread.start();
    initUi();
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
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
    initUi();
    mImageUpdaterHandler.post(createRecalculateImagesRunnable());
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.barmenu, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.buttonloadImageFromGallery:
        loadImageFromGallery();
        break;
      case R.id.buttonSaveImageToGallery:
        saveImageToGallery();
        break;
      case R.id.buttonHelp:
        showHelp();
        break;
    }
    return super.onOptionsItemSelected(item);
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
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    final int deviceOrientation = Utils.getDeviceOrientation(displayMetrics);
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
    final float resourceDisplayDensity = getResources().getDisplayMetrics().density;
    Log.i(TAG, "Screen w, h, display density: " + displayMetrics.widthPixels + ", "
        + displayMetrics.heightPixels + ", " + resourceDisplayDensity);

    mSrcCamView = findViewById(R.id.imageViewSrcCam);
    //mSrcImage = ((BitmapDrawable) mSrcCamView.getDrawable()).getBitmap();
    BitmapFactory.Options bitmapFactoryOptions = new BitmapFactory.Options();
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
    final int imageResourceId = R.drawable.librarytable_195deg_3960x2640;  // Pp = img center
    //final int imageResourceId = R.drawable.redcityroad_120deg_3000x2000;  // Pp not img center
    TextView textViewSrcCamTitle = findViewById(R.id.textViewSrcCamTitle);
    textViewSrcCamTitle.setText(getString(R.string.src_cam_title));
    if (mSrcImage == null) {
      Log.d(TAG, "Loading sample image...");
      mSrcImage = BitmapFactory.decodeResource(getResources(),
          imageResourceId, bitmapFactoryOptions)
          .copy(Bitmap.Config.ARGB_8888, false);
      Log.d(TAG, "Sample image loaded");
      try {
        mSrcCamParameters = new FisheyeParameters(imageResourceId);
        Log.d(TAG, "Src cam pars at init: " + mSrcCamParameters.toString());
      } catch (IllegalArgumentException iae) {
        Toast.makeText(this, iae.getMessage(), Toast.LENGTH_LONG).show();
      }
      Log.d(TAG, "mSrcImage size in onCreate(.)...: " + mSrcImage.getWidth() + " x "
          + mSrcImage.getHeight());
    }

    if (mDstCamParameters == null) {
      mDstCamParameters = new DstCamParameters();
      mDstCamParameters.imageWidthPx = (int) ((float) mSrcImage.getWidth());
      mDstCamParameters.imageHeightPx = (int) ((float) mSrcImage.getHeight());
      //mDstCamParameters.geometry = DstCamParameters.CAMERA_GEOMETRY.EQUIRECTANGULAR;
      Log.d(TAG, "mDstImageSizePx in onCreate(.): " + mDstCamParameters.imageWidthPx + "x"
          + mDstCamParameters.imageHeightPx);
    }
    mDstCamView = findViewById(R.id.imageViewDstCam);
    mDstCamOverlayView = findViewById(R.id.imageViewDstCamOverlay);

    SeekBar seekBarSrcCamHfovDeg = findViewById(R.id.seekBarSrcCamHfovDeg);
    seekBarSrcCamHfovDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSrcCamParameters.hfovDeg = seekBarScaleToValueScale(seekBar, progress, SRCCAM_HFOVDEG_MAX);
        ((TextView) findViewById(R.id.textViewSrcCamHfovDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.srccam_hfovdeg_label) + ": " + FLOAT_FORMAT_STR + "°",
            mSrcCamParameters.hfovDeg));
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });

    Spinner spinnerDstCamGeometry = findViewById(R.id.spinnerDstCamGeometry);
    spinnerDstCamGeometry.setAdapter(new ArrayAdapter<>(this,
        R.layout.spinner_layout, getResources().getStringArray(R.array.dst_cam_geometry_choices)));
    spinnerDstCamGeometry.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
        mDstCamParameters.geometry = cameraGeometry((String) parentView.getItemAtPosition(position),
            mContext);
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
          mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
            ConstraintLayout constraintLayoutAll = findViewById(R.id.constraintLayoutAll);
            if (constraintLayoutAll != null) {
              //if (constraintLayoutAll.getWidth() > 0 && constraintLayoutAll.getHeight() > 0) {
              mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
          mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
            mImageUpdaterHandler.post(createRecalculateImagesRunnable());
          }
          Log.d(TAG, "Hiding keyboard...");
          Utils.hideKeyboard(v, mContext);
        }
      }
    });
    editTextDstImageHeightPx.setBackgroundColor(getResources().getColor(
        R.color.halfTransparentBlack));

    SeekBar seekBarDstCamRollDeg = findViewById(R.id.seekBarDstCamRollDeg);
    seekBarDstCamRollDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mDstCamParameters.rollDeg = seekBarScaleToValueScale(seekBar, progress,
            DSTCAM_ROLL_SPAN_DEG) - 0.5f * DSTCAM_ROLL_SPAN_DEG;
        ((TextView) findViewById(R.id.textViewDstCamRollDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.dstcam_zrotdeg_label)
                + ": " + FLOAT_FORMAT_STR + "°", mDstCamParameters.rollDeg));
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    SeekBar seekBarDstCamPitchDeg = findViewById(R.id.seekBarDstCamPitchDeg);
    seekBarDstCamPitchDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mDstCamParameters.pitchDeg = seekBarScaleToValueScale(seekBar, progress,
            DSTCAM_PITCH_SPAN_DEG) - 0.5f * DSTCAM_PITCH_SPAN_DEG;
        ((TextView) findViewById(R.id.textViewDstCamPitchDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.dstcam_xrotdeg_label)
                + ": " + FLOAT_FORMAT_STR + "°", mDstCamParameters.pitchDeg));
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    SeekBar seekBarDstCamYawDeg = findViewById(R.id.seekBarDstCamYawDeg);
    seekBarDstCamYawDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mDstCamParameters.yawDeg = seekBarScaleToValueScale(seekBar, progress, DSTCAM_YAW_SPAN_DEG)
            - 0.5f * DSTCAM_YAW_SPAN_DEG;
        ((TextView) findViewById(R.id.textViewDstCamYawDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.dstcam_yrotdeg_label)
                + ": " + FLOAT_FORMAT_STR + "°", mDstCamParameters.yawDeg));
        mImageUpdaterHandler.post(createRecalculateImagesRunnable());
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
            mImageUpdaterHandler.post(createRecalculateImagesRunnable());
          }
        });

    ((TextView) findViewById(R.id.textViewStatus)).setText(
        getResources().getString(R.string.loading));

    updateCameraParametersPanelVisibilities();
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

    mDstCamOverlayView.post(new Runnable() {
      @Override
      public void run() {
        adjustUiComponentsToBeAdjustedAfterViewsHaveBeenCreated();
      }
    });
  }

  void adjustUiComponentsToBeAdjustedAfterViewsHaveBeenCreated() {
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

    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    final int deviceOrientation = Utils.getDeviceOrientation(displayMetrics);
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

    TextView textViewStatus = findViewById(R.id.textViewStatus);
    textViewStatus.setText(getResources().getString(R.string.status));
    textViewStatus.setVisibility(View.INVISIBLE);
  }

  Integer readValueIfTextIsValid(EditText editText, int existingValue,
                                 boolean resetTextIfZero) {
    Integer retVal = null;
    final String text = editText.getText().toString();
    boolean needToResetText = false;
    if (text.length() > 0) {
      try {
        int newDstImageHeightPx = Integer.parseInt(text);
        if (newDstImageHeightPx > 0 && newDstImageHeightPx <= MAX_IMAGE_SIZE_PX) {
          retVal = newDstImageHeightPx;
          if (text.charAt(0) == '0') {
            editText.setText(String.format(Locale.US, "%d", retVal));
          }
        } else {
          needToResetText = true;
          if (newDstImageHeightPx > MAX_IMAGE_SIZE_PX) {
            Toast.makeText(this, "Image width and height can be max. "
                    + MAX_IMAGE_SIZE_PX + " pixels",
                Toast.LENGTH_LONG).show();
          } else if (!resetTextIfZero) {
            needToResetText = false;
          }
        }
      } catch (NumberFormatException nfe) {
        Log.d(TAG, Utils.stackTraceToString(nfe));
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
    /* No
    scrollViewCameraParameters.setMinimumWidth((int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT
        * (float) displayMetrics.widthPixels));
    scrollViewCameraParameters.setMinimumHeight((int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT
        * (float) displayMetrics.widthPixels));*/
    /* No
    ScrollView.LayoutParams scrollViewsLayoutparams
        = scrollViewCameraParameters.getLayoutParams();*/
    scrollViewCameraParameters.getLayoutParams().width =
        (int) (CAMERAPARAMETERSPANEL_WIDTH_RATIO_PORTRAIT * (float) displayMetrics.widthPixels);
    scrollViewCameraParameters.getLayoutParams().height =
        (int) (CAMERAPARAMETERSPANEL_HEIGHT_RATIO_PORTRAIT * (float) displayMetrics.heightPixels);
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
    findViewById(R.id.textViewDstCamPitchDeg).setVisibility(visibility);
    findViewById(R.id.seekBarDstCamPitchDeg).setVisibility(visibility);
    findViewById(R.id.textViewDstCamYawDeg).setVisibility(visibility);
    findViewById(R.id.seekBarDstCamYawDeg).setVisibility(visibility);
    findViewById(R.id.textViewDstCamRollDeg).setVisibility(visibility);
    findViewById(R.id.seekBarDstCamRollDeg).setVisibility(visibility);
  }

  private void showHelp() {
    // TODO
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (resultCode == Activity.RESULT_OK && requestCode == PICKIMAGEFROMGALLERY_REQUESTCODE) {
      try {
        final Uri imageUri = intent.getData();
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

          final Bitmap loadedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(),
              imageUri).copy(Bitmap.Config.ARGB_8888, false);
          Log.d(TAG, "loadedImage size in onActivityResult(.): " + loadedImage.getWidth()
              + "x" + loadedImage.getHeight());
          if (mSrcImage.getWidth() <= MAX_IMAGE_SIZE_PX
              && mSrcImage.getHeight() <= MAX_IMAGE_SIZE_PX) {
            mSrcImage = loadedImage;
            mSrcCamParameters = new FisheyeParameters(mSrcImage);
            //final float pixelDensity = 1.0f;
            // From gallery the bitmap size tells the actual image resolution, don't know why

            mDstCamParameters.imageWidthPx = mSrcImage.getWidth();
            mDstCamParameters.imageHeightPx = mSrcImage.getHeight();
            Log.d(TAG, "mDstImageSizePx in onActivityResult(.): "
                + mDstCamParameters.imageWidthPx + "x" + mDstCamParameters.imageHeightPx);

            mImageUpdaterHandler.post(createRecalculateImagesRunnable());

            ((TextView) findViewById(R.id.textViewSrcCamTitle)).setText(srcImageTitle);
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
            Toast.makeText(this, "Output image must be max. "
                + MAX_IMAGE_SIZE_PX + " pixels wide and max. "
                + MAX_IMAGE_SIZE_PX + " pixels high", Toast.LENGTH_LONG).show();
          }
          textViewStatus.setText(getResources().getString(R.string.status));
          textViewStatus.setVisibility(View.INVISIBLE);
        }
      } catch (Exception e) {
        Log.e(TAG, Utils.stackTraceToString(e));
      }
    }
  }

  private void loadImageFromGallery() {
    Intent intent = new Intent(Intent.ACTION_PICK);
    intent.setType("image/*");
    ArrayList<String> mimeTypes = new ArrayList<>();
    for (String sie : SUPPORTED_IMAGE_EXTENSIONS) {
      mimeTypes.add("image/" + sie);
    }
    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
    startActivityForResult(intent, PICKIMAGEFROMGALLERY_REQUESTCODE);
  }

  private void saveImageToGallery() {
    Log.d(TAG, "Saving image to gallery...");
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "App does not have write permission");
      //<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:remove="android:maxSdkVersion" />
      //<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:node="replace"/>
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITEEXTERNALSTORAGE_REQUESTCODE);
      //Toast.makeText(this, "App does not have permission to write to storage. Adjust app permissions in your App Settings", Toast.LENGTH_SHORT).show();
    } else {
      Log.i(TAG, "App has write permission");
      //String imageFilename = "linear_" + Utils.getDateTimeStr() + "." + Utils.JPG_STR;
      String imageFilename = "linear_" + Utils.getDateTimeStr() + "." + Utils.PNG_STR;
      TextView textViewStatus = findViewById(R.id.textViewStatus);
      textViewStatus.setText(getResources().getString(R.string.saving_image));
      textViewStatus.setVisibility(View.VISIBLE);
      switch (RELEASECONFIG.releaseType) {
        case FREE:
          final Bitmap reducedDstImage = Utils.reduceImageToFit(mDstImage,
              RELEASECONFIG.maxSavedImageSizePx[0], RELEASECONFIG.maxSavedImageSizePx[1]);
          Utils.saveImageToExternalStorage(this, reducedDstImage, imageFilename);
          Toast.makeText(this, "Saved image \'" + imageFilename + "\' to Gallery in "
              + reducedDstImage.getWidth() + "x"
              + reducedDstImage.getHeight() + " resolution.\n\n"
              + getString(R.string.buy_pro_for_high_resolution), Toast.LENGTH_LONG).show();
          break;
        case PRO:
          try {
            Utils.saveImageToExternalStorage(this, mDstImage, imageFilename);
            Toast.makeText(this, "Saved image \'" + imageFilename + "\' to Gallery.",
                Toast.LENGTH_LONG).show();
          } catch (RuntimeException re) {
            Toast.makeText(this, re.getMessage(), Toast.LENGTH_LONG).show();
          }
          break;
      }
      textViewStatus.setVisibility(View.INVISIBLE);
      textViewStatus.setText(getResources().getString(R.string.status));
    }
  }


  void recalculateUpdatedImages() {
    mDstImage = mImageTransformer.linearize(mSrcImage, mSrcCamParameters, mDstCamParameters);
  }

  void updateImagesInUi() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    if (displayMetrics.widthPixels <= displayMetrics.heightPixels) {
      final int srcImageSmallHeight = (int) (SMALLIMAGE_SIZE_RATIO
          * (float) displayMetrics.heightPixels);
      mSrcCamView.setImageBitmap(Bitmap.createScaledBitmap(mSrcImage,
          mSrcImage.getWidth() * srcImageSmallHeight / mSrcImage.getHeight(),
          srcImageSmallHeight, false));
    } else {
      final int srcImageSmallWidth = (int) (SMALLIMAGE_SIZE_RATIO
          * (float) displayMetrics.widthPixels);
      mSrcCamView.setImageBitmap(Bitmap.createScaledBitmap(mSrcImage, srcImageSmallWidth,
          mSrcImage.getHeight() * srcImageSmallWidth / mSrcImage.getWidth(), false));
    }

    mDstCamView.setImageBitmap(mDstImage);

    FrameLayout textLayoutAllPortrait = findViewById(R.id.textLayoutAllPortrait);
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
        //ConstraintLayout.LayoutParams layoutParams =
        //    (ConstraintLayout.LayoutParams) imageViewDstCam.getLayoutParams();
        //ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(
        //    ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
      } else {
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
      }
      imageViewDstCam.setLayoutParams(layoutParams);
      //findViewById(R.id.imageViewDstCamOverlay).setLayoutParams(layoutParams);
      //}
    } else {
      Log.d(TAG, "Top-level layout (constraintLayoutAll) is null");
    }

    if (((Switch) findViewById(R.id.switchDstImageGridOverlay)).isChecked()) {
      //Log.d(TAG, "  mDstCamView w, h: " + mDstCamView.getWidth() + ", "
      //    + mDstCamView.getHeight());
      if (mDstCamView.getWidth() > 0 && mDstCamView.getHeight() > 0) {
        int gridCellSizePx = Math.max((int) ((float) Math.max(mDstCamView.getWidth(), mDstCamView.getHeight())
            / (float) GRIDOVERLAY_CELLCOUNT), 2);
        final int deviceOrientation = Utils.getDeviceOrientation(displayMetrics);
        switch (deviceOrientation) {
          case ORIENTATION_LANDSCAPE:
            mDstCamOverlayView.setImageBitmap(
                Utils.createGridOverlayImage(mDstCamView.getHeight()
                        * mDstImage.getWidth() / mDstImage.getHeight(), mDstCamView.getHeight(),
                    gridCellSizePx, GRIDOVERLAY_THICKLINEWIDTH_PX,
                    getResources().getColor(R.color.colorAccent),
                    getResources().getColor(R.color.colorPrimary)));
            break;
          case ORIENTATION_PORTRAIT:
            mDstCamOverlayView.setImageBitmap(
                Utils.createGridOverlayImage(mDstCamView.getWidth(), mDstCamView.getWidth()
                        * mDstImage.getHeight() / mDstImage.getWidth(),
                    gridCellSizePx, GRIDOVERLAY_THICKLINEWIDTH_PX,
                    getResources().getColor(R.color.colorAccent),
                    getResources().getColor(R.color.colorPrimary)));
            break;
          default:
            throw new RuntimeException("Invalid device orientation: " + deviceOrientation);
        }

        mDstCamOverlayView.setVisibility(View.VISIBLE);
      }
      //Log.d(TAG, "  mDstCamOverlayView w, h: " + mDstCamOverlayView.getWidth() + ", "
      //    + mDstCamOverlayView.getHeight());
    } else {
      findViewById(R.id.imageViewDstCamOverlay).setVisibility(View.INVISIBLE);
    }
  }

  private Runnable createRecalculateImagesRunnable() {
    return new Runnable() {
      @Override
      public void run() {
        recalculateUpdatedImages();
        runOnUiThread(createUpdateImagesInUiRunnable());
      }
    };
  }

  private Runnable createUpdateImagesInUiRunnable() {
    return new Runnable() {
      @Override
      public void run() {
        updateImagesInUi();
      }
    };
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
}