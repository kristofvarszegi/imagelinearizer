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

  Bitmap linearize(final Bitmap srcImage, final FisheyeParameters srcCamParameters,
                   final DstCamParameters dstCamParameters) {
    Log.d(TAG, "Linearization parameters:");
    Log.d(TAG, "  src imageWidthPx, HeightPx: " + srcImage.getWidth() + ", "
        + srcImage.getHeight());
    Log.d(TAG, "  src hfovDeg, FocalOffset: " + srcCamParameters.hfovDeg + ", "
        + srcCamParameters.focalOffset);
    Log.d(TAG, "  src principalPointXPx, YPx: " + srcCamParameters.principalPointXPx + ", "
        + srcCamParameters.principalPointYPx);
    Log.d(TAG, "  dst geometryId: " + dstCamParameters.getGeometryId());
    Log.d(TAG, "  dst hfovDeg: " + dstCamParameters.hfovDeg);
    Log.d(TAG, "  dst imageWidthPx, HeightPx: " + dstCamParameters.imageWidthPx + ", "
        + dstCamParameters.imageHeightPx);
    Log.d(TAG, "  dst xRotDeg, yRotDeg, zRotDeg: " + dstCamParameters.xRotDeg + ", "
        + dstCamParameters.yRotDeg + ", " + dstCamParameters.zRotDeg);
    Allocation srcImageAllocation = Allocation.createFromBitmap(mRenderScript, srcImage);
    //Log.d(TAG, "srcImageAllocation pixel count: " + srcImageAllocation.getBytesSize() / 4);
    mLinearizeScript.set_srcImage(srcImageAllocation);
    mLinearizeScript.set_srcImageWidthPx(srcImage.getWidth());
    mLinearizeScript.set_srcImageHeightPx(srcImage.getHeight());
    mLinearizeScript.set_srcCamHfovDeg(srcCamParameters.hfovDeg);
    mLinearizeScript.set_srcCamFocalOffset(srcCamParameters.focalOffset);
    mLinearizeScript.set_srcCamPrincipalPointXPx(srcCamParameters.principalPointXPx);
    mLinearizeScript.set_srcCamPrincipalPointYPx(srcCamParameters.principalPointYPx);
    mLinearizeScript.set_dstCamGeometryId(dstCamParameters.getGeometryId());
    mLinearizeScript.set_dstCamHfovDeg(dstCamParameters.hfovDeg);
    mLinearizeScript.set_dstImageWidthPx(dstCamParameters.imageWidthPx);
    mLinearizeScript.set_dstImageHeightPx(dstCamParameters.imageHeightPx);
    mLinearizeScript.set_dstCamXRotDeg(dstCamParameters.xRotDeg);
    mLinearizeScript.set_dstCamYRotDeg(dstCamParameters.yRotDeg);
    mLinearizeScript.set_dstCamZRotDeg(dstCamParameters.zRotDeg);
    Type.Builder typeBuilder = new Type.Builder(mRenderScript, Element.RGBA_8888(mRenderScript));
    typeBuilder.setX(dstCamParameters.imageWidthPx);
    typeBuilder.setY(dstCamParameters.imageHeightPx);
    final Type dstAllocationType = typeBuilder.create();
    Allocation dstImageAllocation = Allocation.createTyped(mRenderScript, dstAllocationType);
    mLinearizeScript.forEach_linearize(dstImageAllocation);
    Bitmap dstImage = Bitmap.createBitmap(dstCamParameters.imageWidthPx,
        dstCamParameters.imageHeightPx, Bitmap.Config.ARGB_8888);
    dstImageAllocation.copyTo(dstImage);
    //Log.d(TAG, "dstImage density: " + dstImage.getDensity());
    return dstImage;
  }
}
