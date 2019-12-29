#pragma version(1)
#pragma rs java_package_name(com.krisstof.imagelinearizer)

rs_allocation srcImage;
int srcImageWidthPx;
int srcImageHeightPx;
float srcCamHfovDeg;
float srcCamFocalOffset;
float srcCamPrincipalPointXPx;
float srcCamPrincipalPointYPx;
float dstCamHfovDeg;
int dstImageWidthPx;
int dstImageHeightPx;

static float2 pinholeToFisheye(float2 dstImagePoint) {
  float3 viewSpaceVec;
  viewSpaceVec.x = dstImagePoint.x - 0.5f * (float) dstImageWidthPx;
  viewSpaceVec.y = dstImagePoint.y - 0.5f * (float) dstImageHeightPx;
  viewSpaceVec.z = 0.5f * (float) dstImageWidthPx / tan(radians(dstCamHfovDeg / 2.f));
  viewSpaceVec = normalize(viewSpaceVec);

  const float srcCamViewPointOffset = cos(radians(srcCamHfovDeg / 2.f));
  const float srcCamViewSpaceVecZ = viewSpaceVec.z + (srcCamFocalOffset - srcCamViewPointOffset);
  const float srcCamImagePlaneHalfWidth = sin(radians(srcCamHfovDeg / 2.f));

  float2 srcImagePoint;
  srcImagePoint.x = ((0.5f * (float) srcImageWidthPx)
          * (srcCamFocalOffset * viewSpaceVec.x / srcCamViewSpaceVecZ)
          / srcCamImagePlaneHalfWidth)
      + srcCamPrincipalPointXPx;
  srcImagePoint.y = ((0.5f * (float) srcImageHeightPx
              / ((float) srcImageHeightPx / (float) srcImageWidthPx))
          * (srcCamFocalOffset * viewSpaceVec.y / srcCamViewSpaceVecZ)
          / srcCamImagePlaneHalfWidth)
      + srcCamPrincipalPointYPx;
  //srcImagePoint.x = -1.f;
  //srcImagePoint.y = -1.f;
  return srcImagePoint;
  //return dstImagePoint;
}

uchar4 __attribute__((kernel)) linearize(uint32_t x, uint32_t y) {
  float2 dstImagePoint;
  dstImagePoint.x = (float) x + 0.5f;
  dstImagePoint.y = (float) y + 0.5f;
  const float2 srcImagePoint = pinholeToFisheye(dstImagePoint);
  int2 srcImagePointPx;
  srcImagePointPx.x = round(srcImagePoint.x);
  srcImagePointPx.y = round(srcImagePoint.y);
  //srcImagePointPx.x = -1;
  //srcImagePointPx.y = -1;
  uchar4 outputColor;
  if (srcImagePointPx.x >= 0 && srcImagePointPx.x < srcImageWidthPx
      && srcImagePointPx.y >= 0 && srcImagePointPx.y < srcImageHeightPx) {
    outputColor = rsGetElementAt_uchar4(srcImage, srcImagePointPx.x, srcImagePointPx.y);
  } else {
    outputColor.a = 255;
    outputColor.r = 0;
    outputColor.g = 0;
    outputColor.b = 0;
  }
  //uchar4 outputColor = inputColor;
  //outputColor.a = 255;
  //outputColor.r = 255;
  //outputColor.g = 255;
  //outputColor.b = 0;
  return outputColor;
}