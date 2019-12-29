package com.krisstof.imagelinearizer;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;

final class ImageTransformer {
  private static final String TAG = ImageTransformer.class.getSimpleName();

  private RenderScript mRenderScript;
  private ScriptC_linearize mLinearizeScript;

  ImageTransformer(Context context) {
    mRenderScript = RenderScript.create(context);
    mLinearizeScript = new ScriptC_linearize(mRenderScript);
  }

  Bitmap linearize(final Bitmap srcImage, FisheyeParameters srcCamParameters, int dstImageWidthPx,
                   int dstImageHeightPx, float dstCamHfovDeg) {
    Log.d(TAG, "Linearization parameters:");
    Log.d(TAG, "  srcImageWidthPx, HeightPx: " + srcImage.getWidth() + ", "
        + srcImage.getHeight());
    Log.d(TAG, "  srcCamHfovDeg, FocalOffset: " + srcCamParameters.hfovDeg + ", "
        + srcCamParameters.focalOffset);
    Log.d(TAG, "  srcCamPrincipalPointXPx, YPx: " + srcCamParameters.principalPointXPx + ", "
        + srcCamParameters.principalPointYPx);
    Log.d(TAG, "  dstImageWidthPx, HeightPx: " + dstImageWidthPx + ", " + dstImageHeightPx);
    Log.d(TAG, "  dstCamHfovDeg: " + dstCamHfovDeg);
    Allocation srcImageAllocation = Allocation.createFromBitmap(mRenderScript, srcImage);
    //Log.d(TAG, "srcImageAllocation pixel count: " + srcImageAllocation.getBytesSize() / 4);
    mLinearizeScript.set_srcImage(srcImageAllocation);
    mLinearizeScript.set_srcImageWidthPx(srcImage.getWidth());
    mLinearizeScript.set_srcImageHeightPx(srcImage.getHeight());
    mLinearizeScript.set_srcCamHfovDeg(srcCamParameters.hfovDeg);
    mLinearizeScript.set_srcCamFocalOffset(srcCamParameters.focalOffset);
    mLinearizeScript.set_srcCamPrincipalPointXPx(srcCamParameters.principalPointXPx);
    mLinearizeScript.set_srcCamPrincipalPointYPx(srcCamParameters.principalPointYPx);
    mLinearizeScript.set_dstImageWidthPx(dstImageWidthPx);
    mLinearizeScript.set_dstImageHeightPx(dstImageHeightPx);
    mLinearizeScript.set_dstCamHfovDeg(dstCamHfovDeg);
    Type.Builder typeBuilder = new Type.Builder(mRenderScript, Element.RGBA_8888(mRenderScript));
    typeBuilder.setX(dstImageWidthPx);
    typeBuilder.setY(dstImageHeightPx);
    final Type dstAllocationType = typeBuilder.create();
    Allocation dstImageAllocation = Allocation.createTyped(mRenderScript, dstAllocationType);
    mLinearizeScript.forEach_linearize(dstImageAllocation);
    Bitmap dstImage = Bitmap.createBitmap(dstImageWidthPx, dstImageHeightPx, Bitmap.Config.ARGB_8888);
    dstImageAllocation.copyTo(dstImage);
    //Log.d(TAG, "dstImage density: " + dstImage.getDensity());
    return dstImage;
  }
}
