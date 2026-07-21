export type { Command, CommandContext } from './types'

export { Polygon, Polyline } from '@/models/editor/geometry'

export { Commander, commander } from './commander'

export { CreatePolygonCommand, type CreatePolygonCommandData } from './create-polygon-command'
export { UpdatePolygonCommand, type UpdatePolygonCommandData } from './update-polygon-command'
export { DeletePolygonCommand, type DeletePolygonCommandData } from './delete-polygon-command'

export { CreatePolylineCommand, type CreatePolylineCommandData } from './create-polyline-command'
export { DeletePolylineCommand, type DeletePolylineCommandData } from './delete-polyline-command'
export { UpdatePolylineCommand, type UpdatePolylineCommandData } from './update-polyline-command'

export { UpdateTextContentVariantsCommand, type UpdateTextContentVariantsCommandData } from './update-text-content-variants-command'
export { UpdateTextlineCommentCommand, type UpdateTextlineCommentCommandData } from './update-textline-comment-command'

export { ChangeRegionLabelCommand, type ChangeRegionLabelCommandParams } from './change-region-label-command'

export { ChangeRegionKindCommand, type ChangeRegionKindCommandParams } from './change-region-kind-command'

export { SetHiddenElementsCommand, type SetHiddenElementsCommandData, type HiddenAction } from './set-hidden-elements-command'

export { DeleteSelectedElementsCommand, type DeleteSelectedElementsCommandData } from './delete-selected-elements-command'

export { CreateRelationCommand, type CreateRelationCommandData } from './create-relation-command'
export { UpdateRelationCommand, type UpdateRelationCommandData } from './update-relation-command'
export { DeleteRelationCommand, type DeleteRelationCommandData } from './delete-relation-command'

export { CompoundCommand, CompoundCommandBuilder } from './compound-command'

export { CutElementsCommand, type CutElementsCommandData, type CutMode } from './cut-elements-command'

export { DuplicateElementCommand, type DuplicateElementCommandData } from './duplicate-element-command'

export { MoveElementCommand, type MoveElementCommandData } from './move-element-command'

export { MergeElementsCommand, type MergeElementsCommandData } from './merge-elements-command'

export {
  CreateTextlineAutoParentCommand,
  type CreateTextlineAutoParentCommandData,
  type CreateTextlineAutoParentResult
} from './create-textline-auto-parent-command'
export {
  CreateBaselineAutoParentCommand,
  type CreateBaselineAutoParentCommandData,
  type CreateBaselineAutoParentResult
} from './create-baseline-auto-parent-command'

export { SimplifyPolygonCommand, type SimplifyPolygonCommandData } from './simplify-polygon-command'
export { BufferPolygonCommand, type BufferPolygonCommandData } from './buffer-polygon-command'
export { FitToBoundingBoxCommand, type FitToBoundingBoxCommandData } from './fit-to-bounding-box-command'
export { ConvexHullCommand, type ConvexHullCommandData } from './convex-hull-command'
export { ReparentElementCommand, type ReparentElementCommandData } from './reparent-element-command'
export { ReorderTextLinesCommand, type ReorderTextLinesCommandData } from './reorder-textlines-command'
export { UpdateReadingOrderCommand, type UpdateReadingOrderCommandData } from './update-reading-order-command'

export {
  deepClone,
  toPlainPoint,
  toPlainPoints,
  pointToVertex,
  vertexToPoint,
  pointsToVertices,
  verticesToPoints,
  createPolygonFromPoints,
  createPolylineFromPoints,
  getPointsFromPolygon,
  getPointsFromPolyline
} from './utils'
