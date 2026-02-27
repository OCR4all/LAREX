import { driver, type Config, type Driver, type DriveStep, type PopoverDOM } from 'driver.js'
import {
  getTourDefinition,
  resolveContextTourId,
  type OnboardingDriveStep,
  type OnboardingMajor,
  type OnboardingContext,
  type TourId
} from './onboarding/tour-registry'
import {
  buildLocalCompletionKey,
  clearLocalCompletion,
  isTourCompleted,
  loadLocalCompletion,
  saveLocalCompletion,
  setTourCompletion,
  type LocalCompletionPayload
} from './onboarding/onboarding-storage'
import { sleep } from './onboarding/tour-utils'

/**
 * Bump these versions to trigger major onboarding resets.
 * Per-mini-tour completion is stored in localStorage and will be cleared
 * when these change through reset logic.
 */
const DASHBOARD_TOUR_VERSION = 2
const EDITOR_TOUR_VERSION = 2

const LOCAL_COMPLETION_SCHEMA_VERSION = 1
const LOCAL_OPTOUT_SCHEMA_VERSION = 1

/**
 * Map from CSS selector used in step definitions -> sidebar nav label text.
 * These selectors won't match the DOM directly (UNavigationMenu attr forwarding),
 * so we resolve them by text content lookup.
 */
const NAV_SELECTOR_TO_LABEL: Record<string, string> = {
  '[data-tour="nav-library"]': 'Library',
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

type SessionUserLike = {
  id?: string
  sub?: string
  username?: string
  preferred_username?: string
  email?: string
  name?: string
}

function getCurrentUserScopeId(): string {
  const { user, loggedIn } = useUserSession()
  const sessionUser = user.value as SessionUserLike | null | undefined

  const candidate = sessionUser?.id
    || sessionUser?.sub
    || sessionUser?.username
    || sessionUser?.preferred_username
    || sessionUser?.email
    || sessionUser?.name

  if (candidate && candidate.trim().length > 0) {
    return candidate.trim()
  }

  if (loggedIn.value) {
    return 'authenticated'
  }

  return 'anonymous'
}

function getLocalCompletionKey(userScopeId: string): string {
  return buildLocalCompletionKey({
    schemaVersion: LOCAL_COMPLETION_SCHEMA_VERSION,
    dashboardVersion: DASHBOARD_TOUR_VERSION,
    editorVersion: EDITOR_TOUR_VERSION,
    userScopeId
  })
}

function getOptOutKey(userScopeId: string): string {
  return ['larex', 'onboarding', `optout-${LOCAL_OPTOUT_SCHEMA_VERSION}`, userScopeId].join(':')
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

  function getCompletionPayload(): LocalCompletionPayload {
    const userScopeId = getCurrentUserScopeId()
    const key = getLocalCompletionKey(userScopeId)
    return loadLocalCompletion(import.meta.client ? localStorage : null, key, LOCAL_COMPLETION_SCHEMA_VERSION)
  }

  function markCompleted(tourId: TourId): void {
    const userScopeId = getCurrentUserScopeId()
    const key = getLocalCompletionKey(userScopeId)
    const payload = loadLocalCompletion(import.meta.client ? localStorage : null, key, LOCAL_COMPLETION_SCHEMA_VERSION)
    const next = setTourCompletion(payload, tourId, true)
    saveLocalCompletion(import.meta.client ? localStorage : null, key, next)
  }

  function isCompleted(tourId: TourId): boolean {
    const payload = getCompletionPayload()
    return isTourCompleted(payload, tourId)
  }

  function clearAllLocalCompletion(): void {
    if (!import.meta.client) return
    const prefix = 'larex:onboarding:'

    const keysToRemove: string[] = []
    for (let idx = 0; idx < localStorage.length; idx += 1) {
      const key = localStorage.key(idx)
      if (key?.startsWith(prefix)) {
        keysToRemove.push(key)
      }
    }

    for (const key of keysToRemove) {
      clearLocalCompletion(localStorage, key)
    }
  }

  function isOptedOut(): boolean {
    if (import.meta.server) return false
    const userScopeId = getCurrentUserScopeId()
    const key = getOptOutKey(userScopeId)
    return localStorage.getItem(key) === '1'
  }

  function setOptedOut(value: boolean): void {
    if (import.meta.server) return
    const userScopeId = getCurrentUserScopeId()
    const key = getOptOutKey(userScopeId)
    if (value) {
      localStorage.setItem(key, '1')
      return
    }
    localStorage.removeItem(key)
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
      clearAllLocalCompletion()
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
      // Continue with local state and let startTourById apply best-effort policy.
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

    clearAllLocalCompletion()
    setOptedOut(false)

    await savePreferences({
      onboardingDashboardTourVersion: null,
      onboardingEditorTourVersion: null
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
