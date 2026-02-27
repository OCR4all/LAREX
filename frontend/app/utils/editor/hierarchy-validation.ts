/**
 * Hierarchy validation utilities for enforcing parent-child relationship rules.
 *
 * Rules:
 * - A region can contain other regions as well as textlines
 * - A textline can only contain exactly one baseline
 * - A baseline always belongs to exactly one textline (and can't contain anything else)
 */

import { PolygonType } from '@/models/editor'

export interface HierarchyItem {
  id: string
  type: string
  parentId?: string | null
}

export interface PolylineItem {
  id: string
  parentId?: string | null
  parentPolygonId?: string | null
}

export interface ValidationResult {
  valid: boolean
  error?: string
}

/**
 * Check if a parent-child relationship is valid based on types.
 */
export function isValidParentChildType(parentType: string, childType: string): boolean {
  if (parentType === PolygonType.REGION) {
    return childType === PolygonType.REGION || childType === PolygonType.TEXTLINE
  }

  if (parentType === PolygonType.TEXTLINE) {
    return childType === PolygonType.BASELINE
  }

  if (parentType === PolygonType.BASELINE) {
    return false
  }

  return false
}

/**
 * Validate that a polygon can be a child of another polygon.
 */
export function validatePolygonParent(
  childType: string,
  parentId: string | null | undefined,
  allPolygons: HierarchyItem[]
): ValidationResult {
  if (childType === PolygonType.TEXTLINE && !parentId) {
    return {
      valid: false,
      error: 'A textline must belong to a region. Please select a region first.'
    }
  }

  if (childType === PolygonType.BASELINE && !parentId) {
    return {
      valid: false,
      error: 'A baseline must belong to a textline. Please select a textline first.'
    }
  }

  if (!parentId) {
    return { valid: true }
  }

  const parent = allPolygons.find(p => p.id === parentId)
  if (!parent) {
    return {
      valid: false,
      error: `Parent polygon with ID "${parentId}" not found`
    }
  }

  if (!isValidParentChildType(parent.type, childType)) {
    return {
      valid: false,
      error: `A ${parent.type} cannot contain a ${childType}`
    }
  }

  return { valid: true }
}

/**
 * Validate that a polyline (baseline) can be a child of a polygon (textline).
 */
export function validatePolylineParent(
  parentPolygonId: string | null | undefined,
  allPolygons: HierarchyItem[]
): ValidationResult {
  if (!parentPolygonId) {
    return {
      valid: false,
      error: 'A baseline must belong to a textline'
    }
  }

  const parent = allPolygons.find(p => p.id === parentPolygonId)
  if (!parent) {
    return {
      valid: false,
      error: `Parent textline with ID "${parentPolygonId}" not found`
    }
  }

  if (parent.type !== PolygonType.TEXTLINE) {
    return {
      valid: false,
      error: `A baseline can only belong to a textline, not a ${parent.type}`
    }
  }

  return { valid: true }
}

/**
 * Check if a textline already has a baseline.
 */
export function textlineHasBaseline(
  textlineId: string,
  allPolylines: PolylineItem[]
): boolean {
  return allPolylines.some(pl => (pl.parentId === textlineId || pl.parentPolygonId === textlineId))
}

/**
 * Validate that adding a baseline to a textline is allowed.
 */
export function validateBaselineForTextline(
  textlineId: string,
  allPolylines: PolylineItem[],
  excludeBaselineId?: string
): ValidationResult {
  const existingBaseline = allPolylines.find(
    pl => (pl.parentId === textlineId || pl.parentPolygonId === textlineId) && pl.id !== excludeBaselineId
  )

  if (existingBaseline) {
    return {
      valid: false,
      error: `Textline already has a baseline (${existingBaseline.id}). A textline can only contain exactly one baseline.`
    }
  }

  return { valid: true }
}

/**
 * Validate that a polygon type change is allowed.
 */
export function validatePolygonTypeChange(
  polygonId: string,
  newType: string,
  allPolygons: HierarchyItem[],
  allPolylines: PolylineItem[]
): ValidationResult {
  const polygon = allPolygons.find(p => p.id === polygonId)
  if (!polygon) {
    return { valid: false, error: 'Polygon not found' }
  }

  const children = allPolygons.filter(p => p.parentId === polygonId)
  const polylineChildren = allPolylines.filter(pl => pl.parentId === polygonId || pl.parentPolygonId === polygonId)

  if (newType === PolygonType.BASELINE) {
    if (children.length > 0 || polylineChildren.length > 0) {
      return {
        valid: false,
        error: 'Cannot change to baseline type: baselines cannot contain other elements'
      }
    }
  }

  if (polygon.type === PolygonType.TEXTLINE && newType !== PolygonType.TEXTLINE) {
    if (polylineChildren.length > 0) {
      return {
        valid: false,
        error: 'Cannot change type: textline contains baselines that would become orphaned'
      }
    }
  }

  if (newType === PolygonType.TEXTLINE) {
    if (children.length > 0) {
      return {
        valid: false,
        error: 'Cannot change to textline: it contains polygon children. Textlines can only contain baselines.'
      }
    }

    if (polylineChildren.length > 1) {
      return {
        valid: false,
        error: 'Cannot change to textline: it contains multiple baselines. A textline can only contain exactly one baseline.'
      }
    }
  }

  if (newType === PolygonType.REGION) {
    for (const child of children) {
      if (child.type !== PolygonType.REGION && child.type !== PolygonType.TEXTLINE) {
        return {
          valid: false,
          error: `Cannot change to region: it contains a ${child.type} child. Regions can only contain regions or textlines.`
        }
      }
    }

    if (polylineChildren.length > 0) {
      return {
        valid: false,
        error: 'Cannot change to region: it contains baselines. Regions cannot directly contain baselines.'
      }
    }
  }

  if (polygon.parentId) {
    const parentValidation = validatePolygonParent(newType, polygon.parentId, allPolygons)
    if (!parentValidation.valid) {
      return parentValidation
    }
  }

  return { valid: true }
}

/**
 * Get a human-readable description of valid parent types for a given child type.
 */
export function getValidParentTypesDescription(childType: string): string {
  if (childType === PolygonType.REGION) {
    return 'regions'
  }

  if (childType === PolygonType.TEXTLINE) {
    return 'regions'
  }

  if (childType === PolygonType.BASELINE) {
    return 'textlines'
  }

  return 'none'
}

/**
 * Get a human-readable description of valid child types for a given parent type.
 */
export function getValidChildTypesDescription(parentType: string): string {
  if (parentType === PolygonType.REGION) {
    return 'regions and textlines'
  }

  if (parentType === PolygonType.TEXTLINE) {
    return 'baselines only'
  }

  if (parentType === PolygonType.BASELINE) {
    return 'none (baselines cannot contain other elements)'
  }

  return 'none'
}
