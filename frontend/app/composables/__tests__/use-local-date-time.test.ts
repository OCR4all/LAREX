// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { computed, createApp, createSSRApp, h, nextTick, ref } from 'vue'
import { renderToString } from 'vue/server-renderer'
import {
  formatLocalDateTime,
  localDateTimeInputToUtc,
  normalizeLocalDateTimeInput,
  toLocalDateTimeInput,
  useLocalDateTime,
  useLocalDateTimeFormatter
} from '../use-local-date-time'

const originalTimezone = process.env.TZ
afterEach(() => {
  if (originalTimezone === undefined) delete process.env.TZ
  else process.env.TZ = originalTimezone
  vi.useRealTimers()
})

describe('server timestamp parsing', () => {
  it.each(['UTC', 'Europe/Berlin', 'America/New_York', 'Asia/Kolkata'])('treats unzoned API timestamps as UTC in %s', (timezone) => {
    process.env.TZ = timezone
    expect(normalizeLocalDateTimeInput('2026-07-15T12:00:00.123456')?.toISOString()).toBe('2026-07-15T12:00:00.123Z')
    expect(normalizeLocalDateTimeInput('2026-07-15T14:00:00+02:00')?.toISOString()).toBe('2026-07-15T12:00:00.000Z')
    expect(normalizeLocalDateTimeInput('2026-07-15T12:00:00Z')?.toISOString()).toBe('2026-07-15T12:00:00.000Z')
  })

  it.each([null, undefined, '', ' ', 'invalid', new Date(NaN), NaN])('handles missing or invalid timestamps: %s', (value) => {
    expect(normalizeLocalDateTimeInput(value)).toBeNull()
    expect(formatLocalDateTime(value)).toBe('')
  })

  it('supports Date and epoch values without applying an offset twice', () => {
    const date = new Date('2026-07-15T12:00:00Z')
    expect(normalizeLocalDateTimeInput(date)).toBe(date)
    expect(normalizeLocalDateTimeInput(date.getTime())?.toISOString()).toBe(date.toISOString())
  })
})

describe('local clock values', () => {
  it.each([
    ['Europe/Berlin', '2026-01-15T12:00:00', '2026-01-15T13:00'],
    ['Europe/Berlin', '2026-07-15T12:00:00', '2026-07-15T14:00'],
    ['Europe/Berlin', '2026-03-29T00:30:00', '2026-03-29T01:30'],
    ['Europe/Berlin', '2026-03-29T01:30:00', '2026-03-29T03:30'],
    ['America/New_York', '2026-01-15T02:00:00', '2026-01-14T21:00'],
    ['Asia/Kolkata', '2026-07-15T12:00:00', '2026-07-15T17:30']
  ])('round trips %s server time %s through a local editor', (timezone, timestamp, localValue) => {
    process.env.TZ = timezone
    expect(toLocalDateTimeInput(timestamp)).toBe(localValue)
    expect(localDateTimeInputToUtc(localValue)).toBe(`${timestamp}.000`)
    expect(formatLocalDateTime(timestamp, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }, 'en-GB')).toBe(localValue.slice(11))
  })
})

describe('rendering in the browser timezone', () => {
  it('keeps server-local labels out of SSR and refreshes computed labels on mount', async () => {
    const value = ref('2026-07-15T12:00:00')
    const component = {
      setup() {
        const { formatDateTime } = useLocalDateTimeFormatter()
        const label = computed(() => formatDateTime(value.value, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }, 'en-GB'))
        return () => h('time', label.value || '—')
      }
    }
    process.env.TZ = 'UTC'
    const html = await renderToString(createSSRApp(component))
    expect(html).toBe('<time>—</time>')
    process.env.TZ = 'Europe/Berlin'
    const host = document.createElement('div')
    host.innerHTML = html
    const app = createSSRApp(component)
    app.mount(host)
    try {
      await nextTick()
      expect(host.textContent).toBe('14:00')
      value.value = '2026-01-15T12:00:00'
      await nextTick()
      expect(host.textContent).toBe('13:00')
    } finally {
      app.unmount()
    }
  })

  it('shows a newly uploaded item as just now in Berlin', async () => {
    process.env.TZ = 'Europe/Berlin'
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-15T12:00:00Z'))
    const host = document.createElement('div')
    const app = createApp({
      setup() {
        const { label } = useLocalDateTime('2026-07-15T12:00:00')
        return () => h('time', label.value)
      }
    })
    app.mount(host)
    try {
      await nextTick()
      expect(host.textContent).toBe('just now')
    } finally {
      app.unmount()
    }
  })
})
