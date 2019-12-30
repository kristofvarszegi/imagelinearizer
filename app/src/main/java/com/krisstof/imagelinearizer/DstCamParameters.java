package com.krisstof.imagelinearizer;

final class DstCamParameters {
  private static final String TAG = DstCamParameters.class.getSimpleName();
  enum CAMERA_GEOMETRY {PINHOLE, EQUIRECTANGULAR}
  static final int DEFAULT_IMAGEWIDTH_PX = 1920;
  static final int DEFAULT_IMAGEHEIGHT_PX = 1080;

  CAMERA_GEOMETRY geometry;
  float hfovDeg;
  int imageWidthPx, imageHeightPx;

  DstCamParameters() {
    this.geometry = CAMERA_GEOMETRY.PINHOLE;
    this.hfovDeg = 150.f;
    this.imageWidthPx = DEFAULT_IMAGEWIDTH_PX;
    this.imageHeightPx = DEFAULT_IMAGEHEIGHT_PX;
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
