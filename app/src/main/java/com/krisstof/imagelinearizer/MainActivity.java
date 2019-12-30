/*
 * TODO
 * - Measure hfovs
 * - Panoramizer
 * - Grid with on/off
 * - Check for updates and update
 * - Help
 * - Tests
 * - RELEASE FREE
 * - UI for rotating camera
 * - Online camera input
 *
 * Implemented
 * - Load image from Gallery
 * - Save image to Gallery
 * - Modify src, dst cam parameters via sliders
 */

package com.krisstof.imagelinearizer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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

  private static final int LOADIMAGEFROMGALLERY_REQUESTCODE = 100;
  private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {"jpeg", "jpg"};
  private static final int WRITEEXTERNALSTORAGE_REQUESTCODE = 200;
  private static final float SMALLIMAGE_SIZE_RATIO = 0.2f;
  private static final float INPUTPANEL_WIDTH_RATIO_LANDSCAPE = 0.4f;
  private static final int MAX_SRCIMAGETITLE_LENGTH = 32;

  private static final float SRCCAM_HFOVDEG_MAX = 270.0f;
  private static final float SRCCAM_FOCALLENGTH_MAX = 4.0f;
  private static final float DSTCAM_HFOVDEG_MAX = 170.0f;

  private static final int MAX_IMAGE_WIDTH_PX = 7680;
  private static final int MAX_IMAGE_HEIGHT_PX = MAX_IMAGE_WIDTH_PX;
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
      new int[]{320, 180});
  private static final ReleaseConfig PRO_RELEASECONFIG = new ReleaseConfig(RELEASE_TYPE.PRO,
      new int[]{MAX_IMAGE_WIDTH_PX, MAX_IMAGE_HEIGHT_PX});
  private static final ReleaseConfig RELEASECONFIG = FREE_RELEASECONFIG;
  //private static final ReleaseConfig RELEASECONFIG = PRO_RELEASECONFIG;

  private Bitmap mSrcImage;
  private FisheyeParameters mSrcCamParameters;
  private Bitmap mDstImage;

  private float mDstCamHfovDeg = 150.f;
  private int mDstImageWidthPx, mDstImageHeightPx;

  private ImageTransformer mImageTransformer;
  private ImageView mSrcCamView;
  private ImageView mDstCamView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mImageTransformer = new ImageTransformer(this);

    initUi();
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

    Log.d(TAG, "updateImages() in onCreate(.)...");
    updateImages();
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
    Log.d(TAG, "updateImages() in onConfigurationChanged(.)...");
    updateImages();
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
        setContentView(R.layout.activity_main_landscapeorientation);
        adjustViewSizesToLandscape();
        break;
      case ORIENTATION_PORTRAIT:
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
    //final int imageResourceId = R.drawable.cityview_150deg_4256x2832;
    final int imageResourceId = R.drawable.ladderboy_180_3025x2235;
    //final int imageResourceId = R.drawable.libraryhallway_195deg_3910x2607;
    mSrcImage = BitmapFactory.decodeResource(getResources(),
        imageResourceId, bitmapFactoryOptions)
        .copy(Bitmap.Config.ARGB_8888, false);
    mSrcCamParameters = new FisheyeParameters(imageResourceId);
    Log.d(TAG, "mSrcImage size in onCreate(.)...: " + mSrcImage.getWidth() + " x "
        + mSrcImage.getHeight());
    TextView textViewSrcCamTitle = findViewById(R.id.textViewSrcCamTitle);
    textViewSrcCamTitle.setText(getString(R.string.src_cam_title));

    mDstImageWidthPx = (int) ((float) mSrcImage.getWidth());
    mDstImageHeightPx = (int) ((float) mSrcImage.getHeight());
    Log.d(TAG, "mDstImageSizePx in onCreate(.): " + mDstImageWidthPx + "x" + mDstImageHeightPx);
    mDstCamView = findViewById(R.id.imageViewDstCam);

    SeekBar seekBarSrcCamHfovDeg = findViewById(R.id.seekBarSrcCamHfovDeg);
    seekBarSrcCamHfovDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSrcCamParameters.hfovDeg = seekBarScaleToValueScale(seekBar, progress, SRCCAM_HFOVDEG_MAX);
        ((TextView) findViewById(R.id.textViewSrcCamHfovDeg)).setText(String.format(
            Locale.getDefault(), getResources().getString(R.string.src_cam_hfovdeg_label) + ": " + FLOAT_FORMAT_STR + "°",
            mSrcCamParameters.hfovDeg));
        //Log.i(TAG, "mSrcCamHfovDeg, seekBarSrcCamHfovDeg: " + mSrcCamHfovDeg + ", " + seekBar.getProgress());
        Log.d(TAG, "updateImages() for seekBarSrcCamHfovDeg...");
        updateImages();
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
            Locale.getDefault(), getResources().getString(R.string.src_cam_focaloffset_label)
                + ": " + FLOAT_FORMAT_STR, mSrcCamParameters.focalOffset));
        //Log.i(TAG, "mSrcCamFocalOffset, seekBarSrcCamFocalOffset: " + mSrcCamFocalOffset + ", " + seekBar.getProgress());
        Log.d(TAG, "updateImages() for seekBarSrcCamFocalOffset...");
        updateImages();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    SeekBar seekBarSrcCamPrincipalPointXPx = findViewById(R.id.seekBarSrcCamPrincipalPointX);
    seekBarSrcCamPrincipalPointXPx.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSrcCamParameters.principalPointXPx = seekBarScaleToValueScale(seekBar, progress,
            mSrcImage.getWidth());
        ((TextView) findViewById(R.id.textViewSrcCamPrincipalPointX)).setText(
            String.format(Locale.getDefault(), getResources().getString(
                R.string.src_cam_principalpointx_label) + ": " + FLOAT_FORMAT_STR,
                mSrcCamParameters.principalPointXPx));
        Log.d(TAG, "updateImages() for seekBarSrcCamPrincipalPointXPx...");
        updateImages();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    SeekBar seekBarSrcCamPrincipalPointYPx = findViewById(R.id.seekBarSrcCamPrincipalPointY);
    seekBarSrcCamPrincipalPointYPx.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSrcCamParameters.principalPointYPx = seekBarScaleToValueScale(seekBar, progress,
            mSrcImage.getHeight());
        ((TextView) findViewById(R.id.textViewSrcCamPrincipalPointY)).setText(String.format(
            Locale.getDefault(), getResources().getString(
                R.string.src_cam_principalpointy_label) + ": " + FLOAT_FORMAT_STR + "",
            mSrcCamParameters.principalPointYPx));
        Log.d(TAG, "updateImages() for seekBarSrcCamPrincipalPointYPx...");
        updateImages();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekbar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekbar) {
      }
    });
    SeekBar seekBarDstCamHfovDeg = findViewById(R.id.seekBarDstCamHfovDeg);
    seekBarDstCamHfovDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mDstCamHfovDeg = seekBarScaleToValueScale(seekBar, progress, DSTCAM_HFOVDEG_MAX);
        ((TextView) findViewById(R.id.textViewDstCamHfovDeg)).setText(String.format(Locale.getDefault(), getResources().getString(R.string.dst_cam_hfovdeg_label) + ": " + FLOAT_FORMAT_STR + "°", mDstCamHfovDeg));
        //Log.i(TAG, "mDstCamHfovDeg, seekBarDstCamHfovDeg: " + mDstCamHfovDeg + ", " + seekBar.getProgress());
        Log.d(TAG, "updateImages() for seekBarDstCamHfovDeg...");
        updateImages();
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
        boolean needToReset = false;
        if (s.length() > 0) {
          int newDstImageWidthPx = Integer.parseInt(s.toString());
          if (newDstImageWidthPx > 0 && newDstImageWidthPx <= MAX_IMAGE_WIDTH_PX) {
            mDstImageWidthPx = newDstImageWidthPx;
          } else {
            needToReset = true;
          }
        }
        if (needToReset) {
          ((EditText) findViewById(R.id.editTextDstImageWidthPx)).setText(String.format(Locale.US,
              "%d", mDstImageWidthPx));
        }
      }
    });
    editTextDstImageWidthPx.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
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
        boolean needToReset = false;
        if (s.length() > 0) {
          int newDstImageHeightPx = Integer.parseInt(s.toString());
          if (newDstImageHeightPx > 0 && newDstImageHeightPx <= MAX_IMAGE_HEIGHT_PX) {
            mDstImageHeightPx = newDstImageHeightPx;
          } else {
            needToReset = true;
          }
        }
        if (needToReset) {
          ((EditText) findViewById(R.id.editTextDstImageHeightPx)).setText(String.format(Locale.US,
              "%d", mDstImageHeightPx));
        }
      }
    });
    editTextDstImageHeightPx.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
    seekBarSrcCamHfovDeg.setProgress(valueScaleToSeekBarScale(seekBarSrcCamHfovDeg,
        mSrcCamParameters.hfovDeg, SRCCAM_HFOVDEG_MAX));
    seekBarSrcCamFocalOffset.setProgress(valueScaleToSeekBarScale(seekBarSrcCamFocalOffset,
        mSrcCamParameters.focalOffset, SRCCAM_FOCALLENGTH_MAX));
    seekBarSrcCamPrincipalPointXPx.setProgress(valueScaleToSeekBarScale(seekBarSrcCamPrincipalPointXPx,
        mSrcCamParameters.principalPointXPx, mSrcImage.getWidth()));
    seekBarSrcCamPrincipalPointYPx.setProgress(valueScaleToSeekBarScale(seekBarSrcCamPrincipalPointYPx,
        mSrcCamParameters.principalPointYPx, mSrcImage.getHeight()));
    seekBarDstCamHfovDeg.setProgress(valueScaleToSeekBarScale(seekBarDstCamHfovDeg, mDstCamHfovDeg,
        DSTCAM_HFOVDEG_MAX));
    editTextDstImageWidthPx.setText(String.format(Locale.US, "%d", mDstImageWidthPx));
    editTextDstImageHeightPx.setText(String.format(Locale.US, "%d", mDstImageHeightPx));

    Switch switchAdvancedParameters = findViewById(R.id.switchAdvancedParameters);
    switchAdvancedParameters.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
              updateCameraParametersPanel(isChecked);
          }
      });
  }

  private void adjustViewSizesToLandscape() {
    LinearLayout layout = findViewById(R.id.linearLayoutCameraParameters);
    ViewGroup.LayoutParams params = layout.getLayoutParams();
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    params.width = (int) (INPUTPANEL_WIDTH_RATIO_LANDSCAPE * (float) displayMetrics.widthPixels);
    layout.setLayoutParams(params);
  }

  private void updateCameraParametersPanel(boolean advancedInputEnabled) {
    final int visibility;
    if (advancedInputEnabled) {
      visibility = View.VISIBLE;
    } else {
      visibility = View.GONE;
    }
    findViewById(R.id.textViewSrcCamPrincipalPointX).setVisibility(visibility);
    findViewById(R.id.seekBarSrcCamPrincipalPointX).setVisibility(visibility);
    findViewById(R.id.textViewSrcCamPrincipalPointY).setVisibility(visibility);
    findViewById(R.id.seekBarSrcCamPrincipalPointY).setVisibility(visibility);
  }

  private void showHelp() {
    // TODO
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (resultCode == Activity.RESULT_OK && requestCode == LOADIMAGEFROMGALLERY_REQUESTCODE) {
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
          final Bitmap loadedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(),
              imageUri).copy(Bitmap.Config.ARGB_8888, false);
          Log.d(TAG, "loadedImage size in onActivityResult(.): " + loadedImage.getWidth()
              + "x" + loadedImage.getHeight());
          if (mSrcImage.getWidth() <= MAX_IMAGE_WIDTH_PX
              && mSrcImage.getHeight() <= MAX_IMAGE_HEIGHT_PX) {
            mSrcImage = loadedImage;
            mSrcCamParameters = new FisheyeParameters(mSrcImage);
            //final float pixelDensity = 1.0f;
            // From gallery the bitmap size tells the actual image resolution, don't know why

            mDstImageWidthPx = mSrcImage.getWidth();
            mDstImageHeightPx = mSrcImage.getHeight();
            Log.d(TAG, "mDstImageSizePx in onActivityResult(.): " + mDstImageWidthPx + "x"
                + mDstImageHeightPx);

            Log.d(TAG, "updateImages() in onActivityResult(.)...");
            updateImages();

            ((TextView) findViewById(R.id.textViewSrcCamTitle)).setText(srcImageTitle);
            SeekBar seekBarSrcCamPrincipalPointXPx = findViewById(R.id.seekBarSrcCamPrincipalPointX);
            seekBarSrcCamPrincipalPointXPx.setProgress(
                valueScaleToSeekBarScale(seekBarSrcCamPrincipalPointXPx,
                    mSrcCamParameters.principalPointXPx, mSrcImage.getWidth()));
            SeekBar seekBarSrcCamPrincipalPointYPx = findViewById(R.id.seekBarSrcCamPrincipalPointY);
            seekBarSrcCamPrincipalPointYPx.setProgress(
                valueScaleToSeekBarScale(seekBarSrcCamPrincipalPointYPx,
                    mSrcCamParameters.principalPointYPx, mSrcImage.getHeight()));
            ((EditText) findViewById(R.id.editTextDstImageWidthPx)).setText(
                String.format(Locale.US, "%d", mDstImageWidthPx));
            ((EditText) findViewById(R.id.editTextDstImageHeightPx)).setText(
                String.format(Locale.US, "%d", mDstImageHeightPx));
          } else {
            Toast.makeText(this, "Output image must be max. "
                + MAX_IMAGE_WIDTH_PX + " pixels wide and max. "
                + MAX_IMAGE_HEIGHT_PX + " pixels high", Toast.LENGTH_LONG).show();
          }
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
    startActivityForResult(intent, LOADIMAGEFROMGALLERY_REQUESTCODE);
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
      String imageFilename = "linear_" + Utils.getDateTimeStr() + ".jpg";
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
    }
  }

  void updateImages() {
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

    mDstImage = mImageTransformer.linearize(mSrcImage, mSrcCamParameters, mDstImageWidthPx,
        mDstImageHeightPx, mDstCamHfovDeg);
    mDstCamView.setImageBitmap(mDstImage);
  }

  static float seekBarScaleToValueScale(final SeekBar seekBar, int progress, float valueMax) {
    return ((float) progress / (float) seekBar.getMax()) * valueMax;
  }

  static int valueScaleToSeekBarScale(final SeekBar seekBar, float value, float valueMax) {
    //Log.i(TAG, "  value, limits, normdValue; seekBar max: " + value + ", " + valueLimits[0] + "->" + valueLimits[1] + ", " + normdValue + "; " + seekBar.getMax());
    return (int) ((value / valueMax) * (float) seekBar.getMax());
  }
}