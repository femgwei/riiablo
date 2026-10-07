#ifdef GL_ES
  #define LOWP lowp
  precision mediump float;
#else
  #define LOWP
#endif

uniform sampler2D ColorTable; //256 x 1  pixels
uniform sampler2D ColorMap;   //256 x 22 pixels
uniform sampler2D u_texture;
uniform mat4 u_projTrans;
uniform int blendMode;
uniform int colormapId;
uniform float gamma;
uniform int lightingEnabled;
uniform vec3 ambientLight;
uniform int lightCount;
uniform vec4 localLights[16]; // centre x/y, radius x/y in isometric pixels
uniform vec3 localLightColors[16];

varying vec2 v_texCoord;
varying vec4 tint;
varying vec2 worldPosition;

void main() {
  vec4 color = texture2D(u_texture, v_texCoord);
  // Intensity/alpha indexed textures carry the DCC palette index in the
  // source alpha channel. Keep it available after the palette lookup so a
  // caller can use index-zero transparency without depending on the palette
  // texture's own alpha channel.
  float sourceIndex = color.a;
  if (colormapId > 0 && color.a > 0.0) {
    color.r = (float(colormapId) + 0.5) / 22.0;
    color = texture2D(ColorMap, color.ar);
  }

  // workaround for https://github.com/collinsmith/riiablo/issues/139
  else if (color.a == 0.99609375) { // index 255 == float(255 / 256)
    color.a = color.a - 0.00390625; // index 255 -> 254 by subtracting float(1 / 256)
  }

  color = texture2D(ColorTable, color.ar);

  // Set alpha to tint alpha, including palette id 0
  if (blendMode == 0) {
    color.a = tint.a;

  // Set alpha to tint alpha
  } else if (blendMode == 1) {
    if (color.a > 0.0) color.a = tint.a;

  // Set alpha based on luminance
  } else if (blendMode == 2) {
    if (color.a > 0.0) color.a = (0.299*color.r + 0.587*color.g + 0.114*color.b) * 2.0;

  // Palette-preserving luminosity mask with caller-controlled flicker alpha.
  } else if (blendMode == 11) {
    if (color.a > 0.0) {
      color.a = min(1.0, (0.299*color.r + 0.587*color.g + 0.114*color.b) * 2.0 * tint.a);
    }

  // Native PL2/Screen poison-cloud mask. The fixed-function blend state is
  // SRC_ALPHA, ONE_MINUS_SRC_COLOR, so this branch only supplies the source
  // RGB and its luminance-driven intensity.
  } else if (blendMode == 12) {
    if (color.a > 0.0) {
      color.a = min(1.0, (0.299*color.r + 0.587*color.g + 0.114*color.b) * 2.0);
    }

  // Indexed sprite alpha: index 0 is transparent, all other DCC indices
  // retain the palette colour and use the caller tint alpha.
  } else if (blendMode == 13) {
    color.a = sourceIndex > 0.0 ? tint.a : 0.0;

  // Native missile Trans=1 sprites use the palette colour as an additive
  // source. Index zero remains transparent; the fixed-function batch state
  // supplies the destination-preserving additive equation.
  } else if (blendMode == 14) {
    color.a = sourceIndex > 0.0 ? tint.a : 0.0;

  // Set alpha based on luminance and color to tint
  } else if (blendMode == 3) {
    if (color.a > 0.0) {
      //color.a = (0.299*color.r + 0.587*color.g + 0.114*color.b) * 2.0;
      //color.rgb = tint.rgb;
      float avg = (color.r + color.g + color.b) / 3.0;
      color = vec4(avg, avg, avg, 1.0) * tint;
    }

  // Sets color to tint
  } else if (blendMode == 4) {
    if (color.a > 0.0) color = tint;

  // Sets color to tint, blending using src as 1.0 - alpha
  } else if (blendMode == 5) {
    if (color.a > 0.0) {
      color.a = (1.0 - color.r);
      color.rgb = tint.rgb;
    }

  // Sets color to tint, blending using src as alpha
  } else if (blendMode == 6) {
    if (color.a > 0.0) {
      color.a = color.r;
      color.rgb = tint.rgb;
    }

  // Same as 1, except adds tint
  } else if (blendMode == 7) {
    if (color.a > 0.0) {
      color.rgb += tint.rgb;
    }

  // Same as 1, except adds contrast and brightness
  } else if (blendMode == 8) {
    if (color.a > 0.0) {
      // Apply contrast
      color.rgb -= 0.5;
      color.rgb *= 1.5; // 1.8
      color.rgb += 0.5;

      // Apply brightness
      color.rgb += 0.3; // 0.4
    }

  // Same as 1, except hard coded red
  } else if (blendMode == 9) {
    if (color.a > 0.0) {
      color.a = tint.a;
      color.r += 0.04;
      color.g = 0.0;
      color.b = 0.0;
    }

  // Same as 8, except darker
  } else if (blendMode == 10) {
    if (color.a > 0.0) {
      color.rgba *= tint.rgba;
    }
  }

  vec3 colorRGB = pow(color.rgb, vec3(1.0 / gamma));

  // TODO: Move this effect and gamma effect to a separate shader
  // TODO: Add configs to this effect for contrast + brightness
  colorRGB -= 0.5;
  colorRGB *= 1.20;
  colorRGB += 0.60;

  // D2 composes the outdoor environment with unit/object light radii. Keep
  // this after the legacy palette contrast pass so darkness is not raised by
  // its hard-coded brightness offset. The fixed-size loop is GLES2-safe.
  // Native luminosity/additive effects and mouse highlights are rendered at
  // full brightness. Their surroundings are lit by the local-light list.
  bool selfLit = blendMode == 2 || blendMode == 8 || blendMode == 11
      || blendMode == 12 || blendMode == 14;
  if (lightingEnabled != 0 && !selfLit) {
    vec3 light = ambientLight;
    for (int i = 0; i < 16; i++) {
      if (i >= lightCount) break;
      vec4 source = localLights[i];
      vec2 delta = (worldPosition - source.xy) / max(source.zw, vec2(1.0));
      float falloff = 1.0 - smoothstep(0.35, 1.0, length(delta));
      light = max(light, localLightColors[i] * falloff);
    }
    colorRGB *= clamp(light, 0.0, 1.0);
  }

  gl_FragColor = vec4(colorRGB, color.a);
}
