import { driver, type Config, type Driver, type DriveStep, type PopoverDOM } from 'driver.js'
import {
  getTourDefinition,
  onboardingTours,
  resolveContextTourId,
  TOUR_IDS,
  type OnboardingDriveStep,
  type OnboardingMajor,
  type OnboardingContext,
  type TourId
} from './onboarding/tour-registry'
import { sleep } from './onboarding/tour-utils'

/**
 * Bump these versions to trigger major onboarding resets.
 */
const DASHBOARD_TOUR_VERSION = 2
const EDITOR_TOUR_VERSION = 2

/**
 * Map from CSS selector used in step definitions -> sidebar nav label text.
 * These selectors won't match the DOM directly (UNavigationMenu attr forwarding),
 * so we resolve them by text content lookup.
 */
const NAV_SELECTOR_TO_LABEL: Record<string, string> = {
  '[data-tour="nav-library"]': 'Projects',
  '[data-tour="nav-tasks"]': 'Tasks',
  '[data-tour="nav-utilities"]': 'Utilities',
  '[data-tour="nav-workspace"]': 'Workspace',
  '[data-tour="nav-settings"]': 'Settings'
}

function findSidebarRoot(): Element | null {
  const anchor = document.querySelector('[data-tour="search-button"]')
  return anchor?.closest('[data-collapsed]') ?? null
}

function findNavItemByLabel(sidebar: Element, label: string): Element | null {
  for (const el of sidebar.querySelectorAll('a, button')) {
    if (el.getAttribute('aria-label')?.trim() === label || el.getAttribute('title')?.trim() === label) {
      return (el.closest('li') as Element) ?? el
    }

    const spans = el.querySelectorAll('span')
    for (const span of spans) {
      if (span.children.length === 0 && span.textContent?.trim() === label) {
        return (el.closest('li') as Element) ?? el
      }
    }
  }
  return null
}

function resolveKnownSelectors(step: DriveStep): DriveStep {
  if (!step.element || typeof step.element !== 'string') return step

  const sidebar = findSidebarRoot()

  if (step.element === '#dashboard' && sidebar) {
    return { ...step, element: sidebar }
  }

  const label = NAV_SELECTOR_TO_LABEL[step.element]
  if (label && sidebar) {
    const el = findNavItemByLabel(sidebar, label)
    if (el) return { ...step, element: el }
  }

  return step
}

function normalizeRoutePath(path: string): string {
  const stripped = path.split('?')[0]?.split('#')[0] ?? path
  const withLeadingSlash = stripped.startsWith('/') ? stripped : `/${stripped}`
  if (withLeadingSlash.length <= 1) return '/'
  return withLeadingSlash.replace(/\/+$/, '')
}

function stepHasRenderableElement(step: DriveStep): boolean {
  const { element } = step

  if (!element) return true

  if (typeof element === 'string') {
    return !!document.querySelector(element)
  }

  if (typeof element === 'function') {
    return !!element()
  }

  return element instanceof Element
}

async function waitForTourReady(
  buildSteps: () => DriveStep[],
  options: { timeoutMs?: number, intervalMs?: number } = {}
): Promise<DriveStep[] | null> {
  const timeoutMs = options.timeoutMs ?? 8000
  const intervalMs = options.intervalMs ?? 120
  const start = Date.now()

  while (Date.now() - start < timeoutMs) {
    const steps = buildSteps()
    if (steps.length === 0) return null

    const firstAnchoredStep = steps.find(step => !!step.element)
    if (!firstAnchoredStep || stepHasRenderableElement(firstAnchoredStep)) {
      return steps
    }

    await sleep(intervalMs)
  }

  return null
}

function toMajorVersion(major: OnboardingMajor): number {
  return major === 'editor' ? EDITOR_TOUR_VERSION : DASHBOARD_TOUR_VERSION
}

function getMajorPreferenceKey(major: OnboardingMajor): 'onboardingDashboardTourVersion' | 'onboardingEditorTourVersion' {
  return major === 'editor' ? 'onboardingEditorTourVersion' : 'onboardingDashboardTourVersion'
}

const TOUR_ID_SET = new Set<TourId>(TOUR_IDS)

function getTourIdsForMajor(major: OnboardingMajor): TourId[] {
  return onboardingTours
    .filter(tour => tour.major === major)
    .map(tour => tour.id)
}

export const useOnboarding = () => {
  const { preferences, savePreferences, fetchPreferences } = useEditorPreferences()

  const route = useRoute()
  const editorStore = useEditorStore()

  const isActive = useState<boolean>('onboarding-active', () => false)
  const activeTourId = useState<TourId | null>('onboarding-active-tour-id', () => null)

  const majorResetDone = useState<Record<OnboardingMajor, boolean>>('onboarding-major-reset-done', () => ({
    dashboard: false,
    editor: false
  }))
  const latestAutoStartRequestId = useState<number>('onboarding-latest-autostart-request-id', () => 0)

  let currentDriver: Driver | null = null
  let hasWarnedFallbackDriver = false

  function createFallbackDriver(config?: Config): Driver {
    return driver({
      showProgress: true,
      progressText: '{{current}} of {{total}}',
      animate: true,
      smoothScroll: true,
      allowClose: true,
      overlayOpacity: 0.5,
      overlayColor: '#000',
      stagePadding: 12,
      stageRadius: 8,
      popoverClass: 'larex-tour-popover',
      nextBtnText: 'Next',
      prevBtnText: 'Back',
      doneBtnText: 'Done',
      ...config
    })
  }

  function resolveCreateDriver(): (config?: Config) => Driver {
    const nuxtApp = useNuxtApp()
    if (typeof nuxtApp.$createDriver === 'function') {
      return nuxtApp.$createDriver
    }

    if (!hasWarnedFallbackDriver) {
      hasWarnedFallbackDriver = true
      console.warn('[onboarding] $createDriver is unavailable, using direct driver() fallback.')
    }

    return createFallbackDriver
  }

  function getCompletionMap(): Partial<Record<TourId, true>> {
    const persisted = preferences.value.onboardingTourCompletion
    if (!persisted || typeof persisted !== 'object' || Array.isArray(persisted)) {
      return {}
    }

    const completion: Partial<Record<TourId, true>> = {}
    for (const [tourId, completed] of Object.entries(persisted)) {
      if (!TOUR_ID_SET.has(tourId as TourId) || completed !== true) continue
      completion[tourId as TourId] = true
    }
    return completion
  }

  async function persistOnboardingState(update: {
    onboardingTourCompletion?: Record<string, true>
    onboardingToursOptedOut?: boolean
    onboardingDashboardTourVersion?: number
    onboardingEditorTourVersion?: number
  }): Promise<void> {
    Object.assign(preferences.value, update)
    await savePreferences(update)
  }

  function markCompleted(tourId: TourId): void {
    const completion = getCompletionMap()
    const next: Record<string, true> = { ...completion, [tourId]: true }
    void persistOnboardingState({ onboardingTourCompletion: next })
  }

  function isCompleted(tourId: TourId): boolean {
    const completion = getCompletionMap()
    return completion[tourId] === true
  }

  async function clearCompletionForMajor(major: OnboardingMajor): Promise<void> {
    const completion = getCompletionMap()
    const majorTourIds = new Set<TourId>(getTourIdsForMajor(major))
    const next: Record<string, true> = {}
    let changed = false

    for (const [tourId, completed] of Object.entries(completion)) {
      if (completed !== true) continue
      if (majorTourIds.has(tourId as TourId)) {
        changed = true
        continue
      }
      next[tourId] = true
    }

    if (!changed) return
    await persistOnboardingState({ onboardingTourCompletion: next })
  }

  function isOptedOut(): boolean {
    if (import.meta.server) return false
    return preferences.value.onboardingToursOptedOut === true
  }

  function setOptedOut(value: boolean): void {
    if (import.meta.server) return
    void persistOnboardingState({ onboardingToursOptedOut: value })
  }

  function optOutTours(): void {
    stopTour()
    setOptedOut(true)
  }

  function attachOptOutControl(popover: PopoverDOM): void {
    if (popover.wrapper.querySelector('[data-onboarding-optout-btn]')) return

    const button = document.createElement('button')
    button.type = 'button'
    button.setAttribute('data-onboarding-optout-btn', 'true')
    button.setAttribute('aria-label', 'Disable all tours until reset')
    button.title = 'Disable all tours until reset'
    button.textContent = '⊘'
    // Do not use classes containing "driver-popover" here. Driver.js captures
    // and suppresses those clicks at document-capture phase.
    button.className = 'larex-tour-optout-btn'
    button.style.width = '32px'
    button.style.height = '32px'
    button.style.padding = '0'
    button.style.display = 'inline-flex'
    button.style.alignItems = 'center'
    button.style.justifyContent = 'center'
    button.style.fontSize = '14px'
    button.style.lineHeight = '1'
    button.style.marginRight = '8px'
    button.style.border = '1px solid var(--ui-border)'
    button.style.borderRadius = '6px'
    button.style.background = 'transparent'
    button.style.color = 'inherit'
    button.style.cursor = 'pointer'

    button.addEventListener('click', (event) => {
      event.preventDefault()
      event.stopPropagation()
      optOutTours()
    })

    popover.footerButtons.prepend(button)
  }

  async function ensureMajorResetPolicy(major: OnboardingMajor): Promise<void> {
    if (majorResetDone.value[major]) return

    try {
      await fetchPreferences()
    } catch {
      return
    }

    const prefKey = getMajorPreferenceKey(major)
    const currentVersion = toMajorVersion(major)
    const storedVersion = preferences.value[prefKey]

    if (storedVersion === null || storedVersion < currentVersion) {
      await clearCompletionForMajor(major)
    }

    majorResetDone.value[major] = true
  }

  function sanitizeSteps(steps: OnboardingDriveStep[]): DriveStep[] {
    return steps
      .filter(step => (typeof step.includeIf === 'function' ? step.includeIf() : true))
      .map((step) => {
        const { includeIf: _includeIf, ...rest } = step
        return resolveKnownSelectors(rest)
      })
  }

  function isStaleAutoStartRequest(options: { requestId?: number, expectedPathNormalized?: string }): boolean {
    if (typeof options.requestId !== 'number') return false
    if (latestAutoStartRequestId.value !== options.requestId) return true

    if (options.expectedPathNormalized && normalizeRoutePath(route.path) !== options.expectedPathNormalized) {
      return true
    }

    return false
  }

  async function startTourById(
    tourId: TourId,
    options: { force?: boolean, requestId?: number, expectedPathNormalized?: string, ignoreOptOut?: boolean } = {}
  ): Promise<boolean> {
    const force = options.force ?? false

    if (!options.ignoreOptOut && isOptedOut()) return false
    if (isStaleAutoStartRequest(options)) return false
    if (isActive.value) return false

    const definition = getTourDefinition(tourId)
    if (!definition) return false

    await ensureMajorResetPolicy(definition.major)

    if (isStaleAutoStartRequest(options)) return false
    if (!force && isCompleted(tourId)) return false

    const steps = await waitForTourReady(() => sanitizeSteps(definition.steps()))
    if (isStaleAutoStartRequest(options)) return false
    if (!steps || steps.length === 0) return false

    isActive.value = true
    activeTourId.value = tourId
    let completed = false
    let dismissed = false

    const createDriver = resolveCreateDriver()

    currentDriver = createDriver({
      steps,
      onPopoverRender: (popover) => {
        attachOptOutControl(popover)
      },
      onNextClick: (_el, _step, opts) => {
        if (opts.driver.hasNextStep()) {
          opts.driver.moveNext()
          return
        }
        completed = true
        opts.driver.destroy()
      },
      onCloseClick: (_el, _step, opts) => {
        dismissed = true
        opts.driver.destroy()
      },
      onDestroyed: () => {
        isActive.value = false
        activeTourId.value = null

        const finishedTourId = tourId
        currentDriver = null

        void definition.onFinish?.()
        if (completed || dismissed) {
          markCompleted(finishedTourId)
        }

        const versionKey = getMajorPreferenceKey(definition.major)
        void savePreferences({ [versionKey]: toMajorVersion(definition.major) })
      }
    })

    currentDriver.drive()
    return true
  }

  async function maybeAutoStartContextTour(routePath?: string, context: OnboardingContext = {}): Promise<boolean> {
    if (import.meta.server) return false
    if (isActive.value) return false

    latestAutoStartRequestId.value += 1
    const requestId = latestAutoStartRequestId.value

    const requestedPath = routePath ?? route.path
    const requestedPathNormalized = normalizeRoutePath(requestedPath)

    if (routePath && normalizeRoutePath(route.path) !== requestedPathNormalized) {
      return false
    }

    try {
      await fetchPreferences()
    } catch {
      // Continue with in-memory defaults and let startTourById apply best-effort policy.
    }

    if (routePath && normalizeRoutePath(route.path) !== requestedPathNormalized) {
      return false
    }

    const effectivePath = requestedPath
    const effectiveContext: OnboardingContext = {
      ...context,
      editorMode: context.editorMode ?? editorStore.effectiveUiMode(editorStore.activeCanvasId)
    }

    const tourId = resolveContextTourId(effectivePath, effectiveContext)
    if (!tourId) return false

    const definition = getTourDefinition(tourId)
    if (!definition || !definition.autoStart) return false

    return startTourById(tourId, {
      requestId,
      expectedPathNormalized: requestedPathNormalized
    })
  }

  async function startCurrentPageTour(): Promise<boolean> {
    const tourId = resolveContextTourId(route.path, {
      editorMode: editorStore.effectiveUiMode(editorStore.activeCanvasId)
    })
    if (!tourId) return false

    return startTourById(tourId, { force: true })
  }

  const startDashboardTour = () => startTourById('global-intro', { force: true })

  const startEditorTour = () => {
    const mode = editorStore.effectiveUiMode(editorStore.activeCanvasId)
    const preferred: TourId = mode === 'text' ? 'editor-text' : 'editor-layout'
    return startTourById(preferred, { force: true })
  }

  const maybeAutoStartDashboardTour = () => maybeAutoStartContextTour('/')

  const maybeAutoStartEditorTour = () => maybeAutoStartContextTour('/editor', {
    editorMode: editorStore.effectiveUiMode(editorStore.activeCanvasId)
  })

  const stopTour = () => {
    if (currentDriver) {
      currentDriver.destroy()
      currentDriver = null
    }
    isActive.value = false
    activeTourId.value = null
  }

  const resetTours = async () => {
    stopTour()

    await persistOnboardingState({
      onboardingTourCompletion: {},
      onboardingToursOptedOut: false,
      onboardingDashboardTourVersion: 0,
      onboardingEditorTourVersion: 0
    })

    majorResetDone.value.dashboard = false
    majorResetDone.value.editor = false

    if (route.path !== '/') {
      await navigateTo('/')
    }

    await nextTick()
    setTimeout(() => {
      void startTourById('global-intro', { force: true })
    }, 500)
  }

  return {
    isActive: computed(() => isActive.value),
    activeTourId: computed(() => activeTourId.value),
    isOptedOut: computed(() => isOptedOut()),
    startTourById,
    maybeAutoStartContextTour,
    startCurrentPageTour,
    startDashboardTour,
    startEditorTour,
    maybeAutoStartDashboardTour,
    maybeAutoStartEditorTour,
    optOutTours,
    stopTour,
    resetTours
  }
}
