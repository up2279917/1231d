export const BIOME_VERTEX_SHADER = `
  precision highp float;
  attribute vec2 a_position;
  attribute vec3 a_color;
  attribute float a_isHovered;

  uniform vec2 u_resolution;
  uniform vec2 u_translation;
  uniform float u_scale;

  varying vec3 v_color;
  varying float v_isHovered;

  void main() {
    vec2 worldPos = a_position;
    vec2 translated = (worldPos + u_translation) * u_scale;
    vec2 normalized = translated / (u_resolution * 0.5);
    float aspect = u_resolution.x / u_resolution.y;
    normalized.x /= aspect;

    gl_Position = vec4(normalized.x, -normalized.y, 0, 1);
    v_color = a_color;
    v_isHovered = a_isHovered;
  }
`;

export const BIOME_FRAGMENT_SHADER = `
  precision highp float;
  varying vec3 v_color;
  varying float v_isHovered;

  void main() {
    vec3 color = v_color;
    if (v_isHovered > 0.5) {
      color = mix(color, vec3(1.0, 0.7, 0.0), 0.3);
    }
    gl_FragColor = vec4(color, 0.8);
  }
`;

export const ENTITY_VERTEX_SHADER = `
  precision highp float;
  attribute vec2 a_position;
  attribute vec2 a_texCoord;
  attribute vec4 a_color;
  attribute float a_type;
  attribute float a_isHovered;

  uniform vec2 u_resolution;
  uniform vec2 u_translation;
  uniform float u_scale;

  varying vec2 v_texCoord;
  varying vec4 v_color;
  varying float v_type;
  varying float v_isHovered;

  void main() {
    vec2 worldPos = a_position;
    vec2 translated = (worldPos + u_translation) * u_scale;
    vec2 normalized = translated / (u_resolution * 0.5);
    float aspect = u_resolution.x / u_resolution.y;
    normalized.x /= aspect;

    gl_Position = vec4(normalized.x, -normalized.y, 0, 1);
    v_texCoord = a_texCoord;
    v_color = a_color;
    v_type = a_type;
    v_isHovered = a_isHovered;
  }
`;

export const ENTITY_FRAGMENT_SHADER = `
  precision highp float;
  varying vec2 v_texCoord;
  varying vec4 v_color;
  varying float v_type;
  varying float v_isHovered;

  uniform sampler2D u_texture;

  void main() {
    vec4 color = v_color;
    if (v_type < 0.5) {
      vec4 texColor = texture2D(u_texture, v_texCoord);
      color *= texColor;
    }
    if (v_isHovered > 0.5) {
      color = mix(color, vec4(1.0, 0.7, 0.0, 1.0), 0.3);
    }
    gl_FragColor = color;
  }
`;

import {
	BUFFER_SIZES,
	VERTEX_SIZES,
	CHUNK_SIZE,
	PLAYER_ICON_SIZE,
	LOCATION_ICON_SIZE,
} from "./constants";

export {
	BUFFER_SIZES,
	VERTEX_SIZES,
	CHUNK_SIZE,
	PLAYER_ICON_SIZE,
	LOCATION_ICON_SIZE,
};
