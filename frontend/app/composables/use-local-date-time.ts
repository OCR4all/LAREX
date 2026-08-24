import { useTimeAgo } from '@vueuse/core'
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

export function formatLocalDateTime(value: DateTimeInput): string {
  const date = normalizeLocalDateTimeInput(value)
  if (!date) return ''

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)
}

export function useLocalDateTime(value: MaybeRefOrGetter<DateTimeInput>) {
  const isHydrated = ref(false)
  const date = computed(() => normalizeLocalDateTimeInput(toValue(value)))
  const absoluteLabel = computed(() => formatLocalDateTime(date.value))
  const isoDate = computed(() => date.value?.toISOString())
  const shouldUseRelativeLabel = computed(() => {
    if (!isHydrated.value || !date.value) return false
    return Math.abs(Date.now() - date.value.getTime()) <= RELATIVE_MAX_MS
  })
  const relativeLabel = useTimeAgo(
    computed(() => date.value ?? new Date()),
    {
      max: RELATIVE_MAX_MS,
      fullDateFormatter: formatLocalDateTime,
      updateInterval: 30_000
    }
  )

  onMounted(() => {
    isHydrated.value = true
  })

  const label = computed(() => {
    if (!date.value) return '—'
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
