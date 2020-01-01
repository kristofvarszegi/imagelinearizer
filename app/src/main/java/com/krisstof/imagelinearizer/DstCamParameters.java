package com.krisstof.imagelinearizer;

final class DstCamParameters {
  private static final String TAG = DstCamParameters.class.getSimpleName();
  enum CAMERA_GEOMETRY {PINHOLE, EQUIRECTANGULAR}
  static final int DEFAULT_IMAGEWIDTH_PX = 1920;
  static final int DEFAULT_IMAGEHEIGHT_PX = 1080;

  CAMERA_GEOMETRY geometry;
  float hfovDeg;
  int imageWidthPx, imageHeightPx;
  float rollDeg, pitchDeg, yawDeg;

  DstCamParameters() {
    geometry = CAMERA_GEOMETRY.PINHOLE;
    hfovDeg = 150.f;
    imageWidthPx = DEFAULT_IMAGEWIDTH_PX;
    imageHeightPx = DEFAULT_IMAGEHEIGHT_PX;
    rollDeg = 0.f;
    pitchDeg = 0.f;
    yawDeg = 0.f;
  }

  DstCamParameters(CAMERA_GEOMETRY geometry, float hfovDeg, int imageWidthPx, int imageHeightPx,
                   float rollDeg, float pitchDeg, float yawDeg) {
    this.geometry = geometry;
    this.hfovDeg = hfovDeg;
    this.imageWidthPx = imageWidthPx;
    this.imageHeightPx = imageHeightPx;
    this.rollDeg = rollDeg;
    this.pitchDeg = pitchDeg;
    this.yawDeg = yawDeg;
  }

  int getGeometryId() {
    switch (geometry) {
      case PINHOLE:
        return 0;
      case EQUIRECTANGULAR:
        return 1;
      default:
        return -1;
    }
  }
}
