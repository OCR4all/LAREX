<script setup lang="ts">
const canvas = useTemplateRef<HTMLCanvasElement>('canvas')
const colorMode = useColorMode()
let dispose: (() => void) | undefined

// Match the noise, palette, and ordered dithering of the Keycloak login panel.
// Render directly with WebGL so the docs do not need the React/Three.js stack.
const vertexSource = `#version 300 es
in vec2 position;
void main() {
  gl_Position = vec4(position, 0.0, 1.0);
}`

const fragmentSource = `#version 300 es
precision highp float;
uniform vec2 resolution;
uniform float time;
uniform vec3 waveColor;
uniform vec3 backgroundColor;
out vec4 outColor;
const float waveFrequency = 2.6;
const float waveAmplitude = 0.34;
const float pixelSize = 3.0;

vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
vec2 fade(vec2 t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }

float cnoise(vec2 p) {
  vec4 pi = floor(p.xyxy) + vec4(0.0, 0.0, 1.0, 1.0);
  vec4 pf = fract(p.xyxy) - vec4(0.0, 0.0, 1.0, 1.0);
  pi = mod289(pi);
  vec4 ix = pi.xzxz;
  vec4 iy = pi.yyww;
  vec4 fx = pf.xzxz;
  vec4 fy = pf.yyww;
  vec4 i = permute(permute(ix) + iy);
  vec4 gx = fract(i * (1.0 / 41.0)) * 2.0 - 1.0;
  vec4 gy = abs(gx) - 0.5;
  vec4 tx = floor(gx + 0.5);
  gx -= tx;
  vec2 g00 = vec2(gx.x, gy.x);
  vec2 g10 = vec2(gx.y, gy.y);
  vec2 g01 = vec2(gx.z, gy.z);
  vec2 g11 = vec2(gx.w, gy.w);
  vec4 norm = taylorInvSqrt(vec4(dot(g00, g00), dot(g01, g01), dot(g10, g10), dot(g11, g11)));
  g00 *= norm.x;
  g01 *= norm.y;
  g10 *= norm.z;
  g11 *= norm.w;
  float n00 = dot(g00, vec2(fx.x, fy.x));
  float n10 = dot(g10, vec2(fx.y, fy.y));
  float n01 = dot(g01, vec2(fx.z, fy.z));
  float n11 = dot(g11, vec2(fx.w, fy.w));
  vec2 fadeXY = fade(pf.xy);
  vec2 nX = mix(vec2(n00, n01), vec2(n10, n11), fadeXY.x);
  return 2.3 * mix(nX.x, nX.y, fadeXY.y);
}

float fbm(vec2 p) {
  float value = 0.0;
  float amplitude = 1.0;
  float frequency = waveFrequency;
  for (int i = 0; i < 4; i++) {
    value += amplitude * abs(cnoise(p));
    p *= frequency;
    amplitude *= waveAmplitude;
  }
  return value;
}

const float bayerMatrix8x8[64] = float[64](
  0.0 / 64.0, 48.0 / 64.0, 12.0 / 64.0, 60.0 / 64.0, 3.0 / 64.0, 51.0 / 64.0, 15.0 / 64.0, 63.0 / 64.0,
  32.0 / 64.0, 16.0 / 64.0, 44.0 / 64.0, 28.0 / 64.0, 35.0 / 64.0, 19.0 / 64.0, 47.0 / 64.0, 31.0 / 64.0,
  8.0 / 64.0, 56.0 / 64.0, 4.0 / 64.0, 52.0 / 64.0, 11.0 / 64.0, 59.0 / 64.0, 7.0 / 64.0, 55.0 / 64.0,
  40.0 / 64.0, 24.0 / 64.0, 36.0 / 64.0, 20.0 / 64.0, 43.0 / 64.0, 27.0 / 64.0, 39.0 / 64.0, 23.0 / 64.0,
  2.0 / 64.0, 50.0 / 64.0, 14.0 / 64.0, 62.0 / 64.0, 1.0 / 64.0, 49.0 / 64.0, 13.0 / 64.0, 61.0 / 64.0,
  34.0 / 64.0, 18.0 / 64.0, 46.0 / 64.0, 30.0 / 64.0, 33.0 / 64.0, 17.0 / 64.0, 45.0 / 64.0, 29.0 / 64.0,
  10.0 / 64.0, 58.0 / 64.0, 6.0 / 64.0, 54.0 / 64.0, 9.0 / 64.0, 57.0 / 64.0, 5.0 / 64.0, 53.0 / 64.0,
  42.0 / 64.0, 26.0 / 64.0, 38.0 / 64.0, 22.0 / 64.0, 41.0 / 64.0, 25.0 / 64.0, 37.0 / 64.0, 21.0 / 64.0
);


void main() {
  vec2 pixel = pixelSize * floor(gl_FragCoord.xy / pixelSize);
  vec2 uv = pixel / resolution - 0.5;
  uv.x *= resolution.x / resolution.y;
  float value = fbm(uv + fbm(uv - time * 0.035));
  vec3 color = mix(backgroundColor, waveColor, clamp(value, 0.0, 1.0));
  ivec2 cell = ivec2(mod(floor(gl_FragCoord.xy / pixelSize), 8.0));
  float threshold = bayerMatrix8x8[cell.y * 8 + cell.x] - 0.25;
  color += threshold * 0.25;
  float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
  color = clamp(color - mix(0.2, 0.0, smoothstep(0.45, 0.8, luminance)), 0.0, 1.0);
  outColor = vec4(floor(color * 4.0 + 0.5) / 4.0, 1.0);
}`

onMounted(() => {
  const element = canvas.value
  const gl = element?.getContext('webgl2', { antialias: false, alpha: false })
  if (!element || !gl) return

  function compile(type: number, source: string) {
    const shader = gl!.createShader(type)
    if (!shader) return null
    gl!.shaderSource(shader, source)
    gl!.compileShader(shader)
    if (!gl!.getShaderParameter(shader, gl!.COMPILE_STATUS)) {
      gl!.deleteShader(shader)
      return null
    }
    return shader
  }

  const vertex = compile(gl.VERTEX_SHADER, vertexSource)
  const fragment = compile(gl.FRAGMENT_SHADER, fragmentSource)
  const program = gl.createProgram()
  const buffer = gl.createBuffer()
  const release = () => {
    if (vertex) gl.deleteShader(vertex)
    if (fragment) gl.deleteShader(fragment)
    if (program) gl.deleteProgram(program)
    if (buffer) gl.deleteBuffer(buffer)
  }
  if (!vertex || !fragment || !program || !buffer) {
    release()
    return
  }
  gl.attachShader(program, vertex)
  gl.attachShader(program, fragment)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    release()
    return
  }

  gl.useProgram(program)
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, -1, 1, 1, -1, 1, 1]), gl.STATIC_DRAW)
  const position = gl.getAttribLocation(program, 'position')
  gl.enableVertexAttribArray(position)
  gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0)

  const resolution = gl.getUniformLocation(program, 'resolution')
  const time = gl.getUniformLocation(program, 'time')
  const waveColor = gl.getUniformLocation(program, 'waveColor')
  const backgroundColor = gl.getUniformLocation(program, 'backgroundColor')
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  const startedAt = performance.now()
  let frame = 0
  let visible = true

  function draw(now: number) {
    const dark = colorMode.value === 'dark'
    gl!.viewport(0, 0, element!.width, element!.height)
    gl!.uniform2f(resolution, element!.width, element!.height)
    gl!.uniform1f(time, reducedMotion.matches ? 0 : (now - startedAt) / 1000)
    gl!.uniform3fv(waveColor, dark ? [0.08, 0.3, 0.7] : [0.35, 0.55, 1])
    gl!.uniform3fv(backgroundColor, dark ? [0.04, 0.06, 0.1] : [0.95, 0.97, 1])
    gl!.drawArrays(gl!.TRIANGLES, 0, 6)
  }

  function animate(now: number) {
    draw(now)
    frame = requestAnimationFrame(animate)
  }

  function updateAnimation() {
    cancelAnimationFrame(frame)
    draw(performance.now())
    if (visible && !document.hidden && !reducedMotion.matches) {
      frame = requestAnimationFrame(animate)
    }
  }

  const resize = new ResizeObserver(() => {
    // Use CSS pixels, as the login theme does, to keep the dither grain consistent.
    element.width = Math.max(1, Math.round(element.clientWidth))
    element.height = Math.max(1, Math.round(element.clientHeight))
    updateAnimation()
  })
  const intersection = new IntersectionObserver(([entry]) => {
    visible = entry?.isIntersecting ?? false
    updateAnimation()
  })
  resize.observe(element)
  intersection.observe(element)
  reducedMotion.addEventListener('change', updateAnimation)
  document.addEventListener('visibilitychange', updateAnimation)
  const stopWatching = watch(() => colorMode.value, updateAnimation)
  updateAnimation()

  dispose = () => {
    cancelAnimationFrame(frame)
    resize.disconnect()
    intersection.disconnect()
    reducedMotion.removeEventListener('change', updateAnimation)
    document.removeEventListener('visibilitychange', updateAnimation)
    stopWatching()
    release()
  }
})

onBeforeUnmount(() => dispose?.())
</script>

<template>
  <div class="pointer-events-none absolute inset-0 overflow-hidden bg-navy-600 dark:bg-navy-950" aria-hidden="true">
    <canvas ref="canvas" class="absolute inset-0 h-full w-full" />
    <div class="absolute inset-0 bg-[linear-gradient(90deg,rgba(10,10,10,0.76)_0%,rgba(10,10,10,0.56)_46%,rgba(10,10,10,0.24)_100%)]" />
  </div>
</template>
