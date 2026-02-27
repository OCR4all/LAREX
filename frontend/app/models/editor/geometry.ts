/** Geometry types */
export class Polygon {
  points: [number, number][]

  constructor(points: [number, number][]) {
    this.points = points
  }

  getBoundingBox(): { minX: number, minY: number, maxX: number, maxY: number } {
    if (this.points.length === 0) {
      return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
    }

    const first = this.points[0]
    if (!first) return { minX: 0, minY: 0, maxX: 0, maxY: 0 }

    let minX = first[0]
    let minY = first[1]
    let maxX = first[0]
    let maxY = first[1]

    for (const [x, y] of this.points) {
      minX = Math.min(minX, x)
      minY = Math.min(minY, y)
      maxX = Math.max(maxX, x)
      maxY = Math.max(maxY, y)
    }

    return { minX, minY, maxX, maxY }
  }

  /** Alias for getBoundingBox() for backward compatibility */
  getBbox(): { minX: number, minY: number, maxX: number, maxY: number } {
    return this.getBoundingBox()
  }

  /** Gets triangulated vertex data suitable for WebGL rendering */
  getWebGLVertices(): number[] {
    if (this.points.length < 3) return []

    const triangles: number[] = []
    const first = this.points[0]
    if (!first) return []

    for (let i = 1; i < this.points.length - 1; i++) {
      const curr = this.points[i]
      const next = this.points[i + 1]
      if (!curr || !next) continue

      triangles.push(first[0], first[1])
      triangles.push(curr[0], curr[1])
      triangles.push(next[0], next[1])
    }

    return triangles
  }

  getArea(): number {
    if (this.points.length < 3) return 0

    let area = 0
    const n = this.points.length

    for (let i = 0; i < n; i++) {
      const j = (i + 1) % n
      const pi = this.points[i]!
      const pj = this.points[j]!
      area += pi[0] * pj[1]
      area -= pj[0] * pi[1]
    }

    return Math.abs(area / 2)
  }

  getCentroid(): [number, number] {
    if (this.points.length === 0) return [0, 0]

    let sumX = 0
    let sumY = 0

    for (const [x, y] of this.points) {
      sumX += x
      sumY += y
    }

    return [sumX / this.points.length, sumY / this.points.length]
  }

  containsPoint(x: number, y: number): boolean {
    let inside = false
    const n = this.points.length

    for (let i = 0, j = n - 1; i < n; j = i++) {
      const pi = this.points[i]
      const pj = this.points[j]
      if (!pi || !pj) continue

      const xi = pi[0]
      const yi = pi[1]
      const xj = pj[0]
      const yj = pj[1]

      const intersect = ((yi > y) !== (yj > y))
        && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)

      if (intersect) inside = !inside
    }

    return inside
  }

  intersects(other: Polygon): boolean {
    for (let i = 0; i < this.points.length; i++) {
      const j = (i + 1) % this.points.length
      const a1 = this.points[i]
      const a2 = this.points[j]
      if (!a1 || !a2) continue

      for (let k = 0; k < other.points.length; k++) {
        const l = (k + 1) % other.points.length
        const b1 = other.points[k]
        const b2 = other.points[l]
        if (!b1 || !b2) continue

        if (this.segmentsIntersect(a1, a2, b1, b2)) {
          return true
        }
      }
    }

    const otherFirst = other.points[0]
    const thisFirst = this.points[0]
    if (!otherFirst || !thisFirst) return false

    return this.containsPoint(otherFirst[0], otherFirst[1])
      || other.containsPoint(thisFirst[0], thisFirst[1])
  }

  private segmentsIntersect(
    p1: [number, number],
    p2: [number, number],
    p3: [number, number],
    p4: [number, number]
  ): boolean {
    const ccw = (a: [number, number], b: [number, number], c: [number, number]) => {
      return (c[1] - a[1]) * (b[0] - a[0]) > (b[1] - a[1]) * (c[0] - a[0])
    }

    return ccw(p1, p3, p4) !== ccw(p2, p3, p4) && ccw(p1, p2, p3) !== ccw(p1, p2, p4)
  }

  union(other: Polygon): Polygon[] {
    if (!this.intersects(other)) {
      return [this, other]
    }

    return this.weilerAthertonUnion(other)
  }

  intersection(other: Polygon): Polygon | null {
    let outputList = [...this.points]

    for (let i = 0; i < other.points.length; i++) {
      const j = (i + 1) % other.points.length
      const e1 = other.points[i]
      const e2 = other.points[j]
      if (!e1 || !e2) continue

      const edge = [e1, e2] as [[number, number], [number, number]]
      const inputList = outputList
      outputList = []

      if (inputList.length === 0) break

      for (let k = 0; k < inputList.length; k++) {
        const current = inputList[k]
        const previous = inputList[(k + inputList.length - 1) % inputList.length]
        if (!current || !previous) continue

        const currentInside = this.isPointInsideEdge(current, edge)
        const previousInside = this.isPointInsideEdge(previous, edge)

        if (currentInside) {
          if (!previousInside) {
            const intersection = this.lineIntersection(previous, current, edge[0], edge[1])
            if (intersection) outputList.push(intersection)
          }
          outputList.push(current)
        } else if (previousInside) {
          const intersection = this.lineIntersection(previous, current, edge[0], edge[1])
          if (intersection) outputList.push(intersection)
        }
      }
    }

    return outputList.length >= 3 ? new Polygon(outputList) : null
  }

  difference(other: Polygon): Polygon[] {
    if (!this.intersects(other)) {
      return [this]
    }

    return this.weilerAthertonDifference(other)
  }

  private isPointInsideEdge(point: [number, number], edge: [[number, number], [number, number]]): boolean {
    const [p1, p2] = edge
    return (p2[0] - p1[0]) * (point[1] - p1[1]) - (p2[1] - p1[1]) * (point[0] - p1[0]) >= 0
  }

  private lineIntersection(
    p1: [number, number],
    p2: [number, number],
    p3: [number, number],
    p4: [number, number]
  ): [number, number] | null {
    const x1 = p1[0], y1 = p1[1]
    const x2 = p2[0], y2 = p2[1]
    const x3 = p3[0], y3 = p3[1]
    const x4 = p4[0], y4 = p4[1]

    const denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if (Math.abs(denom) < 1e-10) return null

    const t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom

    return [x1 + t * (x2 - x1), y1 + t * (y2 - y1)]
  }

  private weilerAthertonUnion(other: Polygon): Polygon[] {
    const allPoints = [...this.points, ...other.points]
    return [this.convexHull(allPoints)]
  }

  private weilerAthertonDifference(other: Polygon): Polygon[] {
    const result = this.intersection(other)
    if (!result) return [this]

    return [this]
  }

  private convexHull(points: [number, number][]): Polygon {
    if (points.length < 3) return new Polygon(points)

    const sorted = [...points].sort((a, b) => a[0] === b[0] ? a[1] - b[1] : a[0] - b[0])
    const cross = (o: [number, number], a: [number, number], b: [number, number]) => {
      return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    }

    const lower: [number, number][] = []
    for (const p of sorted) {
      while (lower.length >= 2 && cross(lower[lower.length - 2]!, lower[lower.length - 1]!, p) <= 0) {
        lower.pop()
      }
      lower.push(p)
    }

    const upper: [number, number][] = []
    for (let i = sorted.length - 1; i >= 0; i--) {
      const p = sorted[i]
      if (!p) continue
      while (upper.length >= 2 && cross(upper[upper.length - 2]!, upper[upper.length - 1]!, p) <= 0) {
        upper.pop()
      }
      upper.push(p)
    }

    lower.pop()
    upper.pop()
    return new Polygon([...lower, ...upper])
  }

  buffer(distance: number): Polygon {
    const offsetPoints: [number, number][] = []
    const n = this.points.length
    if (n === 0) return new Polygon([])

    for (let i = 0; i < n; i++) {
      const prev = this.points[(i - 1 + n) % n]!
      const curr = this.points[i]!
      const next = this.points[(i + 1) % n]!

      const v1 = this.normalize([curr[0] - prev[0], curr[1] - prev[1]])
      const v2 = this.normalize([next[0] - curr[0], next[1] - curr[1]])

      const n1: [number, number] = [-v1[1], v1[0]]
      const n2: [number, number] = [-v2[1], v2[0]]

      const avgNormal: [number, number] = [
        (n1[0] + n2[0]) / 2,
        (n1[1] + n2[1]) / 2
      ]

      const normalizedAvg = this.normalize(avgNormal)

      offsetPoints.push([
        curr[0] + normalizedAvg[0] * distance,
        curr[1] + normalizedAvg[1] * distance
      ])
    }

    return new Polygon(offsetPoints)
  }

  private normalize(v: [number, number]): [number, number] {
    const len = Math.sqrt(v[0] * v[0] + v[1] * v[1])
    return len > 0 ? [v[0] / len, v[1] / len] : [0, 0]
  }

  simplify(tolerance: number): Polygon {
    if (this.points.length <= 3) return this

    const simplified = this.ramerDouglasPeucker(this.points, tolerance)
    return new Polygon(simplified.length >= 3 ? simplified : this.points)
  }

  private ramerDouglasPeucker(points: [number, number][], epsilon: number): [number, number][] {
    if (points.length <= 2) return points

    let maxDist = 0
    let maxIndex = 0
    const end = points.length - 1

    for (let i = 1; i < end; i++) {
      const dist = this.perpendicularDistance(points[i]!, points[0]!, points[end]!)
      if (dist > maxDist) {
        maxDist = dist
        maxIndex = i
      }
    }

    if (maxDist > epsilon) {
      const left = this.ramerDouglasPeucker(points.slice(0, maxIndex + 1), epsilon)
      const right = this.ramerDouglasPeucker(points.slice(maxIndex), epsilon)
      return [...left.slice(0, -1), ...right]
    }

    return [points[0]!, points[end]!]
  }

  private perpendicularDistance(
    point: [number, number],
    lineStart: [number, number],
    lineEnd: [number, number]
  ): number {
    const dx = lineEnd[0] - lineStart[0]
    const dy = lineEnd[1] - lineStart[1]
    const norm = Math.sqrt(dx * dx + dy * dy)

    if (norm === 0) {
      return Math.sqrt(
        Math.pow(point[0] - lineStart[0], 2)
        + Math.pow(point[1] - lineStart[1], 2)
      )
    }

    return Math.abs(
      dy * point[0] - dx * point[1] + lineEnd[0] * lineStart[1] - lineEnd[1] * lineStart[0]
    ) / norm
  }
}

export class Polyline {
  points: [number, number][]

  constructor(points: [number, number][]) {
    this.points = points
  }

  getLength(): number {
    let length = 0

    for (let i = 0; i < this.points.length - 1; i++) {
      const p1 = this.points[i]
      const p2 = this.points[i + 1]
      if (!p1 || !p2) continue
      const [x1, y1] = p1
      const [x2, y2] = p2
      length += Math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2)
    }

    return length
  }

  getBoundingBox(): { minX: number, minY: number, maxX: number, maxY: number } {
    if (this.points.length === 0) {
      return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
    }

    const first = this.points[0]
    if (!first) return { minX: 0, minY: 0, maxX: 0, maxY: 0 }

    let minX = first[0]
    let minY = first[1]
    let maxX = first[0]
    let maxY = first[1]

    for (const [x, y] of this.points) {
      minX = Math.min(minX, x)
      minY = Math.min(minY, y)
      maxX = Math.max(maxX, x)
      maxY = Math.max(maxY, y)
    }

    return { minX, minY, maxX, maxY }
  }

  /** Alias for getBoundingBox() for backward compatibility */
  getBbox(): { minX: number, minY: number, maxX: number, maxY: number } {
    return this.getBoundingBox()
  }

  /** Gets vertex data suitable for WebGL rendering using LINE_STRIP */
  getWebGLVertices(): number[] {
    const flatVertices: number[] = []
    for (const [x, y] of this.points) {
      flatVertices.push(x, y)
    }
    return flatVertices
  }

  getPointAt(distance: number): [number, number] | null {
    if (this.points.length < 2 || distance < 0) return null

    let totalDistance = 0

    for (let i = 0; i < this.points.length - 1; i++) {
      const p1 = this.points[i]
      const p2 = this.points[i + 1]
      if (!p1 || !p2) continue
      const [x1, y1] = p1
      const [x2, y2] = p2
      const segmentLength = Math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2)

      if (totalDistance + segmentLength >= distance) {
        const ratio = (distance - totalDistance) / segmentLength
        return [
          x1 + (x2 - x1) * ratio,
          y1 + (y2 - y1) * ratio
        ]
      }

      totalDistance += segmentLength
    }

    return this.points[this.points.length - 1] ?? null
  }
}
