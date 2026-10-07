attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

uniform mat4 u_projTrans;

varying vec2 v_texCoord;
varying vec4 tint;
varying vec2 worldPosition;

void main() {
  tint = a_color;
  v_texCoord = a_texCoord0;
  worldPosition = a_position.xy;
  gl_Position = u_projTrans * a_position;
}
