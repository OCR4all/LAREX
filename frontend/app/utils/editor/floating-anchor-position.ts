export type FloatingControlPlacement = 'left-sidebar' | 'right-sidebar' | 'toolbar'

export interface FloatingViewportSize {
  width: number
  height: number
}

export interface FloatingControlSize {
  width: number
  height: number
}

export interface FloatingControlPosition {
  x: number
  y: number
}

export interface FloatingControlOffset {
  dx: number
  dy: number
}

export interface FloatingAnchorRect {
  left: number
  top: number
  right: number
  bottom: number
  width: number
  height: number
}

export interface ComputeFloatingDefaultPositionInput {
  placement: FloatingControlPlacement
  anchorRect?: FloatingAnchorRect | null
  controlSize: FloatingControlSize
  viewport: FloatingViewportSize
  gap: number
}

export interface ClampFloatingPositionInput {
  position: FloatingControlPosition
  controlSize: FloatingControlSize
  viewport: FloatingViewportSize
  margin?: number | Partial<Record<'top' | 'right' | 'bottom' | 'left', number>>
}

export interface ResolveFloatingControlPositionInput {
  defaultPosition: FloatingControlPosition
  controlSize: FloatingControlSize
  viewport: FloatingViewportSize
  offset?: FloatingControlOffset | null
  margin?: number | Partial<Record<'top' | 'right' | 'bottom' | 'left', number>>
}

const DEFAULT_MARGIN = 8

function normalizeDimension(value: number): number {
  return Number.isFinite(value) ? Math.max(0, value) : 0
}

function clamp(value: number, min: number, max: number): number {
  if (max < min) return min
  return Math.min(Math.max(value, min), max)
}

function normalizeMargin(
  margin: number | Partial<Record<'top' | 'right' | 'bottom' | 'left', number>> | undefined
): Record<'top' | 'right' | 'bottom' | 'left', number> {
  if (typeof margin === 'number') {
    const normalized = Number.isFinite(margin) ? Math.max(0, Number(margin)) : DEFAULT_MARGIN
    return {
      top: normalized,
      right: normalized,
      bottom: normalized,
      left: normalized
    }
  }

  return {
    top: Number.isFinite(margin?.top) ? Math.max(0, Number(margin?.top)) : DEFAULT_MARGIN,
    right: Number.isFinite(margin?.right) ? Math.max(0, Number(margin?.right)) : DEFAULT_MARGIN,
    bottom: Number.isFinite(margin?.bottom) ? Math.max(0, Number(margin?.bottom)) : DEFAULT_MARGIN,
    left: Number.isFinite(margin?.left) ? Math.max(0, Number(margin?.left)) : DEFAULT_MARGIN
  }
}

export function clampFloatingPosition(input: ClampFloatingPositionInput): FloatingControlPosition {
  const margin = normalizeMargin(input.margin)
  const viewportWidth = normalizeDimension(input.viewport.width)
  const viewportHeight = normalizeDimension(input.viewport.height)
  const controlWidth = normalizeDimension(input.controlSize.width)
  const controlHeight = normalizeDimension(input.controlSize.height)
  const maxX = Math.max(margin.left, viewportWidth - controlWidth - margin.right)
  const maxY = Math.max(margin.top, viewportHeight - controlHeight - margin.bottom)

  return {
    x: clamp(input.position.x, margin.left, maxX),
    y: clamp(input.position.y, margin.top, maxY)
  }
}

export function computeFloatingDefaultPosition(
  input: ComputeFloatingDefaultPositionInput
): FloatingControlPosition {
  const viewportWidth = normalizeDimension(input.viewport.width)
  const viewportHeight = normalizeDimension(input.viewport.height)
  const controlWidth = normalizeDimension(input.controlSize.width)
  const controlHeight = normalizeDimension(input.controlSize.height)
  const gap = Number.isFinite(input.gap) ? Math.max(0, Number(input.gap)) : 0
  const centeredSidebarY = (viewportHeight - controlHeight) / 2
  const anchorRect = input.anchorRect ?? null

  if (anchorRect) {
    switch (input.placement) {
      case 'left-sidebar':
        return {
          x: anchorRect.left - controlWidth - gap,
          y: centeredSidebarY
        }
      case 'right-sidebar':
        return {
          x: anchorRect.right + gap,
          y: centeredSidebarY
        }
      case 'toolbar':
        return {
          x: anchorRect.left + (anchorRect.width - controlWidth) / 2,
          y: anchorRect.bottom + gap
        }
    }
  }

  switch (input.placement) {
    case 'left-sidebar':
      return {
        x: gap,
        y: centeredSidebarY
      }
    case 'right-sidebar':
      return {
        x: viewportWidth - controlWidth - gap,
        y: centeredSidebarY
      }
    case 'toolbar':
      return {
        x: (viewportWidth - controlWidth) / 2,
        y: viewportHeight - controlHeight - gap
      }
  }
}

export function resolveFloatingControlPosition(
  input: ResolveFloatingControlPositionInput
): FloatingControlPosition {
  const offset = input.offset ?? { dx: 0, dy: 0 }
  return clampFloatingPosition({
    position: {
      x: input.defaultPosition.x + offset.dx,
      y: input.defaultPosition.y + offset.dy
    },
    controlSize: input.controlSize,
    viewport: input.viewport,
    margin: input.margin
  })
}

export function toFloatingControlOffset(
  position: FloatingControlPosition,
  defaultPosition: FloatingControlPosition
): FloatingControlOffset {
  return {
    dx: Math.round(position.x - defaultPosition.x),
    dy: Math.round(position.y - defaultPosition.y)
  }
}
