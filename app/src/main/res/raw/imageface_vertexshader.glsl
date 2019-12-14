uniform mat4 u_MVMat;
uniform mat4 u_MVPMat;

attribute vec3 a_Pos;
attribute vec2 a_TexVec;

varying vec4 v_PosInViewSpace;
//varying vec2 v_TexVec;

void main() {
  v_PosInViewSpace = u_MVMat * vec4(a_Pos, 1.0);
  //v_TexVec = a_TexVec;
  gl_Position = u_MVPMat * vec4(a_Pos, 1.0);
}