export type ToolbarLayoutResolved = 'floating' | 'docked-top'
export type ToolbarShapeEntry = 'region' | 'textline' | 'baseline'
export type ToolbarShapeOption = 'polygon' | 'rectangle' | 'polyline'

export type ToolbarApi = {
  canvasId: ComputedRef<string | null>
  isDockedTop: ComputedRef<boolean>

  effectiveUiMode: ComputedRef<'layout' | 'text'>
  setEffectiveUiMode: (mode: 'layout' | 'text') => void
  perPanelUiModeModel: ComputedRef<boolean>

  hasActiveCanvas: ComputedRef<boolean>

  isSelectMode: ComputedRef<boolean>
  handleToggleSelectMode: () => void

  isRegionTypeRegion: ComputedRef<boolean>
  isRegionTypeTextline: ComputedRef<boolean>
  isRegionTypeBaseline: ComputedRef<boolean>

  isPolygonMode: ComputedRef<boolean>
  isRectangleMode: ComputedRef<boolean>
  isPolylineMode: ComputedRef<boolean>

  canCreateRegion: ComputedRef<boolean>
  canCreateTextline: ComputedRef<boolean>
  canCreateBaseline: ComputedRef<boolean>

  getPrimaryShapeForEntry: (entry: ToolbarShapeEntry) => ToolbarShapeOption
  setEntryAndMode: (entry: ToolbarShapeEntry, option?: ToolbarShapeOption) => void

  isViewDefault: ComputedRef<boolean>
  isViewTextline: ComputedRef<boolean>
  isViewBaseline: ComputedRef<boolean>
  setViewModeDefault: () => void
  setViewModeTextline: () => void
  setViewModeBaseline: () => void

  canUndo: ComputedRef<boolean>
  canRedo: ComputedRef<boolean>
  handleUndo: () => void
  handleRedo: () => void
  updateHistoryItems: () => void
  historyDropdownItems: ComputedRef<Array<{ label: string, disabled?: boolean, onSelect?: () => void }>>

  selectionInfo: ComputedRef<string>

  constrainToImageModel: ComputedRef<boolean>
  constrainToParentModel: ComputedRef<boolean>
  autoSelectModel: ComputedRef<boolean>

  toggleToolbarLayout: () => void

  handleSaveDocument: () => void
}

const ToolbarKey: InjectionKey<ToolbarApi> = Symbol('Toolbar')

export function provideToolbar(api: ToolbarApi) {
  provide(ToolbarKey, api)
}

export function useToolbar(): ToolbarApi {
  const api = inject(ToolbarKey, null)
  if (!api) {
    throw new Error('Toolbar is missing: make sure <EditorToolbar> is an ancestor.')
  }
  return api
}
