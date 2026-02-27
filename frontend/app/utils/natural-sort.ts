/**
 * Natural sort comparator that handles numeric strings intelligently.
 * E.g., "page_2" comes before "page_10" (unlike lexicographic sort).
 *
 * @param a - First string to compare
 * @param b - Second string to compare
 * @returns Negative if a < b, positive if a > b, 0 if equal
 */
export function naturalCompare(a: string, b: string): number {
  const collator = new Intl.Collator(undefined, {
    numeric: true,
    sensitivity: 'base'
  })
  return collator.compare(a, b)
}

/**
 * Sorts an array of objects by a string property using natural sort order.
 *
 * @param items - Array of objects to sort
 * @param key - Property key to sort by
 * @returns New sorted array (does not mutate original)
 */
export function naturalSortBy<T>(items: T[], key: keyof T): T[] {
  return [...items].sort((a, b) => {
    const aVal = String(a[key] ?? '')
    const bVal = String(b[key] ?? '')
    return naturalCompare(aVal, bVal)
  })
}
