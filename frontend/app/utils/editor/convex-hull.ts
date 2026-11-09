import { polygonHull } from 'd3-polygon'

/** Keep the editor's winding and smallest x/y starting vertex. */
export function computeConvexHull(points: [number, number][]): [number, number][] {
  const hull = polygonHull(points)
  if (!hull) return points

  hull.reverse()
  let firstIndex = 0
  for (let index = 1; index < hull.length; index++) {
    const point = hull[index]!
    const first = hull[firstIndex]!
    if (point[0] < first[0] || (point[0] === first[0] && point[1] < first[1])) {
      firstIndex = index
    }
  }
  return [...hull.slice(firstIndex), ...hull.slice(0, firstIndex)]
}
