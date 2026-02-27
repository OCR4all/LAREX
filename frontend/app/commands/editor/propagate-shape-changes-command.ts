import type { Command } from './types'
import { toPlainPoints, deepClone } from './utils'

type PlainPoint = { x: number, y: number }

type AffectedOperation = 'clip' | 'delete' | 'none'
type AffectedType = 'polygon' | 'polyline'

interface AffectedElement {
  type: AffectedType
  operation: AffectedOperation
  index: number
  id?: string
  polygon?: PlainPoint[]
  metadata?: unknown
}

interface PropagateShapeChangesCommandData {
  modifiedPolygonId: string
  originalPolygonPoints: PlainPoint[]
  polygons: Array<{ id: string, points: PlainPoint[], [key: string]: unknown }>
  polylines: Array<{ id: string, points: PlainPoint[], [key: string]: unknown }>
  affectedElements: AffectedElement[]
}

function replaceArrayContents<T>(target: T[], next: T[]): void {
  target.splice(0, target.length, ...next)
}

function distance(point1: PlainPoint, point2: PlainPoint): number {
  const dx = point2.x - point1.x
  const dy = point2.y - point1.y
  return Math.sqrt(dx * dx + dy * dy)
}

/**
 * Command for propagating shape changes to child elements when a parent polygon is modified.
 * This command handles clipping or deleting child polygons that are affected by parent boundary changes.
 */
export class PropagateShapeChangesCommand implements Command {
  private readonly targetPolygons: PropagateShapeChangesCommandData['polygons']
  private readonly targetPolylines: PropagateShapeChangesCommandData['polylines']

  private readonly beforePolygons: PropagateShapeChangesCommandData['polygons']
  private readonly beforePolylines: PropagateShapeChangesCommandData['polylines']
  private afterPolygons: PropagateShapeChangesCommandData['polygons'] | null = null
  private afterPolylines: PropagateShapeChangesCommandData['polylines'] | null = null

  private modifiedPolygonId
  private originalPolygonPoints

  private polygonChanges: Array<{ index: number, originalPoints: PlainPoint[], newPoints: PlainPoint[], polygonId?: string, metadata?: unknown }> = []
  private polylineChanges: Array<{ index: number, originalPoints: PlainPoint[], newPoints: PlainPoint[], polylineId?: string, metadata?: unknown }> = []
  private deletedPolygonIndices: number[] = []
  private deletedPolylineIndices: number[] = []

  constructor(data: PropagateShapeChangesCommandData) {
    this.modifiedPolygonId = data.modifiedPolygonId
    this.originalPolygonPoints = data.originalPolygonPoints

    this.targetPolygons = data.polygons
    this.targetPolylines = data.polylines

    this.beforePolygons = deepClone(data.polygons)
    this.beforePolylines = deepClone(data.polylines)

    this.processChanges(data)
  }

  /**
   * Process the propagation changes and categorize them
   */
  processChanges(data: PropagateShapeChangesCommandData) {
    const { polygons, polylines, affectedElements } = data

    const affectedPolygons = affectedElements.filter(el => el.type === 'polygon')
    const affectedPolylines = affectedElements.filter(el => el.type === 'polyline')

    for (const element of affectedPolygons) {
      const polygonIndex = element.index
      const originalPolygon = polygons[polygonIndex]

      if (!originalPolygon) continue

      if (element.operation === 'clip' && element.polygon) {
        this.polygonChanges.push({
          index: polygonIndex,
          originalPoints: toPlainPoints(originalPolygon.points),
          newPoints: toPlainPoints(element.polygon),
          polygonId: element.id,
          metadata: element.metadata
        })
      } else if (element.operation === 'delete') {
        this.deletedPolygonIndices.push(polygonIndex)
      }
    }

    for (const element of affectedPolylines) {
      const polylineIndex = element.index
      const originalPolyline = polylines[polylineIndex]

      if (!originalPolyline) continue

      if (element.operation === 'clip' && element.polygon) {
        const clippedPoints = this.extractPolylineFromClippedPolygon(element.polygon, originalPolyline.points)

        this.polylineChanges.push({
          index: polylineIndex,
          originalPoints: toPlainPoints(originalPolyline.points),
          newPoints: clippedPoints,
          polylineId: element.id,
          metadata: element.metadata
        })
      } else if (element.operation === 'delete') {
        this.deletedPolylineIndices.push(polylineIndex)
      }
    }

    this.deletedPolygonIndices.sort((a, b) => b - a)
    this.deletedPolylineIndices.sort((a, b) => b - a)
  }

  /**
   * Extract a polyline from a clipped polygon for baseline operations
   */
  extractPolylineFromClippedPolygon(clippedPolygon: PlainPoint[], originalPolylinePoints: PlainPoint[]): PlainPoint[] {
    if (!clippedPolygon || clippedPolygon.length < 2) {
      return originalPolylinePoints // Return original if clipping failed
    }

    const first = clippedPolygon[0]
    const second = clippedPolygon[1]
    if (!first || !second) return originalPolylinePoints

    let maxDistance = 0
    let startPoint: PlainPoint = first
    let endPoint: PlainPoint = second

    for (let i = 0; i < clippedPolygon.length; i++) {
      for (let j = i + 1; j < clippedPolygon.length; j++) {
        const a = clippedPolygon[i]
        const b = clippedPolygon[j]
        if (!a || !b) continue

        const dist = distance(a, b)
        if (dist > maxDistance) {
          maxDistance = dist
          startPoint = a
          endPoint = b
        }
      }
    }

    const originalStart = originalPolylinePoints[0]
    const originalEnd = originalPolylinePoints[originalPolylinePoints.length - 1]

    if (!originalStart || !originalEnd) {
      return [startPoint, endPoint]
    }

    const distToStartStart = distance(originalStart, startPoint)
    const distToStartEnd = distance(originalStart, endPoint)

    if (distToStartStart <= distToStartEnd) {
      return [startPoint, endPoint]
    } else {
      return [endPoint, startPoint]
    }
  }

  private buildAfterSnapshots(): void {
    if (this.afterPolygons && this.afterPolylines) return

    const nextPolygons = deepClone(this.beforePolygons)
    const nextPolylines = deepClone(this.beforePolylines)

    for (const change of this.polygonChanges) {
      if (nextPolygons[change.index]) {
        nextPolygons[change.index]!.points = toPlainPoints(change.newPoints)
      }
    }

    for (const change of this.polylineChanges) {
      if (nextPolylines[change.index]) {
        nextPolylines[change.index]!.points = toPlainPoints(change.newPoints)
      }
    }

    for (const index of this.deletedPolygonIndices) {
      if (index >= 0 && index < nextPolygons.length) {
        nextPolygons.splice(index, 1)
      }
    }

    for (const index of this.deletedPolylineIndices) {
      if (index >= 0 && index < nextPolylines.length) {
        nextPolylines.splice(index, 1)
      }
    }

    this.afterPolygons = nextPolygons
    this.afterPolylines = nextPolylines
  }

  /**
   * Execute the propagation changes
   */
  execute() {
    this.buildAfterSnapshots()
    replaceArrayContents(this.targetPolygons, this.afterPolygons ?? [])
    replaceArrayContents(this.targetPolylines, this.afterPolylines ?? [])

    return {
      success: true,
      changedPolygons: this.polygonChanges.length,
      changedPolylines: this.polylineChanges.length,
      deletedPolygons: this.deletedPolygonIndices.length,
      deletedPolylines: this.deletedPolylineIndices.length
    }
  }

  /**
   * Undo the propagation changes
   */
  undo() {
    replaceArrayContents(this.targetPolygons, deepClone(this.beforePolygons))
    replaceArrayContents(this.targetPolylines, deepClone(this.beforePolylines))
  }

  /**
   * Get a human-readable description of the command
   */
  getDescription() {
    const totalChanges = this.polygonChanges.length + this.polylineChanges.length
    const totalDeletions = this.deletedPolygonIndices.length + this.deletedPolylineIndices.length

    let description = 'Propagate shape changes'

    if (totalChanges > 0 || totalDeletions > 0) {
      const parts = []
      if (this.polygonChanges.length > 0) {
        parts.push(`${this.polygonChanges.length} polygon${this.polygonChanges.length !== 1 ? 's' : ''}`)
      }
      if (this.polylineChanges.length > 0) {
        parts.push(`${this.polylineChanges.length} polyline${this.polylineChanges.length !== 1 ? 's' : ''}`)
      }
      if (totalDeletions > 0) {
        parts.push(`${totalDeletions} deletion${totalDeletions !== 1 ? 's' : ''}`)
      }
      description += ` (${parts.join(', ')})`
    }

    return description
  }

  /**
   * Get detailed information about the changes
   */
  getChangesSummary() {
    return {
      modifiedPolygonId: this.modifiedPolygonId,
      polygonChanges: this.polygonChanges.map(change => ({
        id: change.polygonId,
        metadata: change.metadata
      })),
      polylineChanges: this.polylineChanges.map(change => ({
        id: change.polylineId,
        metadata: change.metadata
      })),
      deletedPolygons: this.deletedPolygonIndices.length,
      deletedPolylines: this.deletedPolylineIndices.length
    }
  }
}
