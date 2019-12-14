precision mediump float;

uniform sampler2D u_Tex;
uniform float u_srccam_hfov_deg;
uniform float u_srccam_focallength;
uniform vec2 u_srccam_opticalcenter;
uniform float u_dstcam_hfov_deg;
uniform vec2 u_dstcam_imgsize_px;
uniform vec2 u_srccam_imgsize_px;

varying vec4 v_PosInViewSpace;
//varying vec2 v_TexVec;

float canvas_side_half = 0.5;

vec2 fisheye_to_pinhole() {
  float srccam_hfov_deg = u_srccam_hfov_deg;
  float srccam_focallength = u_srccam_focallength;
  float dstcam_hfov_deg = u_dstcam_hfov_deg;
  vec2 srccam_imgsize_px = u_srccam_imgsize_px;
  vec2 dstcam_imgsize_px = u_dstcam_imgsize_px;
  vec2 srccam_principalpoint_px;

  vec3 viewspace_vec;
  //vec2 dst_imgpoint;
  //dst_imgpoint.x = v_TexVec.x * dstcam_imgsize_px.x;
  //dst_imgpoint.y = v_TexVec.y * dstcam_imgsize_px.y;
  //float dstcam_focallength_px = (dstcam_imgsize_px.x / 2.0) / tan(radians(dstcam_hfov_deg / 2.0));
  //viewspace_vec.x = dst_imgpoint.x - (dstcam_imgsize_px.x / 2.0);
  //viewspace_vec.y = dst_imgpoint.y - (dstcam_imgsize_px.y / 2.0);
  //viewspace_vec.z = dstcam_focallength_px;
  //viewspace_vec = normalize(viewspace_vec);
  vec2 dst_canvasscale;
  if (dstcam_imgsize_px.y <= dstcam_imgsize_px.x) {
    dst_canvasscale.x = 1.0;
    dst_canvasscale.y = 1.0;
  } else {
    dst_canvasscale.x = dstcam_imgsize_px.y / dstcam_imgsize_px.x;
    dst_canvasscale.y = dstcam_imgsize_px.y / dstcam_imgsize_px.x;
  }
  viewspace_vec.x = v_PosInViewSpace.x * dst_canvasscale.x;
  viewspace_vec.y = v_PosInViewSpace.y * dst_canvasscale.y;
  viewspace_vec.z = canvas_side_half / tan(radians(dstcam_hfov_deg / 2.0));
  viewspace_vec = normalize(viewspace_vec);

  float srccam_viewpointoffset = cos(radians(srccam_hfov_deg / 2.0));
  float srccam_viewspace_vec_z = viewspace_vec.z + (srccam_focallength - srccam_viewpointoffset);
  float srccam_imageplane_halfwidth = sin(radians(srccam_hfov_deg / 2.0));

  vec2 src_imgpoint;
  src_imgpoint.x = ((srccam_imgsize_px.x * 0.5) * (srccam_focallength * viewspace_vec.x / srccam_viewspace_vec_z) / srccam_imageplane_halfwidth) + u_srccam_opticalcenter.x * srccam_imgsize_px.x;
  src_imgpoint.y = ((srccam_imgsize_px.y * 0.5 / (srccam_imgsize_px.y / srccam_imgsize_px.x)) * (srccam_focallength * (-viewspace_vec.y) / srccam_viewspace_vec_z) / srccam_imageplane_halfwidth) + u_srccam_opticalcenter.y * srccam_imgsize_px.y;
  return src_imgpoint;
  //return src_texvec;
  //return dst_imgpoint;
}

void main() {
  vec2 src_imgpoint = fisheye_to_pinhole();
  vec2 src_texvec;
  src_texvec.x = src_imgpoint.x / u_srccam_imgsize_px.x;
  src_texvec.y = src_imgpoint.y / u_srccam_imgsize_px.y;
  vec3 color;
  if (all(greaterThan(src_texvec, vec2(0.0, 0.0))) && all(lessThan(src_texvec, vec2(1.0, 1.0)))) {
    color = texture2D(u_Tex, src_texvec).rgb;
    //color = vec3(0.0, 1.0, 0.0);
  } else {
    color = vec3(0.0, 0.0, 0.0);
    //color = vec3(1.0, 1.0, 0.0);
  }
  gl_FragColor = vec4(color, 1.0);
}