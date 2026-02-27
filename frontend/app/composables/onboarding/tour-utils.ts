import type { DriverHook } from 'driver.js'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'

export const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

function isElementVisible(el: Element | null): boolean {
  if (!el || !(el instanceof HTMLElement)) return false
  const style = window.getComputedStyle(el)
  if (style.display === 'none' || style.visibility === 'hidden') return false
  return el.getClientRects().length > 0
}

export async function waitForElement(
  selector: string,
  options: { timeoutMs?: number, intervalMs?: number } = {}
): Promise<Element | null> {
  const timeoutMs = options.timeoutMs ?? 6000
  const intervalMs = options.intervalMs ?? 100
  const start = Date.now()

  while (Date.now() - start < timeoutMs) {
    const el = document.querySelector(selector)
    if (el) return el
    await sleep(intervalMs)
  }

  return null
}

export async function waitForVisibleElement(
  selector: string,
  options: { timeoutMs?: number, intervalMs?: number } = {}
): Promise<Element | null> {
  const timeoutMs = options.timeoutMs ?? 6000
  const intervalMs = options.intervalMs ?? 100
  const start = Date.now()

  while (Date.now() - start < timeoutMs) {
    const el = document.querySelector(selector)
    if (isElementVisible(el)) return el
    await sleep(intervalMs)
  }

  return null
}

export async function clickAndWait(
  clickSelector: string,
  waitSelector?: string,
  options: { timeoutMs?: number, delayAfterClickMs?: number } = {}
): Promise<void> {
  const timeoutMs = options.timeoutMs ?? 6000
  const delayAfterClickMs = options.delayAfterClickMs ?? 120

  const el = await waitForElement(clickSelector, { timeoutMs })
  if (el instanceof HTMLElement) {
    el.click()
    await sleep(delayAfterClickMs)
  }

  if (waitSelector) {
    await waitForElement(waitSelector, { timeoutMs })
  }
}

export async function navigateAndWait(
  to: string,
  waitSelector?: string,
  options: { timeoutMs?: number, delayAfterNavMs?: number } = {}
): Promise<void> {
  const timeoutMs = options.timeoutMs ?? 6000
  const delayAfterNavMs = options.delayAfterNavMs ?? 120

  await navigateTo(to)
  await sleep(delayAfterNavMs)

  if (waitSelector) {
    await waitForElement(waitSelector, { timeoutMs })
  }
}

export async function ensureEditorMode(mode: 'layout' | 'text'): Promise<void> {
  const editorStore = useEditorStore()
  const uiStore = useEditorUiStore()

  uiStore.setUiMode(mode, editorStore.activeCanvasId)

  const timeoutMs = 4000
  const start = Date.now()

  while (Date.now() - start < timeoutMs) {
    if (uiStore.effectiveUiMode(editorStore.activeCanvasId) === mode) return
    await sleep(80)
  }
}

export async function ensureDashboardSidebarVisible(): Promise<void> {
  const searchButton = document.querySelector('[data-tour="search-button"]')
  if (isElementVisible(searchButton)) return

  window.dispatchEvent(new CustomEvent('larex:onboarding:open-dashboard-sidebar'))
  await waitForVisibleElement('[data-tour="search-button"]', { timeoutMs: 4000, intervalMs: 80 })
}

function findSidebarTriggerByLabel(sidebarRoot: Element, sectionLabel: string): HTMLButtonElement | null {
  for (const button of sidebarRoot.querySelectorAll('button')) {
    if (!(button instanceof HTMLButtonElement)) continue
    const directMatch = button.getAttribute('aria-label')?.trim() === sectionLabel || button.getAttribute('title')?.trim() === sectionLabel
    if (directMatch) return button

    const spans = button.querySelectorAll('span')
    for (const span of spans) {
      if (span.children.length === 0 && span.textContent?.trim() === sectionLabel) {
        return button
      }
    }
  }
  return null
}

export async function ensureSidebarSectionExpanded(sectionLabel: 'Utilities' | 'Workspace' | 'Settings'): Promise<void> {
  await ensureDashboardSidebarVisible()

  const searchButton = await waitForVisibleElement('[data-tour="search-button"]', { timeoutMs: 3000, intervalMs: 80 })
  const sidebarRoot = searchButton?.closest('[data-collapsed]')
  if (!sidebarRoot) return

  const trigger = findSidebarTriggerByLabel(sidebarRoot, sectionLabel)
  if (!trigger) return

  if (trigger.getAttribute('aria-expanded') === 'false') {
    trigger.click()
    await sleep(120)
  }
}

export function withNextAction(action: () => void | Promise<void>): DriverHook {
  return (_element, _step, opts) => {
    void (async () => {
      try {
        await action()
      } finally {
        opts.driver.moveNext()
      }
    })()
  }
}

export function withHookAction(action: () => void | Promise<void>): DriverHook {
  return () => {
    void action()
  }
}

export function dispatchOnboardingEvent(eventName: string): void {
  window.dispatchEvent(new CustomEvent(eventName))
}
