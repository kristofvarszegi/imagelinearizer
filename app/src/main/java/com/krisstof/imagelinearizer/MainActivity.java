/**
 TODO
 - GLSView border
 - Render offscreen with real dst image size and save that to storage
 - Help
 - UI for rotating camera
 - Fix advanced input switch text
 - Panoramizer
 - Online camera input

 Implemented
 - Load image from Gallery
 - Save image to Gallery
 - Modify src, dst cam parameters via sliders

 */

package com.krisstof.imagelinearizer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;

import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main";

    private static final float SRCCAM_HFOVDEG_MAX = 360.0f;
    private static final float SRCCAM_FOCALLENGTH_MAX = 7.0f;
    private static final float SRCCAM_OPTICALCENTER_1DIM_MAX = 1.0f;
    private static final float DSTCAM_HFOVDEG_MAX = 170.0f;
    private static final int DSTCAM_IMGSIZEX_MAX = 1920 * 4;
    private static final int DSTCAM_IMGSIZEY_MAX = 1080 * 4;
    private static final float IMAGEFACEPOS_Y_NORMALINPUT = 0.0f;
    private static final float IMAGEFACEPOS_Y_ADVANCEDINPUT = 0.3f;

    private static final int LOADIMAGEFROMGALLERY_REQUESTCODE = 100;
    private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {"jpeg", "jpg", "png"};
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200;

    private static final String FLOAT_FORMAT_STR = "%.2f";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private Uri mSrcImageUri;
    private Bitmap mSrcImage;
    private float mSrcCamHfovDeg = 126.0f;
    private float mSrcCamFocalLength = 2.2f;
    private float[] mSrcCamOpticalCenter = new float[]{0.5f, 0.5f};
    private float mDstCamHfovDeg = 90.0f;
    private int[] mDstCamImgSizePx = new int[]{0, 0};

    private ImageView mSrcCamView;
    private ImageTransformerView mDstCamView;
    private ImageTransformerRenderer mDstCamRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        final int[] screenSize = new int[]{displayMetrics.widthPixels, displayMetrics.heightPixels};
        Log.i(TAG, "Screen w, h: " + displayMetrics.widthPixels + ", " + displayMetrics.heightPixels);

        mSrcCamView = findViewById(R.id.imageViewSrcCam);
        //mSrcImage = ((BitmapDrawable) mSrcCamView.getDrawable()).getBitmap();
        mSrcImage = BitmapFactory.decodeResource(getResources(), R.drawable.fisheye_120deg_600x400).copy(Bitmap.Config.ARGB_8888, false);
        int srcImageSmallHeight = getSmallImageHeightPx();
        mSrcCamView.setImageBitmap(Bitmap.createScaledBitmap(mSrcImage, mSrcImage.getWidth() * srcImageSmallHeight / mSrcImage.getHeight(), srcImageSmallHeight, false));
        Log.i(TAG, "mSrcImage size in onCreate(.)...: " + mSrcImage.getWidth() + "x" + mSrcImage.getHeight());

        final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        final boolean supportsOpenGlEs2 = configurationInfo.reqGlEsVersion >= 0x20000;
        if (!supportsOpenGlEs2) {
            Log.e(TAG, "Device does not support OpenGL ES 2");
            return;
        }

        final float dpi = getResources().getDisplayMetrics().density;
        Log.i(TAG, "dpi: " + dpi);
        mDstCamImgSizePx[0] = (int) ((float) mSrcImage.getWidth() / dpi);
        mDstCamImgSizePx[1] = (int) ((float) mSrcImage.getHeight() / dpi);
        //mDstCamImgSizePx[0] = mSrcImage.getWidth();
        //mDstCamImgSizePx[1] = mSrcImage.getHeight();
        Log.i(TAG, "mDstCamImgSizePx in onCreate(.): " + mDstCamImgSizePx[0] + "x" + mDstCamImgSizePx[1]);
        mDstCamView = findViewById(R.id.imageTransformerViewDstCam);
        mDstCamRenderer = new ImageTransformerRenderer(this, screenSize, mSrcImage, mSrcCamHfovDeg, mSrcCamFocalLength, mSrcCamOpticalCenter, mDstCamHfovDeg, mDstCamImgSizePx);
        mDstCamView.setRenderer(mDstCamRenderer);
        //mDstCamView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        //mDstCamView = new ImageTransformerView(this, mSrcImage);
        //((LinearLayout) findViewById(R.id.linearLayoutImagesPanel)).addView(mDstCamView);
        Log.i(TAG, "mDstCamView w, h: " + mDstCamView.getWidth() + ", " + mDstCamView.getHeight());

        SeekBar seekBarSrcCamHfovDeg = findViewById(R.id.seekBarSrcCamHfovDeg);
        seekBarSrcCamHfovDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mSrcCamHfovDeg = seekBarScaleToValueScale(seekBar, progress, SRCCAM_HFOVDEG_MAX);
                ((TextView) findViewById(R.id.textViewSrcCamHfovDeg)).setText(String.format(Locale.getDefault(), getResources().getString(R.string.src_cam_hfovdeg_label) + ": " + FLOAT_FORMAT_STR + "°", mSrcCamHfovDeg));
                //Log.i(TAG, "mSrcCamHfovDeg, seekBarSrcCamHfovDeg: " + mSrcCamHfovDeg + ", " + seekBar.getProgress());
                updateImages();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekbar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekbar) {}
        });
        SeekBar seekBarSrcCamFocalLength = findViewById(R.id.seekBarSrcCamFocalLength);
        seekBarSrcCamFocalLength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mSrcCamFocalLength = seekBarScaleToValueScale(seekBar, progress,SRCCAM_FOCALLENGTH_MAX);
                ((TextView) findViewById(R.id.textViewSrcCamFocalLength)).setText(String.format(Locale.getDefault(), getResources().getString(R.string.src_cam_focallength_label) + ": " + FLOAT_FORMAT_STR, mSrcCamFocalLength));
                //Log.i(TAG, "mSrcCamFocalLength, seekBarSrcCamFocalLength: " + mSrcCamFocalLength + ", " + seekBar.getProgress());
                updateImages();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekbar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekbar) {}
        });
        SeekBar seekBarSrcCamOpticalCenterX = findViewById(R.id.seekBarSrcCamOpticalCenterX);
        seekBarSrcCamOpticalCenterX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mSrcCamOpticalCenter[0] = seekBarScaleToValueScale(seekBar, progress, SRCCAM_OPTICALCENTER_1DIM_MAX);
                ((TextView) findViewById(R.id.textViewSrcCamOpticalCenterX)).setText(String.format(Locale.getDefault(), getResources().getString(R.string.src_cam_opticalcenterx_label) + ": " + FLOAT_FORMAT_STR + "", mSrcCamOpticalCenter[0]));
                updateImages();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekbar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekbar) {}
        });
        SeekBar seekBarSrcCamOpticalCenterY = findViewById(R.id.seekBarSrcCamOpticalCenterY);
        seekBarSrcCamOpticalCenterY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mSrcCamOpticalCenter[1] = seekBarScaleToValueScale(seekBar, progress, SRCCAM_OPTICALCENTER_1DIM_MAX);
                ((TextView) findViewById(R.id.textViewSrcCamOpticalCenterY)).setText(String.format(Locale.getDefault(), getResources().getString(R.string.src_cam_opticalcentery_label) + ": " + FLOAT_FORMAT_STR + "", mSrcCamOpticalCenter[1]));
                updateImages();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekbar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekbar) {}
        });
        SeekBar seekBarDstCamHfovDeg = findViewById(R.id.seekBarDstCamHfovDeg);
        seekBarDstCamHfovDeg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mDstCamHfovDeg = seekBarScaleToValueScale(seekBar, progress, DSTCAM_HFOVDEG_MAX);
                ((TextView) findViewById(R.id.textViewDstCamHfovDeg)).setText(String.format(Locale.getDefault(), getResources().getString(R.string.dst_cam_hfovdeg_label) + ": " + FLOAT_FORMAT_STR + "°", mDstCamHfovDeg));
                //Log.i(TAG, "mDstCamHfovDeg, seekBarDstCamHfovDeg: " + mDstCamHfovDeg + ", " + seekBar.getProgress());
                updateImages();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekbar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekbar) {}
        });
        EditText editTextDstCamImgSizePxX = findViewById(R.id.editTextDstCamImgSizePxX);
        editTextDstCamImgSizePxX.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean needToReset = false;
                if (s.length() > 0) {
                    int newDstCamImgSizeX = Integer.parseInt(s.toString());
                    if (newDstCamImgSizeX > 0 && newDstCamImgSizeX < DSTCAM_IMGSIZEX_MAX) {
                        mDstCamImgSizePx[0] = newDstCamImgSizeX;
                        mDstCamRenderer.setDstCamImgSizePx(mDstCamImgSizePx);
                    } else {
                        needToReset = true;
                    }
                }
                if (needToReset) {
                    ((EditText) findViewById(R.id.editTextDstCamImgSizePxX)).setText(Integer.toString(mDstCamImgSizePx[0]));
                }
            }
        });
        EditText editTextDstCamImgSizePxY = findViewById(R.id.editTextDstCamImgSizePxY);
        editTextDstCamImgSizePxY.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean needToReset = false;
                if (s.length() > 0) {
                    int newDstCamImgSizeY = Integer.parseInt(s.toString());
                    if (newDstCamImgSizeY > 0 && newDstCamImgSizeY < DSTCAM_IMGSIZEY_MAX) {
                        mDstCamImgSizePx[1] = newDstCamImgSizeY;
                        mDstCamRenderer.setDstCamImgSizePx(mDstCamImgSizePx);
                    } else {
                        needToReset = true;
                    }
                }
                if (needToReset) {
                    ((EditText) findViewById(R.id.editTextDstCamImgSizePxY)).setText(Integer.toString(mDstCamImgSizePx[1]));
                }
            }
        });
        seekBarSrcCamHfovDeg.setProgress(valueScaleToSeekBarScale(seekBarSrcCamHfovDeg, mSrcCamHfovDeg, SRCCAM_HFOVDEG_MAX));
        seekBarSrcCamFocalLength.setProgress(valueScaleToSeekBarScale(seekBarSrcCamFocalLength, mSrcCamFocalLength, SRCCAM_FOCALLENGTH_MAX));
        seekBarSrcCamOpticalCenterX.setProgress(valueScaleToSeekBarScale(seekBarSrcCamOpticalCenterX, mSrcCamOpticalCenter[0], SRCCAM_OPTICALCENTER_1DIM_MAX));
        seekBarSrcCamOpticalCenterY.setProgress(valueScaleToSeekBarScale(seekBarSrcCamOpticalCenterY, mSrcCamOpticalCenter[1], SRCCAM_OPTICALCENTER_1DIM_MAX));
        seekBarDstCamHfovDeg.setProgress(valueScaleToSeekBarScale(seekBarDstCamHfovDeg, mDstCamHfovDeg, DSTCAM_HFOVDEG_MAX));
        editTextDstCamImgSizePxX.setText(Integer.toString(mDstCamImgSizePx[0]));
        editTextDstCamImgSizePxY.setText(Integer.toString(mDstCamImgSizePx[1]));

        TextView textViewDstCamImgSizePx = findViewById(R.id.textViewDstCamImgSizePx);
        editTextDstCamImgSizePxX.setTextSize(textViewDstCamImgSizePx.getTextSize() / dpi);
        editTextDstCamImgSizePxY.setTextSize(textViewDstCamImgSizePx.getTextSize() / dpi);

        //((TextView) findViewById(R.id.textViewSrcCamHfovDeg)).setText(String.format(Locale.getDefault(), R.string.src_cam_hfovdeg_label + ": " + FLOAT_FORMAT_STR + "°", mSrcCamHfovDeg));
        //((TextView) findViewById(R.id.textViewSrcCamFocalLength)).setText(String.format(Locale.getDefault(), R.string.src_cam_focallength_label + ": " + FLOAT_FORMAT_STR, mSrcCamFocalLength));
        //((TextView) findViewById(R.id.textViewSrcCamFocalLength)).setText(String.format(Locale.getDefault(), R.string.src_cam_opticalcenterx_label + ": " + FLOAT_FORMAT_STR, mSrcCamOpticalCenter[0]));
        //((TextView) findViewById(R.id.textViewSrcCamFocalLength)).setText(String.format(Locale.getDefault(), R.string.src_cam_opticalcentery_label + ": " + FLOAT_FORMAT_STR, mSrcCamOpticalCenter[1]));
        //((TextView) findViewById(R.id.textViewDstCamHfovDeg)).setText(String.format(Locale.getDefault(), R.string.dst_cam_hfovdeg_label + ": " + FLOAT_FORMAT_STR + "°", mDstCamHfovDeg));

        Log.i(TAG, "mSrcCamHfovDeg, seekBarSrcCamHfovDeg: " + mSrcCamHfovDeg + ", " + seekBarSrcCamHfovDeg.getProgress());
        Log.i(TAG, "mSrcCamFocalLength, seekBarSrcCamFocalLength: " + mSrcCamFocalLength + ", " + seekBarSrcCamFocalLength.getProgress());
        Log.i(TAG, "mDstCamHfovDeg, seekBarDstCamHfovDeg: " + mDstCamHfovDeg + ", " + seekBarDstCamHfovDeg.getProgress());

        updateImages();
        //saveImageToGallery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mDstCamView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDstCamView.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.barmenu, menu);

        Switch switchAdvancedInputPanel = menu.findItem(R.id.layoutSwitchAdvancedInputPanel)
                .getActionView().findViewById(R.id.switchAdvancedInputPanel);
        switchAdvancedInputPanel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateInputPanel(isChecked);
            }
        });
        updateInputPanel(switchAdvancedInputPanel.isChecked());

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

    private void loadImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        ArrayList<String> mimeTypes = new ArrayList<>();
        for (String sie : SUPPORTED_IMAGE_EXTENSIONS) {
            mimeTypes.add("image/" + sie);
        }
        //String[] mimeTypes = {"image/jpeg", "image/png"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, LOADIMAGEFROMGALLERY_REQUESTCODE);
    }

    private void saveImageToGallery() {
        /*final String srcImagePath = mSrcImageUri.getPath();
        String dstImagePath = "";
        for (String sie : SUPPORTED_IMAGE_EXTENSIONS) {
            if (srcImagePath.endsWith(sie)) {
                StringBuilder postfix = new StringBuilder("__linear_");
                postfix.append("shf" + mSrcCamHfovDeg);
                postfix.append("sfl" + mSrcCamFocalLength);
                if (mSrcCamOpticalCenter[0] != 0.5 | mSrcCamOpticalCenter[1] != 0.5) {
                    postfix.append("soc" + mSrcCamOpticalCenter[0] + "," + mSrcCamOpticalCenter[1]);
                }
                postfix.append("dst" + mDstCamHfovDeg);
                dstImagePath = srcImagePath.replaceAll("." + sie + "$",  postfix + "." + sie);
                break;
            }
        }*/
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Does not have write permission");
            //<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:remove="android:maxSdkVersion" />
            //<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:node="replace"/>
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            //Toast.makeText(this, "App does not have permission to write to storage. Adjust app permissions in your App Settings", Toast.LENGTH_SHORT).show();
        } else {
            Log.i(TAG, "Has write permission");
            String imageTitle = "linear_" + getDateTimeStr();
            MediaStore.Images.Media.insertImage(getContentResolver(), mSrcImage, imageTitle, "");
            Toast.makeText(this, "Saved image to Gallery with title \'" + imageTitle + "\'", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                }
                break;
            }
        }
    }

    private void updateInputPanel(boolean advancedInputEnabled) {
        final int visibility;
        if (advancedInputEnabled) {
            mDstCamRenderer.setTargetImageFacePosY(IMAGEFACEPOS_Y_ADVANCEDINPUT);
            visibility = View.VISIBLE;
        } else {
            mDstCamRenderer.setTargetImageFacePosY(IMAGEFACEPOS_Y_NORMALINPUT);
            visibility = View.GONE;
        }
        findViewById(R.id.textViewSrcCamOpticalCenterX).setVisibility(visibility);
        findViewById(R.id.seekBarSrcCamOpticalCenterX).setVisibility(visibility);
        findViewById(R.id.textViewSrcCamOpticalCenterY).setVisibility(visibility);
        findViewById(R.id.seekBarSrcCamOpticalCenterY).setVisibility(visibility);
        findViewById(R.id.linearLayoutDstCamImgSizePx).setVisibility(visibility);
        findViewById(R.id.editTextDstCamImgSizePxX).setVisibility(visibility);
        findViewById(R.id.editTextDstCamImgSizePxY).setVisibility(visibility);
    }

    private void showHelp() {
        // TODO
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent){
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == Activity.RESULT_OK && requestCode == LOADIMAGEFROMGALLERY_REQUESTCODE) {
            try {
                mSrcImageUri = intent.getData();
                Log.i(TAG, "mSrcImageUri: " + mSrcImageUri);
                mSrcImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), mSrcImageUri).copy(Bitmap.Config.ARGB_8888, false);
                Log.i(TAG, "mSrcImage size in onActivityResult(.)...: " + mSrcImage.getWidth() + "x" + mSrcImage.getHeight());
                if (mSrcImage.getWidth() <= DSTCAM_IMGSIZEX_MAX && mSrcImage.getHeight() <= DSTCAM_IMGSIZEY_MAX) {
                    //final float dpi = getResources().getDisplayMetrics().density;
                    final float dpi = 1.0f; // From gallery the bitmap size tells the actual image resolution, don't know why
                    ((EditText)findViewById(R.id.editTextDstCamImgSizePxX)).setText(Integer.toString((int) ((float) mSrcImage.getWidth() / dpi)));
                    ((EditText)findViewById(R.id.editTextDstCamImgSizePxY)).setText(Integer.toString((int) ((float) mSrcImage.getHeight() / dpi)));
                    Log.i(TAG, "mDstCamImgSizePx in onActivityResult(.): " + mDstCamImgSizePx[0] + "x" + mDstCamImgSizePx[1]);
                    updateImages();
                } else {
                    Toast.makeText(this, "Image must be max. " + DSTCAM_IMGSIZEX_MAX + " pixels wide and max. " + DSTCAM_IMGSIZEY_MAX + " high", Toast.LENGTH_LONG);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    int getSmallImageHeightPx() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return (int) (0.2f * (float) displayMetrics.heightPixels);
    }

    void updateImages() {
        int srcImageSmallHeight = getSmallImageHeightPx();
        mSrcCamView.setImageBitmap(Bitmap.createScaledBitmap(mSrcImage, mSrcImage.getWidth() * srcImageSmallHeight / mSrcImage.getHeight(), srcImageSmallHeight, false));
        mDstCamRenderer.setSrcImage(mSrcImage);
        mDstCamRenderer.setSrcCamHfovDeg(mSrcCamHfovDeg);
        mDstCamRenderer.setSrcCamFocalLength(mSrcCamFocalLength);
        mDstCamRenderer.setDstCamHfovDeg(mDstCamHfovDeg);
        mDstCamRenderer.setDstCamImgSizePx(mDstCamImgSizePx);
        //mDstCamView.requestRender();
        //Bitmap dstImage = fisheyeImageToPinholeImage(mSrcImage, mSrcCamHfovDeg, mSrcCamFocalLength, mDstCamHfovDeg);
        //ImageView imageViewDstCam = findViewById(R.id.imageTransformerViewDstCam);
    }

    /*static Bitmap fisheyeImageToPinholeImage(final Bitmap srcImage, double srcCamHfovDeg, double srcCamFocalLength, double dstCamHfovDeg) {
        double[] principalPointPx = {srcImage.getWidth() / 2, srcImage.getHeight() / 2};
        //double srcFocalLengthPx = (double) (srcImage.getWidth() / 2) / Math.sin(Math.toRadians(srcCamHfovDeg) / 2.0);
        double dstFocalLengthPx = (double) (srcImage.getWidth() / 2) / Math.tan(Math.toRadians(dstCamHfovDeg / 2.0));
        Bitmap dstImage = Bitmap.createBitmap(srcImage.getWidth(), srcImage.getHeight(), srcImage.getConfig());
        int[] srcPixels = new int[srcImage.getWidth() * srcImage.getHeight()];
        int[] dstPixels = new int[srcImage.getWidth() * srcImage.getHeight()];
        srcImage.getPixels(srcPixels, 0, srcImage.getWidth(), 0, 0, srcImage.getWidth(), srcImage.getHeight());
        for (int i = 0; i < srcImage.getWidth() * srcImage.getHeight(); ++i) {
            //pixels[i] ^= 0x00FFFFFF;
            double dstImgSpaceX = i % srcImage.getWidth();
            double dstImgSpaceY = i / srcImage.getWidth();
            double dstImgSpaceR = Math.hypot(dstImgSpaceX -  principalPointPx[0], dstImgSpaceY - principalPointPx[1]);
            double viewSpaceLength = Math.hypot(dstImgSpaceR, dstFocalLengthPx);
            double viewSpaceX = (dstImgSpaceX - principalPointPx[0])/ viewSpaceLength;
            double viewSpaceY = (dstImgSpaceY - principalPointPx[1])/ viewSpaceLength;
            double viewSpaceZ = dstFocalLengthPx / viewSpaceLength;
            double srcViewPointOffset = Math.cos(Math.toRadians(srcCamHfovDeg / 2.0));
            double srcViewSpaceZ = viewSpaceZ + (srcCamFocalLength - srcViewPointOffset);
            double srcImagePlaneWHalf = Math.sin(Math.toRadians(srcCamHfovDeg / 2.0));
            double srcImgSpaceX = ((srcCamFocalLength * viewSpaceX / srcViewSpaceZ)) * ((double) (srcImage.getWidth() / 2) / srcImagePlaneWHalf) + principalPointPx[0];
            double srcImgSpaceY = ((srcCamFocalLength * viewSpaceY / srcViewSpaceZ)) * ((double) (srcImage.getWidth() / 2) / srcImagePlaneWHalf) + principalPointPx[1];
            //double srcImgSpaceX = srcFocalLengthPx * viewSpaceX / viewSpaceZ + principalPointPx[0];
            //double srcImgSpaceY = srcFocalLengthPx * viewSpaceY / viewSpaceZ + principalPointPx[1];
            if (srcImgSpaceX >= 0.0 && srcImgSpaceX < srcImage.getWidth() && srcImgSpaceY >= 0.0 && srcImgSpaceY < srcImage.getHeight()) {
                int srcPixelId = (int) srcImgSpaceX + srcImage.getWidth() * (int) srcImgSpaceY;
                //if (srcPixelId < 0 || srcPixelId >= (srcImage.getWidth() * srcImage.getHeight())) {
                //    throw new AssertionError("Invalid src pixel ID: " + srcPixelId + " (x, y: " + srcImgSpaceX + ", " + srcImgSpaceY);
                //}
                dstPixels[i] = srcPixels[srcPixelId];
            }
        }
        dstImage.setPixels(dstPixels, 0, srcImage.getWidth(), 0, 0, srcImage.getWidth(), srcImage.getHeight());
        return dstImage;
    }*/

    static float seekBarScaleToValueScale(final SeekBar seekBar, int progress, float valueMax) {
        return ((float)progress / (float) seekBar.getMax()) * valueMax;
    }

    static int valueScaleToSeekBarScale(final SeekBar seekBar, float value, float valueMax) {
        //Log.i(TAG, "  value, limits, normdValue; seekBar max: " + value + ", " + valueLimits[0] + "->" + valueLimits[1] + ", " + normdValue + "; " + seekBar.getMax());
        return (int) ((value / valueMax) * (float) seekBar.getMax());
    }

    private static String getDateTimeStr() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATETIME_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date now = Calendar.getInstance().getTime();
        return dateFormat.format(now);
    }
}

class ImageTransformerView extends GLSurfaceView {
    public ImageTransformerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
    }
}

class ImageTransformerRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "ImageTransformer";

    private static final int POS_DATA_SIZE = 3;
    private static final int SIZEOF_FLOAT = 4;

    private static final String U_MVMAT = "u_MVMat";
    private static final String U_MVPMAT = "u_MVPMat";
    private static final String A_POS = "a_Pos";
    private static final String A_TEXVEC = "a_TexVec";
    private static final String U_TEX = "u_Tex";
    private static final String U_SRCCAM_HFOV_DEG = "u_srccam_hfov_deg";
    private static final String U_SRCCAM_FOCALLENGTH = "u_srccam_focallength";
    private static final String U_SRCCAM_OPTICALCENTER = "u_srccam_opticalcenter";
    private static final String U_DSTCAM_HFOV_DEG = "u_dstcam_hfov_deg";
    private static final String U_DSTCAM_IMGSIZE_PX = "u_dstcam_imgsize_px";
    private static final String U_SRCCAM_IMGSIZE_PX = "u_srccam_imgsize_px";

    private static final float IMAGEFACEPOS_TIMECONSTANT = 10.0f;

    private final Context mActivityContext;
    private final int[] mScreenSize;
    private Bitmap mSrcImage;
    private int[] mSrcCamImgSizePx;
    private float mSrcCamHfovDeg;
    private float mSrcCamFocalLength;
    private float[] mSrcCamOpticalCenter;
    private float mDstCamHfovDeg;
    private int[] mDstCamImgSizePx;
    private float mTargetImageFacePosY;
    //private float mCurrentImageFacePosY;
    private float[] mModelMatrix = new float[16];
    private float[] mViewMatrix = new float[16];
    private float[] mMVMatrix = new float[16];
    private float[] mProjectionMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];

    private final FloatBuffer mImageFacePosBuf;
    private final FloatBuffer mImageFaceTexVecsBuf;

    private int[] mTextureHandle;
    private int mTexUnifHandle;
    private int mMVMatrixHandle;
    private int mMVPMatrixHandle;
    private int mSrcCamHfovDegHandle;
    private int mSrcCamFocalLengthHandle;
    private int mSrcCamOpticalCenterHandle;
    private int mDstCamHfovDegHandle;
    private int mDstCamImgSizePxHandle;
    private int mSrcCamImgSizePxHandle;
    private int mPosAttribHandle;
    private int mTexVecAttribHandle;

    private final float CANVAS_SIDE_HALF = 0.5f;
    private final float[] IMAGEFACE_POS_DATA = {
            -CANVAS_SIDE_HALF, CANVAS_SIDE_HALF, 0.0f,
            -CANVAS_SIDE_HALF, -CANVAS_SIDE_HALF, 0.0f,
            CANVAS_SIDE_HALF, CANVAS_SIDE_HALF, 0.0f,
            -CANVAS_SIDE_HALF, -CANVAS_SIDE_HALF, 0.0f,
            CANVAS_SIDE_HALF, -CANVAS_SIDE_HALF, 0.0f,
            CANVAS_SIDE_HALF, CANVAS_SIDE_HALF, 0.0f
    };

    private final float[] IMAGEFACE_TEXVEC_DATA = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    ImageTransformerRenderer(final Context activityContext, int[] screenSize, final Bitmap srcImage, float srcCamHfovDeg, float srcCamFocalLength, float[] srcCamOpticalCenter, float dstCamHfovDeg, int[] dstCamImgSizePx) {
        if (screenSize.length != 2) {
            throw new IllegalArgumentException("Screen size must be 2-dimensional");
        }
        if (srcCamOpticalCenter.length != 2) {
            throw new IllegalArgumentException("Optical center must be 2-dimensional");
        }
        if (dstCamImgSizePx.length != 2) {
            throw new IllegalArgumentException("Transformed image size must be 2-dimensional");
        }

        mActivityContext = activityContext;
        mScreenSize = screenSize;

        mSrcImage = srcImage;
        final float dpi = mActivityContext.getResources().getDisplayMetrics().density;
        mSrcCamImgSizePx = new int[]{(int) ((float) mSrcImage.getWidth() / dpi), (int) ((float) mSrcImage.getHeight() / dpi)};
        Log.i(TAG, "mSrcCamImgSizePx: " + mSrcCamImgSizePx[0] + ", " + mSrcCamImgSizePx[1]);
        mSrcCamHfovDeg = srcCamHfovDeg;
        mSrcCamFocalLength = srcCamFocalLength;
        mSrcCamOpticalCenter = srcCamOpticalCenter;
        mDstCamHfovDeg = dstCamHfovDeg;
        mDstCamImgSizePx = dstCamImgSizePx;
        mTargetImageFacePosY = 0.0f;
        //mCurrentImageFacePosY = 0.0f;

        mImageFacePosBuf = ByteBuffer.allocateDirect(IMAGEFACE_POS_DATA.length * SIZEOF_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mImageFacePosBuf.put(IMAGEFACE_POS_DATA).position(0);

        mImageFaceTexVecsBuf = ByteBuffer.allocateDirect(IMAGEFACE_TEXVEC_DATA.length * SIZEOF_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mImageFaceTexVecsBuf.put(IMAGEFACE_TEXVEC_DATA).position(0);
    }

    void setSrcImage(final Bitmap srcImage) {
        mSrcImage = srcImage;
        final float dpi = mActivityContext.getResources().getDisplayMetrics().density;
        mSrcCamImgSizePx[0] = (int) ((float) mSrcImage.getWidth() / dpi);
        mSrcCamImgSizePx[1] = (int) ((float) mSrcImage.getHeight() / dpi);
        //mDstCamImgSizePx = mSrcCamImgSizePx;
        //Log.i(TAG,"mDstCamImgSizePx in setSrcImage(.): " + mDstCamImgSizePx[0] + ", " + mDstCamImgSizePx[1]);
    }

    void setSrcCamHfovDeg(float srcCamHfovDeg) {
        mSrcCamHfovDeg = srcCamHfovDeg;
    }

    void setSrcCamFocalLength(float srcCamFocalLength) {
        mSrcCamFocalLength = srcCamFocalLength;
    }

    void setDstCamHfovDeg(float dstCamHfovDeg) {
        mDstCamHfovDeg = dstCamHfovDeg;
    }

    void setDstCamImgSizePx(int[] dstCamImgSizePx) {
        if (dstCamImgSizePx.length != 2) {
            throw new IllegalArgumentException("Transformed image size must be 2-dimensional");
        }
        mDstCamImgSizePx = dstCamImgSizePx;
    }

    void setTargetImageFacePosY(float targetImageFacePosY) {
        mTargetImageFacePosY = targetImageFacePosY;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = 1.0f;
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -1.0f;
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;
        Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

        final String vertexShaderSourceCode = loadShaderSourceCode(mActivityContext, R.raw.imageface_vertexshader);
        final String fragmentShaderSourceCode = loadShaderSourceCode(mActivityContext, R.raw.linearize_fragmentshader);
        /*final String vertexShaderSourceCode =
                "uniform mat4 u_MVPMat;      \n"
                        + "attribute vec4 a_Pos;          \n"
                        + "attribute vec2 a_TexVec;       \n"
                        + "varying vec2 v_TexVec;         \n"
                        + "                               \n"
                        + "void main() {                  \n"
                        + "   v_TexVec = a_TexVec;        \n"
                        + "   gl_Position = u_MVPMat * a_Pos;        \n"
                        + "}                              \n";

        final String fragmentShaderSourceCode =
                "precision mediump float;       \n"
                        + "uniform sampler2D u_Tex;       \n"
                        + "                               \n"
                        + "void main() {                  \n"
                        + "   gl_FragColor = vec4(1.0, 1.0, 0.0, 1.0);     \n"
                        + "}                              \n";*/
        final int vertexShaderHandle = compileShader(vertexShaderSourceCode, GLES20.GL_VERTEX_SHADER);
        final int fragmentShaderHandle = compileShader(fragmentShaderSourceCode, GLES20.GL_FRAGMENT_SHADER);
        final int programHandle = createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle, new String[]{A_POS, A_TEXVEC});
        //final int programHandle = createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle, new String[]{"u_MVPMatrix", "a_Pos"});

        mMVMatrixHandle = GLES20.glGetUniformLocation(programHandle, U_MVMAT);
        mMVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, U_MVPMAT);
        mPosAttribHandle = GLES20.glGetAttribLocation(programHandle, A_POS);
        mTexVecAttribHandle = GLES20.glGetAttribLocation(programHandle, A_TEXVEC);

        mTextureHandle = new int[1];
        GLES20.glGenTextures(1, mTextureHandle, 0);
        if (mTextureHandle[0] > 0) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureHandle[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mSrcImage, 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureHandle[0]);
            mTexUnifHandle = GLES20.glGetUniformLocation(programHandle, U_TEX);
            GLES20.glUniform1i(mTexUnifHandle, 0);
        } else {
            throw new RuntimeException("Failed to load image to texture");
        }

        mSrcCamHfovDegHandle = GLES20.glGetUniformLocation(programHandle, U_SRCCAM_HFOV_DEG);
        mSrcCamFocalLengthHandle = GLES20.glGetUniformLocation(programHandle, U_SRCCAM_FOCALLENGTH);
        mSrcCamOpticalCenterHandle = GLES20.glGetUniformLocation(programHandle, U_SRCCAM_OPTICALCENTER);
        mDstCamHfovDegHandle = GLES20.glGetUniformLocation(programHandle, U_DSTCAM_HFOV_DEG);
        mDstCamImgSizePxHandle = GLES20.glGetUniformLocation(programHandle, U_DSTCAM_IMGSIZE_PX);
        mSrcCamImgSizePxHandle = GLES20.glGetUniformLocation(programHandle, U_SRCCAM_IMGSIZE_PX);

        GLES20.glUseProgram(programHandle);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Log.i(TAG, "Surface w, h: " + width + ", " + height);
        //mRequestedViewPortY = (int) (0.35f * (float) height);
        //mRequestedViewPortHeight = (int) ((float) width * (float) mDstCamImgSizePx[1] / (float) mDstCamImgSizePx[0]);
        //Log.i(TAG, "mRequestedViewPortHeight: " + mRequestedViewPortHeight);
        //GLES20.glViewport(0, mRequestedViewPortY, width, mRequestedViewPortHeight);
        //GLES20.glViewport(0, (height - viewPortHeight) / 2, width, viewPortHeight);
        GLES20.glViewport(0, 0, width, height);

        final float left = -CANVAS_SIDE_HALF;
        final float right = -left;
        //final float ratio = (float) width / height;
        //final float left = -ratio;
        //final float right = ratio;
        final float top = ((float) height / (float) width) * CANVAS_SIDE_HALF;
        final float bottom = -top;
        //final float top = 1.0f;
        //final float bottom = -1.0f;
        final float near = 1.0f;
        final float far = 10.0f;
        Matrix.orthoM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
        //Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        drawImageFace();
    }

    private void drawImageFace() {
        mImageFacePosBuf.position(0);
        GLES20.glVertexAttribPointer(mPosAttribHandle, POS_DATA_SIZE, GLES20.GL_FLOAT, false,0, mImageFacePosBuf);
        GLES20.glEnableVertexAttribArray(mPosAttribHandle);

        mImageFaceTexVecsBuf.position(0);
        GLES20.glVertexAttribPointer(mTexVecAttribHandle, 2, GLES20.GL_FLOAT, false, 0, mImageFaceTexVecsBuf);
        GLES20.glEnableVertexAttribArray(mTexVecAttribHandle);

        //mCurrentImageFacePosY += (mTargetImageFacePosY - mCurrentImageFacePosY) / IMAGEFACEPOS_TIMECONSTANT;
        Matrix.setIdentityM(mModelMatrix, 0);
        final float imageFaceScaleX;
        final float imageFaceScaleY;
        if (mDstCamImgSizePx[1] <= mDstCamImgSizePx[0]) {
            imageFaceScaleY = (float) mDstCamImgSizePx[1] / (float) mDstCamImgSizePx[0];
            imageFaceScaleX = 1.0f;
        } else {
            imageFaceScaleY = 1.0f;
            imageFaceScaleX = (float) mDstCamImgSizePx[0] / (float) mDstCamImgSizePx[1];
        }
        Matrix.scaleM(mModelMatrix, 0, imageFaceScaleX, imageFaceScaleY, 1.0f);
        //Matrix.translateM(mModelMatrix, 0, 0.0f, mCurrentImageFacePosY, 0.0f);
        Matrix.multiplyMM(mMVMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVMatrix, 0);
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVMatrix, 0);
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        GLES20.glUniform1f(mSrcCamHfovDegHandle, mSrcCamHfovDeg);
        GLES20.glUniform1f(mSrcCamFocalLengthHandle, mSrcCamFocalLength);
        GLES20.glUniform2f(mSrcCamOpticalCenterHandle, mSrcCamOpticalCenter[0], mSrcCamOpticalCenter[1]);
        GLES20.glUniform1f(mDstCamHfovDegHandle, mDstCamHfovDeg);
        GLES20.glUniform2f(mDstCamImgSizePxHandle, (float) mDstCamImgSizePx[0], (float) mDstCamImgSizePx[1]);
        GLES20.glUniform2f(mSrcCamImgSizePxHandle, (float) mSrcCamImgSizePx[0], (float) mSrcCamImgSizePx[1]);
        //Log.i(TAG, "Src, Dst img size px: " + mSrcCamImgSizePx[0] + ", " + mSrcCamImgSizePx[1] + "; " + mDstCamImgSizePx[0] + ", " + mDstCamImgSizePx[1]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, IMAGEFACE_POS_DATA.length / POS_DATA_SIZE);
    }

    private static int compileShader(final String shaderSourceCode, final int shaderType) {
        int shaderHandle = GLES20.glCreateShader(shaderType);
        if (shaderHandle > 0) {
            GLES20.glShaderSource(shaderHandle, shaderSourceCode);
            GLES20.glCompileShader(shaderHandle);
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Failed to compile shader: " + GLES20.glGetShaderInfoLog(shaderHandle));
                GLES20.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        } else {
            throw new RuntimeException("Failed to create shader");
        }
        return shaderHandle;
    }

    private static int createAndLinkProgram(final int vertexShaderHandle, final int fragmentShaderHandle, final String[] attributes) {
        int programHandle = GLES20.glCreateProgram();
        if (programHandle > 0) {
            GLES20.glAttachShader(programHandle, vertexShaderHandle);
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);
            for (int i = 0; i < attributes.length; ++i) {
                GLES20.glBindAttribLocation(programHandle, i, attributes[i]);
            }
            GLES20.glLinkProgram(programHandle);
            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Failed to link program: " + GLES20.glGetProgramInfoLog(programHandle));
                GLES20.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        } else {
            throw new RuntimeException("Failed to create program");
        }
        return programHandle;
    }

    private static String loadShaderSourceCode(final Context context, final int resourceId) {
        final InputStream inputStream = context.getResources().openRawResource(resourceId);
        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line;
        final StringBuilder shaderSourceCode = new StringBuilder();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                shaderSourceCode.append(line);
                shaderSourceCode.append('\n');
            }
        } catch (IOException ioe) {
            return "";
        }
        return shaderSourceCode.toString();
    }
}