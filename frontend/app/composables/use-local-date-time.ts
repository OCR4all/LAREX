import { formatTimeAgo, useMounted, useTimeAgo } from '@vueuse/core'
import { computed, toValue } from 'vue'
import type { MaybeRefOrGetter } from 'vue'

export type DateTimeInput = string | number | Date | null | undefined

const TIMEZONE_OFFSET_PATTERN = /(?:z|[+-]\d{2}:?\d{2})$/i
const ISO_DATE_TIME_WITHOUT_TIMEZONE_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/
const RELATIVE_MAX_MS = 7 * 24 * 60 * 60 * 1000

export function normalizeLocalDateTimeInput(value: DateTimeInput): Date | null {
  if (value === null || value === undefined || value === '') return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? null : date
  }

  const trimmed = value.trim()
  if (!trimmed) return null

  const normalized = ISO_DATE_TIME_WITHOUT_TIMEZONE_PATTERN.test(trimmed) && !TIMEZONE_OFFSET_PATTERN.test(trimmed)
    ? `${trimmed}Z`
    : trimmed

  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatLocalDateTime(
  value: DateTimeInput,
  options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' },
  locale?: string
): string {
  const date = normalizeLocalDateTimeInput(value)
  if (!date) return ''

  return new Intl.DateTimeFormat(locale, options).format(date)
}

// Do not render the server's locale/timezone, or disagree with it during hydration.
// Reading isMounted also makes computed labels refresh once the browser is ready.
export function useLocalDateTimeFormatter() {
  const isMounted = useMounted()
  return {
    formatDateTime: (value: DateTimeInput, options?: Intl.DateTimeFormatOptions, locale?: string) =>
      isMounted.value ? formatLocalDateTime(value, options, locale) : '',
    formatRelativeTime: (value: DateTimeInput) => {
      if (!isMounted.value) return ''
      const date = normalizeLocalDateTimeInput(value)
      return date ? formatTimeAgo(date) : ''
    }
  }
}

/** Convert a server timestamp to the wall-clock value expected by datetime-local inputs. */
export function toLocalDateTimeInput(value: DateTimeInput): string {
  const date = normalizeLocalDateTimeInput(value)
  if (!date) return ''
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** The API's LocalDateTime fields contain UTC wall-clock values without an offset. */
export function localDateTimeInputToUtc(value: string): string | null {
  if (!value.trim()) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString().slice(0, -1)
}

export function useLocalDateTime(value: MaybeRefOrGetter<DateTimeInput>) {
  const isHydrated = useMounted()
  const date = computed(() => normalizeLocalDateTimeInput(toValue(value)))
  const absoluteLabel = computed(() => isHydrated.value ? formatLocalDateTime(date.value) : '')
  const isoDate = computed(() => date.value?.toISOString())
  const shouldUseRelativeLabel = computed(() => {
    if (!isHydrated.value || !date.value) return false
    return Math.abs(Date.now() - date.value.getTime()) <= RELATIVE_MAX_MS
  })
  const relativeLabel = useTimeAgo(
    computed(() => date.value ?? new Date()),
    {
      max: RELATIVE_MAX_MS,
      fullDateFormatter: formatLocalDateTime
    }
  )

  const label = computed(() => {
    if (!isHydrated.value || !date.value) return '—'
    return shouldUseRelativeLabel.value ? relativeLabel.value : absoluteLabel.value
  })

  return {
    absoluteLabel,
    date,
    isoDate,
    label,
    relativeLabel,
    shouldUseRelativeLabel
  }
}
