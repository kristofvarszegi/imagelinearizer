#pragma version(1)
#pragma rs java_package_name(com.krisstof.imagelinearizer)

rs_allocation srcImage;
int srcImageWidthPx;
int srcImageHeightPx;
float srcCamHfovDeg;
float srcCamFocalOffset;
float srcCamPrincipalPointXPx;
float srcCamPrincipalPointYPx;
int dstCamGeometryId;
float dstCamHfovDeg;
int dstImageWidthPx;
int dstImageHeightPx;
float dstCamRollDeg;
float dstCamPitchDeg;
float dstCamYawDeg;

static float3 pinholeImageSpaceToCameraSpace(float2 dstImagePoint) {
  float3 camSpaceVec;
  camSpaceVec.x = dstImagePoint.x - 0.5f * (float) dstImageWidthPx;
  camSpaceVec.y = dstImagePoint.y - 0.5f * (float) dstImageHeightPx;
  camSpaceVec.z = 0.5f * (float) dstImageWidthPx / tan(radians(dstCamHfovDeg / 2.f));
  return normalize(camSpaceVec);
}

static float3 equirectangularImageSpaceToCameraSpace(float2 dstImagePoint) {
  float3 camSpaceVec;
  const float horizontalAngleRad = radians(dstCamHfovDeg / 2.f)
      * (dstImagePoint.x - 0.5f * (float) dstImageWidthPx) / (float) (dstImageWidthPx / 2);
  const float dstCamVfovDeg = dstCamHfovDeg * (float) dstImageHeightPx / (float) dstImageWidthPx;
  const float verticalAngleRad = radians(dstCamVfovDeg / 2.f)
      * (dstImagePoint.y - 0.5f * (float) dstImageHeightPx) / (float) (dstImageHeightPx / 2);
  camSpaceVec.x = cos(verticalAngleRad) * sin(horizontalAngleRad);
  camSpaceVec.y = sin(verticalAngleRad);
  camSpaceVec.z = cos(verticalAngleRad) * cos(horizontalAngleRad);
  //camSpaceVec.z = sqrt(1.f - (camSpaceVec.x * camSpaceVec.x + camSpaceVec.y * camSpaceVec.y));
  return normalize(camSpaceVec);
}

static float3 rotateAroundX(float3 vec, float xRotRad) {
  float3 rotatedVec;
  rotatedVec.x = vec.x;
  rotatedVec.y = cos(xRotRad) * vec.y - sin(xRotRad) * vec.z;
  rotatedVec.z = sin(xRotRad) * vec.y + cos(xRotRad) * vec.z;
  return rotatedVec;
}

static float3 rotateAroundY(float3 vec, float yRotRad) {
  float3 rotatedVec;
  rotatedVec.x = cos(yRotRad) * vec.x - sin(yRotRad) * vec.z;
  rotatedVec.y = vec.y;
  rotatedVec.z = sin(yRotRad) * vec.x + cos(yRotRad) * vec.z;
  return rotatedVec;
}

static float3 rotateAroundZ(float3 vec, float zRotRad) {
  float3 rotatedVec;
  rotatedVec.x = cos(zRotRad) * vec.x - sin(zRotRad) * vec.y;
  rotatedVec.y = sin(zRotRad) * vec.x + cos(zRotRad) * vec.y;
  rotatedVec.z = vec.z;
  return rotatedVec;
}

static void loadIdentityMat3x3(float* outMat) {
  outMat[0] = 1.f;
  outMat[1] = 0.f;
  outMat[2] = 0.f;
  outMat[3] = 0.f;
  outMat[4] = 1.f;
  outMat[5] = 0.f;
  outMat[6] = 0.f;
  outMat[7] = 0.f;
  outMat[8] = 1.f;
}

static void loadZeroMat3x3(float* outMat) {
  outMat[0] = 0.f;
  outMat[1] = 0.f;
  outMat[2] = 0.f;
  outMat[3] = 0.f;
  outMat[4] = 0.f;
  outMat[5] = 0.f;
  outMat[6] = 0.f;
  outMat[7] = 0.f;
  outMat[8] = 0.f;
}

static void loadXRotMat3x3ColMajor(float xRotRad, float* outMat) {
  loadIdentityMat3x3(outMat);
  outMat[4] = cos(xRotRad);
  outMat[5] = -sin(xRotRad);
  outMat[7] = sin(xRotRad);
  outMat[8] = cos(xRotRad);
}

static void loadYRotMat3x3ColMajor(float yRotRad, float* outMat) {
  loadIdentityMat3x3(outMat);
  outMat[0] = cos(yRotRad);
  outMat[2] = sin(yRotRad);
  outMat[6] = -sin(yRotRad);
  outMat[8] = cos(yRotRad);
}

static void loadZRotMat3x3ColMajor(float zRotRad, float* outMat) {
  loadIdentityMat3x3(outMat);
  outMat[0] = cos(zRotRad);
  outMat[1] = -sin(zRotRad);
  outMat[3] = sin(zRotRad);
  outMat[4] = cos(zRotRad);
}

static void multiplyMatMat3x3ColMajor(const float* logicalLhsMat, const float* logicalRhsMat,
    float* outMat) {
  loadZeroMat3x3(outMat);
  //loadIdentityMat3x3(outMat);
  ////float identityMat[9];
  ////loadIdentityMat3x3(identityMat);
  for (int r = 0; r < 3; ++r) {
    for (int c = 0; c < 3; ++c) {
      for (int i = 0; i < 3; ++i) {
        outMat[r + c * 3] += logicalLhsMat[i + c * 3] * logicalRhsMat[r + i * 3];
        //outMat[c + r * 3] += logicalLhsMat[i + c * 3] * identityMat[r + i * 3];
        //outMat[c + r * 3] += identityMat[c + i * 3] * identityMat[i + r * 3];
      }
    }
  }
}

//static void loadRotMat3x3ColMajor(float xRotRad, float yRotRad, float zRotRad,
//    float* outMat) {
  //loadIdentityMat3x3(outMat);
  //float xRotMat[9], yRotMat[9], zRotMat[9];
  //loadXRotMat3x3ColMajor(xRotRad, xRotMat);
  //loadYRotMat3x3ColMajor(yRotRad, yRotMat);
  //loadZRotMat3x3ColMajor(zRotRad, zRotMat);
  //float xYRotMat[9];
  //loadIdentityMat3x3(xYRotMat);
  ////multiplyMatMat3x3ColMajor(xRotMat, yRotMat, outMat);
  //multiplyMatMat3x3ColMajor(xRotMat, yRotMat, xYRotMat);
  //multiplyMatMat3x3ColMajor(zRotMat, xYRotMat, outMat);
//}

static void loadAxisAngleRotMat3x3ColMajor(float3 axis, float angleRad, float* outMat) {
  const float c = cos(angleRad);
  const float s = sin(angleRad);
  const float t = 1.0 - c;

  outMat[0] = c + axis.x * axis.x * t;
  outMat[4] = c + axis.y * axis.y * t;
  outMat[8] = c + axis.z * axis.z * t;

  float tmp1 = axis.x * axis.y * t;
  float tmp2 = axis.z * s;
  outMat[1] = tmp1 + tmp2;
  outMat[3] = tmp1 - tmp2;

  tmp1 = axis.x * axis.z * t;
  tmp2 = axis.y * s;
  outMat[2] = tmp1 - tmp2;
  outMat[6] = tmp1 + tmp2;

  tmp1 = axis.y * axis.z * t;
  tmp2 = axis.x * s;
  outMat[5] = tmp1 + tmp2;
  outMat[7] = tmp1 - tmp2;
}

static void transposeMat3x3(float* inMat, float* outMat) {
  float tmpMat[9];
  for (int r = 0; r < 3; ++r) {
    for (int c = 0; c < 3; ++c) {
      tmpMat[r + c * 3] = inMat[c + r * 3];
    }
  }
  outMat = tmpMat;
}

static float3 multiplyMatVec3x3ColMajor(const float* inMat, float3 inVec) {
  float3 outVec;
  outVec.x = inMat[0] * inVec.x + inMat[3] * inVec.y + inMat[6] * inVec.z;
  outVec.y = inMat[1] * inVec.x + inMat[4] * inVec.y + inMat[7] * inVec.z;
  outVec.z = inMat[2] * inVec.x + inMat[5] * inVec.y + inMat[8] * inVec.z;
  return outVec;
}

//static void changeTfToBasis(const float* inMat, const float* basisMat, float* outMat) {
//  float tmpMat[9];
//  multiplyMatMat3x3ColMajor(basisMat, inMat, tmpMat);
//  float invBasisMat[9];
//  transposeMat3x3(basisMat, invBasisMat);
//  multiplyMatMat3x3ColMajor(tmpMat, invBasisMat, tmpMat);
//  outMat = tmpMat;
//}

static float3 rotateAroundAxis(float3 vec, float3 axis, float angleRad) {
  float rotMat[9];
  loadAxisAngleRotMat3x3ColMajor(axis, angleRad, rotMat);
  return multiplyMatVec3x3ColMajor(rotMat, vec);
}

static float3 rotateAroundAxisXyz(float3 vec, float axisX, float axisY, float axisZ,
    float angleRad) {
  float3 axis;
  axis.x = axisX;
  axis.y = axisY;
  axis.z = axisZ;
  float rotMat[9];
  loadAxisAngleRotMat3x3ColMajor(axis, angleRad, rotMat);
  return multiplyMatVec3x3ColMajor(rotMat, vec);
}

static float3 rotateVec(float3 inVec, float rollRad, float pitchRad, float yawRad) {
  //float rotMat[9];
  //float basisMat[9];
  //loadIdentityMat3x3(basisMat);
  //float invBasisMat[9];
  //float rollMat[9], pitchMat[9], yawMat[9];
  //loadZRotMat3x3ColMajor(rollRad, rollMat);
  //loadXRotMat3x3ColMajor(pitchRad, pitchMat);
  //loadYRotMat3x3ColMajor(yawRad, yawMat);
  //loadIdentityMat3x3(rollMat);
  //loadIdentityMat3x3(pitchMat);
  //loadIdentityMat3x3(yawMat);
  //float pitchYawMat[9];  multiplyMatMat3x3ColMajor(pitchMat, yawMat, pitchYawMat);
  //float rollPitchYawMat[9];  multiplyMatMat3x3ColMajor(rollMat, pitchYawMat, rollPitchYawMat);
  //const float3 rotatedVec = multiplyMatVec3x3ColMajor(rollPitchYawMat, inVec);
  //const float3 rotatedVec = multiplyMatVec3x3ColMajor(pitchYawMat, inVec);
  //const float3 rotatedVec = multiplyMatVec3x3ColMajor(pitchMat, inVec);
  //const float3 rotatedVec = inVec;

  float3 rotatedVec = inVec;
  //float3 zUnitVec = {0.f, 0.f, 1.f};
  //float3 yUnitVec = {0.f, 1.f, 0.f};
  //float3 xUnitVec = {1.f, 0.f, 0.f};
  //rotatedVec = rotateAroundAxis(rotatedVec, zUnitVec, rollRad);
  //rotatedVec = rotateAroundAxis(rotatedVec, xUnitVec, pitchRad);
  //rotatedVec = rotateAroundAxis(rotatedVec, yUnitVec, yawRad);
  rotatedVec = rotateAroundZ(rotatedVec, rollRad);
  rotatedVec = rotateAroundX(rotatedVec, pitchRad);
  rotatedVec = rotateAroundY(rotatedVec, -yawRad);
  //float3 worldXVec;
  //float3 worldYVec;
  //float3 worldZVec;
  //worldXVec = cross(worldYVec, worldZVec);
  //worldZVec = zUnitVec;
  //worldZVec = rotateAroundAxisXyz(worldZVec, 1.f, 0.f, 0.f, pitchRad);
  //worldXVec = rotateAroundAxisXyz(worldXVec, 0.f, 0.f, 1.f, rollRad);
  //worldXVec = rotateAroundAxisXyz(worldXVec, 0.f, 1.f, 0.f, yawRad);
  //float basisTfMat[9];
  //basisTfMat[0] = worldXVec.x;  basisTfMat[1] = worldXVec.y;  basisTfMat[2] = worldXVec.z;
  //basisTfMat[3] = worldYVec.x;  basisTfMat[4] = worldYVec.y;  basisTfMat[5] = worldYVec.z;
  //basisTfMat[6] = worldZVec.x;  basisTfMat[7] = worldZVec.y;  basisTfMat[8] = worldZVec.z;
  //transposeMat3x3(basisTfMat, basisTfMat);
  //rotatedVec = multiplyMatVec3x3ColMajor(basisTfMat, rotatedVec);
  //rotatedVec = rotateAroundAxis(rotatedVec, forwardVec, rollRad);
  //rotatedVec = multiplyMatVec3x3ColMajor(yRotMat, rotatedVec);
  //multiplyMatMat3x3ColMajor(yRotMat, basisMat, basisMat);
  //changeToBasis(zRotMat, basisMat, zRotMat);
  //rotatedVec = multiplyMatVec3x3ColMajor(zRotMat, rotatedVec);
  return rotatedVec;
}

static float3 rotateCamSpaceVec(float3 camSpaceVec, float rollDeg, float pitchDeg, float yawDeg) {
  //rs_matrix3x3 rotMat;
  //rsMatrixLoadIdentity(&rotMat);
  //rsMatrixRotate(&rotMat, yawDeg, 0.f, -1.f, 0.f);
  //rsMatrixLoadRotate(&rotMat, yawDeg, 0.f, -1.f, 0.f);  // Crashes
  float3 rotatedCamSpaceVec = camSpaceVec;
  rotatedCamSpaceVec = rotateVec(rotatedCamSpaceVec, radians(rollDeg), radians(pitchDeg),
      radians(yawDeg));
  //rotatedCamSpaceVec = rsMatrixMultiply(&rotMat, rotatedCamSpaceVec);  // Crashes
  //rotatedCamSpaceVec = rotateAroundZ(rotatedCamSpaceVec, rollDeg);
  //rotatedCamSpaceVec = rotateAroundY(rotatedCamSpaceVec, -yawDeg);
  //rotatedCamSpaceVec = rotateAroundX(rotatedCamSpaceVec, pitchDeg);
  return rotatedCamSpaceVec;
}

static float2 fisheyeCameraSpaceToImageSpace(float3 camSpaceVec) {
  const float srcCamViewPointOffset = cos(radians(srcCamHfovDeg / 2.f));
  const float srcCamViewSpaceVecZ = camSpaceVec.z + (srcCamFocalOffset - srcCamViewPointOffset);
  const float srcCamImagePlaneHalfWidth = sin(radians(srcCamHfovDeg / 2.f));

  float2 srcImagePoint;
  srcImagePoint.x = ((0.5f * (float) srcImageWidthPx)
          * (srcCamFocalOffset * camSpaceVec.x / srcCamViewSpaceVecZ)
          / srcCamImagePlaneHalfWidth)
      + srcCamPrincipalPointXPx;
  srcImagePoint.y = ((0.5f * (float) srcImageHeightPx
              / ((float) srcImageHeightPx / (float) srcImageWidthPx))
          * (srcCamFocalOffset * camSpaceVec.y / srcCamViewSpaceVecZ)
          / srcCamImagePlaneHalfWidth)
      + srcCamPrincipalPointYPx;
  //srcImagePoint.x = -1.f;
  //srcImagePoint.y = -1.f;
  return srcImagePoint;
  //return dstImagePoint;
}

static uchar4 interpolate(float2 pointPx, uchar4 topLeftColor, uchar4 topRightColor,
    uchar4 bottomLeftColor, uchar4 bottomRightColor) {
  float2 xVec, yVec;
  xVec.x = (ceil(pointPx.x) + 0.5f) - (pointPx.x + 0.5f);
  xVec.y = (pointPx.x + 0.5f) - (floor(pointPx.x) + 0.5f);
  yVec.x = (ceil(pointPx.y) + 0.5f) - (pointPx.y + 0.5f);
  yVec.y = (pointPx.y + 0.5f) - (floor(pointPx.y) + 0.5f);
  uchar4 fMatTimesYVecX, fMatTimesYVecY;
  fMatTimesYVecX.a = topLeftColor.a * yVec.x + topRightColor.a * yVec.y;
  fMatTimesYVecX.r = topLeftColor.r * yVec.x + topRightColor.r * yVec.y;
  fMatTimesYVecX.g = topLeftColor.g * yVec.x + topRightColor.g * yVec.y;
  fMatTimesYVecX.b = topLeftColor.b * yVec.x + topRightColor.b * yVec.y;
  fMatTimesYVecY.a = bottomLeftColor.a * yVec.x + bottomRightColor.a * yVec.y;
  fMatTimesYVecY.r = bottomLeftColor.r * yVec.x + bottomRightColor.r * yVec.y;
  fMatTimesYVecY.g = bottomLeftColor.g * yVec.x + bottomRightColor.g * yVec.y;
  fMatTimesYVecY.b = bottomLeftColor.b * yVec.x + bottomRightColor.b * yVec.y;
  uchar4 interpolatedColor;
  interpolatedColor.a = xVec.x * fMatTimesYVecX.a + xVec.y * fMatTimesYVecY.a;
  interpolatedColor.r = xVec.x * fMatTimesYVecX.r + xVec.y * fMatTimesYVecY.r;
  interpolatedColor.g = xVec.x * fMatTimesYVecX.g + xVec.y * fMatTimesYVecY.g;
  interpolatedColor.b = xVec.x * fMatTimesYVecX.b + xVec.y * fMatTimesYVecY.b;
  return interpolatedColor;
}

uchar4 __attribute__((kernel)) linearize(uint32_t x, uint32_t y) {
  float2 dstImagePoint;
  dstImagePoint.x = (float) x + 0.5f;
  dstImagePoint.y = (float) y + 0.5f;
  float3 camSpaceRayVec;
  switch (dstCamGeometryId) {
    case 0:
      camSpaceRayVec = pinholeImageSpaceToCameraSpace(dstImagePoint);
      break;
    case 1:
      camSpaceRayVec = equirectangularImageSpaceToCameraSpace(dstImagePoint);
      break;
  }
  uchar4 outputColor;
  outputColor.a = 255;
  outputColor.r = 0;
  outputColor.g = 0;
  outputColor.b = 0;
  const float3 rotatedRayVec = rotateCamSpaceVec(camSpaceRayVec, dstCamRollDeg, dstCamPitchDeg,
    dstCamYawDeg);
  if (rotatedRayVec.z >= 0.f) {
      const float2 srcImagePointPx = fisheyeCameraSpaceToImageSpace(rotatedRayVec);
      if (srcImagePointPx.x >= 0.f && srcImagePointPx.x < srcImageWidthPx
          && srcImagePointPx.y >= 0.f && srcImagePointPx.y < srcImageHeightPx) {
        outputColor = rsGetElementAt_uchar4(srcImage, floor(srcImagePointPx.x),
          floor(srcImagePointPx.y));
      }
  }
  //uchar4 outputColor = inputColor;
  //outputColor.a = 255;
  //outputColor.r = 255;
  //outputColor.g = 255;
  //outputColor.b = 0;
  return outputColor;
}