/* eslint-disable react/no-unknown-property */
import { useEffect, useRef, type ComponentType } from "react";
import { Canvas, useFrame, useThree, type ThreeEvent } from "@react-three/fiber";
import { EffectComposer, wrapEffect } from "@react-three/postprocessing";
import { Effect } from "postprocessing";
import * as THREE from "three";

const waveVertexShader = `
precision highp float;
varying vec2 vUv;

void main() {
  vUv = uv;
  gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
}
`;

const waveFragmentShader = `
precision highp float;
uniform vec2 resolution;
uniform float time;
uniform float waveSpeed;
uniform float waveFrequency;
uniform float waveAmplitude;
uniform vec3 waveColor;
uniform vec3 backgroundColor;
uniform vec2 mousePos;
uniform int enableMouseInteraction;
uniform float mouseRadius;

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

void main() {
  vec2 uv = gl_FragCoord.xy / resolution.xy - 0.5;
  uv.x *= resolution.x / resolution.y;
  vec2 movingUV = uv - time * waveSpeed;
  float value = fbm(uv + fbm(movingUV));

  if (enableMouseInteraction == 1) {
    vec2 mouseNDC = (mousePos / resolution - 0.5) * vec2(1.0, -1.0);
    mouseNDC.x *= resolution.x / resolution.y;
    value -= 0.5 * (1.0 - smoothstep(0.0, mouseRadius, length(uv - mouseNDC)));
  }

  gl_FragColor = vec4(mix(backgroundColor, waveColor, clamp(value, 0.0, 1.0)), 1.0);
}
`;

const ditherFragmentShader = `
precision highp float;
uniform float colorNum;
uniform float pixelSize;

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

vec3 dither(vec2 uv, vec3 color) {
  vec2 scaledCoord = floor(uv * resolution / pixelSize);
  int x = int(mod(scaledCoord.x, 8.0));
  int y = int(mod(scaledCoord.y, 8.0));
  float threshold = bayerMatrix8x8[y * 8 + x] - 0.25;
  float stepSize = 1.0 / (colorNum - 1.0);
  color += threshold * stepSize;
  float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
  color = clamp(color - mix(0.2, 0.0, smoothstep(0.45, 0.8, luminance)), 0.0, 1.0);
  return floor(color * (colorNum - 1.0) + 0.5) / (colorNum - 1.0);
}

void mainImage(in vec4 inputColor, in vec2 uv, out vec4 outputColor) {
  vec2 normalizedPixelSize = pixelSize / resolution;
  vec2 uvPixel = normalizedPixelSize * floor(uv / normalizedPixelSize);
  vec4 color = texture2D(inputBuffer, uvPixel);
  color.rgb = dither(uv, color.rgb);
  outputColor = color;
}
`;

class RetroEffectImpl extends Effect {
  public uniforms: Map<string, THREE.Uniform<number>>;

  constructor() {
    const uniforms = new Map<string, THREE.Uniform<number>>([
      ["colorNum", new THREE.Uniform(4)],
      ["pixelSize", new THREE.Uniform(2)]
    ]);
    super("RetroEffect", ditherFragmentShader, { uniforms });
    this.uniforms = uniforms;
  }

  set colorNum(value: number) {
    this.uniforms.get("colorNum")!.value = value;
  }

  get colorNum() {
    return this.uniforms.get("colorNum")!.value;
  }

  set pixelSize(value: number) {
    this.uniforms.get("pixelSize")!.value = value;
  }

  get pixelSize() {
    return this.uniforms.get("pixelSize")!.value;
  }
}

const RetroEffect = (props: { colorNum: number; pixelSize: number }) => {
  const { colorNum, pixelSize } = props;
  const WrappedRetroEffect = wrapEffect(RetroEffectImpl) as unknown as ComponentType<{
    colorNum: number;
    pixelSize: number;
  }>;
  return <WrappedRetroEffect colorNum={colorNum} pixelSize={pixelSize} />;
};

interface DitherProps {
  waveColor?: [number, number, number];
  backgroundColor?: [number, number, number];
  waveSpeed?: number;
  waveFrequency?: number;
  waveAmplitude?: number;
  colorNum?: number;
  pixelSize?: number;
  disableAnimation?: boolean;
  enableMouseInteraction?: boolean;
  mouseRadius?: number;
}

interface WaveUniforms {
  [key: string]: THREE.IUniform;
  time: THREE.Uniform<number>;
  resolution: THREE.Uniform<THREE.Vector2>;
  waveSpeed: THREE.Uniform<number>;
  waveFrequency: THREE.Uniform<number>;
  waveAmplitude: THREE.Uniform<number>;
  waveColor: THREE.Uniform<THREE.Color>;
  backgroundColor: THREE.Uniform<THREE.Color>;
  mousePos: THREE.Uniform<THREE.Vector2>;
  enableMouseInteraction: THREE.Uniform<number>;
  mouseRadius: THREE.Uniform<number>;
}

function DitheredWaves({
  waveColor,
  backgroundColor,
  waveSpeed,
  waveFrequency,
  waveAmplitude,
  colorNum,
  pixelSize,
  disableAnimation,
  enableMouseInteraction,
  mouseRadius
}: Required<DitherProps>) {
  const mesh = useRef<THREE.Mesh>(null);
  const mouseRef = useRef(new THREE.Vector2());
  const { viewport, size, gl } = useThree();
  const uniformsRef = useRef<WaveUniforms>({
    time: new THREE.Uniform(0),
    resolution: new THREE.Uniform(new THREE.Vector2()),
    waveSpeed: new THREE.Uniform(waveSpeed),
    waveFrequency: new THREE.Uniform(waveFrequency),
    waveAmplitude: new THREE.Uniform(waveAmplitude),
    waveColor: new THREE.Uniform(new THREE.Color(...waveColor)),
    backgroundColor: new THREE.Uniform(new THREE.Color(...backgroundColor)),
    mousePos: new THREE.Uniform(new THREE.Vector2()),
    enableMouseInteraction: new THREE.Uniform(enableMouseInteraction ? 1 : 0),
    mouseRadius: new THREE.Uniform(mouseRadius)
  });

  useEffect(() => {
    const dpr = gl.getPixelRatio();
    uniformsRef.current.resolution.value.set(Math.floor(size.width * dpr), Math.floor(size.height * dpr));
  }, [gl, size]);

  useFrame(({ clock }) => {
    const uniforms = uniformsRef.current;
    if (!disableAnimation) uniforms.time.value = clock.getElapsedTime();
    uniforms.waveSpeed.value = waveSpeed;
    uniforms.waveFrequency.value = waveFrequency;
    uniforms.waveAmplitude.value = waveAmplitude;
    uniforms.waveColor.value.set(...waveColor);
    uniforms.backgroundColor.value.set(...backgroundColor);
    uniforms.enableMouseInteraction.value = enableMouseInteraction ? 1 : 0;
    uniforms.mouseRadius.value = mouseRadius;
    if (enableMouseInteraction) uniforms.mousePos.value.copy(mouseRef.current);
  });

  const handlePointerMove = (event: ThreeEvent<PointerEvent>) => {
    if (!enableMouseInteraction) return;
    const rect = gl.domElement.getBoundingClientRect();
    const dpr = gl.getPixelRatio();
    mouseRef.current.set((event.clientX - rect.left) * dpr, (event.clientY - rect.top) * dpr);
  };

  return (
    <>
      <mesh ref={mesh} scale={[viewport.width, viewport.height, 1]}>
        <planeGeometry args={[1, 1]} />
        <shaderMaterial
          vertexShader={waveVertexShader}
          fragmentShader={waveFragmentShader}
          uniforms={uniformsRef.current}
        />
      </mesh>
      <EffectComposer>
        <RetroEffect colorNum={colorNum} pixelSize={pixelSize} />
      </EffectComposer>
      <mesh
        onPointerMove={handlePointerMove}
        position={[0, 0, 0.01]}
        scale={[viewport.width, viewport.height, 1]}
        visible={false}
      >
        <planeGeometry args={[1, 1]} />
        <meshBasicMaterial transparent opacity={0} />
      </mesh>
    </>
  );
}

export default function Dither({
  waveColor = [0.35, 0.55, 1],
  backgroundColor = [0.95, 0.97, 1],
  waveSpeed = 0.05,
  waveFrequency = 3,
  waveAmplitude = 0.3,
  colorNum = 4,
  pixelSize = 3,
  disableAnimation = false,
  enableMouseInteraction = true,
  mouseRadius = 1
}: DitherProps) {
  return (
    <Canvas
      className="dither-container"
      camera={{ position: [0, 0, 6] }}
      dpr={1}
      gl={{ antialias: true, preserveDrawingBuffer: true }}
    >
      <DitheredWaves
        waveColor={waveColor}
        backgroundColor={backgroundColor}
        waveSpeed={waveSpeed}
        waveFrequency={waveFrequency}
        waveAmplitude={waveAmplitude}
        colorNum={colorNum}
        pixelSize={pixelSize}
        disableAnimation={disableAnimation}
        enableMouseInteraction={enableMouseInteraction}
        mouseRadius={mouseRadius}
      />
    </Canvas>
  );
}
