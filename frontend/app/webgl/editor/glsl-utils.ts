/**
 * Helpers for generating GLSL source strings safely.
 *
 * When templating GLSL with JS/TS template literals, numbers like `2.0` stringify to `"2"`.
 * In GLSL, `2` is an int literal, which can break expressions like `2 / vec2(...)`.
 */

/**
 * Formats a JS number as a GLSL float literal.
 * - `2` -> `2.0`
 * - `0.5` -> `0.5`
 * - `1e-4` -> `1e-4` (GLSL supports scientific notation)
 */
export function glslFloatLiteral(value: number): string {
  if (!Number.isFinite(value)) return '0.0'

  const raw = String(value)

  if (raw.includes('.') || raw.includes('e') || raw.includes('E')) return raw

  return `${raw}.0`
}
