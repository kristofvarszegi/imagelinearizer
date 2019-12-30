package com.krisstof.imagelinearizer;

final class DstCamParameters {
  private static final String TAG = DstCamParameters.class.getSimpleName();

  enum CAMERA_GEOMETRY {PINHOLE, EQUIRECTANGULAR}

  CAMERA_GEOMETRY geometry;
  float hfovDeg;
  int imageWidthPx, imageHeightPx;

  DstCamParameters() {
    this.geometry = CAMERA_GEOMETRY.PINHOLE;
    this.hfovDeg = 150.f;
    this.imageWidthPx = 1920;
    this.imageHeightPx = 1080;
  }

  DstCamParameters(CAMERA_GEOMETRY geometry, float hfovDeg, int imageWidthPx, int imageHeightPx) {
    this.geometry = geometry;
    this.hfovDeg = hfovDeg;
    this.imageWidthPx = imageWidthPx;
    this.imageHeightPx = imageHeightPx;
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
