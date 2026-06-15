<script setup lang="ts">
import { CalendarDate } from '@internationalized/date'
import { format, parseISO } from 'date-fns'
import type { UCalendar } from '#components'

type PickerSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl'
type CalendarModelValue = Parameters<typeof UCalendar>[0]['modelValue']

const props = withDefaults(defineProps<{
  modelValue?: string | null
  disabled?: boolean
  placeholder?: string
  size?: PickerSize
}>(), {
  modelValue: '',
  disabled: false,
  placeholder: 'Select date and time',
  size: 'md'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const isOpen = ref(false)
const selectedDate = ref<CalendarDate | undefined>(undefined)
const selectedHour = ref('09')
const selectedMinute = ref('00')

const hourItems = Array.from({ length: 24 }, (_, i) => {
  const value = String(i).padStart(2, '0')
  return { label: value, value }
})

const minuteItems = Array.from({ length: 60 }, (_, i) => {
  const value = String(i).padStart(2, '0')
  return { label: value, value }
})

const normalizedModelValue = computed(() => normalizeModelValue(props.modelValue))

watch(() => normalizedModelValue.value, (value) => {
  applyModelToDraft(value)
}, { immediate: true })

watch([selectedDate, selectedHour, selectedMinute], () => {
  const nextValue = buildDraftValue()
  if (nextValue !== normalizedModelValue.value) {
    emit('update:modelValue', nextValue)
  }
})

const displayValue = computed(() => {
  const value = buildDraftValue()
  if (!value) return ''

  const parsed = parseISO(value)
  if (Number.isNaN(parsed.getTime())) {
    return value.replace('T', ' ')
  }

  return format(parsed, 'PPp')
})

function normalizeModelValue(value: string | null | undefined): string {
  if (!value) return ''
  const trimmed = value.trim()
  if (!trimmed) return ''

  const match = trimmed.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/)
  if (!match) return ''

  return `${match[1]}T${match[2]}:${match[3]}`
}

function applyModelToDraft(value: string) {
  if (!value) {
    selectedDate.value = undefined
    return
  }

  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/)
  if (!match) return

  const [, year = '0', month = '1', day = '1', hour = '09', minute = '00'] = match
  selectedDate.value = new CalendarDate(Number(year), Number(month), Number(day))
  selectedHour.value = hour
  selectedMinute.value = minute
}

function buildDraftValue(): string {
  if (!selectedDate.value) return ''

  const year = String(selectedDate.value.year).padStart(4, '0')
  const month = String(selectedDate.value.month).padStart(2, '0')
  const day = String(selectedDate.value.day).padStart(2, '0')

  return `${year}-${month}-${day}T${selectedHour.value}:${selectedMinute.value}`
}

function setNow() {
  const now = new Date()
  selectedDate.value = new CalendarDate(now.getFullYear(), now.getMonth() + 1, now.getDate())
  selectedHour.value = String(now.getHours()).padStart(2, '0')
  selectedMinute.value = String(now.getMinutes()).padStart(2, '0')
}

function clearSelection() {
  selectedDate.value = undefined
  if (normalizedModelValue.value) {
    emit('update:modelValue', '')
  }
}

const calendarModelValue = computed<CalendarModelValue>(() => {
  const value = selectedDate.value
  if (!value) return null
  return value as unknown as CalendarModelValue
})

function handleCalendarChange(value: unknown) {
  if (value && typeof value === 'object' && 'year' in value && 'month' in value && 'day' in value) {
    selectedDate.value = new CalendarDate(Number(value.year), Number(value.month), Number(value.day))
    return
  }

  selectedDate.value = undefined
}
</script>

<template>
  <UPopover
    v-model:open="isOpen"
    :content="{ side: 'bottom', align: 'start', sideOffset: 6 }"
  >
    <UButton
      :size="size"
      color="neutral"
      variant="outline"
      class="w-full justify-between font-normal"
      :disabled="disabled"
    >
      <span :class="displayValue ? 'text-default' : 'text-muted'">
        {{ displayValue || placeholder }}
      </span>
      <UIcon name="i-lucide-calendar-clock" class="size-4 shrink-0 text-muted" />
    </UButton>

    <template #content>
      <div class="w-[19rem] space-y-3 p-3">
        <UCalendar
          :model-value="calendarModelValue"
          @update:model-value="handleCalendarChange"
          :month-controls="true"
          :year-controls="true"
        />

        <div class="grid grid-cols-2 gap-2">
          <UFormField label="Hour">
            <USelect
              v-model="selectedHour"
              :items="hourItems"
              value-key="value"
              size="sm"
              :disabled="!selectedDate || disabled"
            />
          </UFormField>

          <UFormField label="Minute">
            <USelect
              v-model="selectedMinute"
              :items="minuteItems"
              value-key="value"
              size="sm"
              :disabled="!selectedDate || disabled"
            />
          </UFormField>
        </div>

        <div class="flex items-center justify-between gap-2">
          <UButton
            size="xs"
            color="neutral"
            variant="ghost"
            :disabled="disabled"
            @click="clearSelection"
          >
            Clear
          </UButton>

          <div class="flex items-center gap-2">
            <UButton
              size="xs"
              color="neutral"
              variant="soft"
              :disabled="disabled"
              @click="setNow"
            >
              Now
            </UButton>
            <UButton
              size="xs"
              color="primary"
              :disabled="disabled || !selectedDate"
              @click="isOpen = false"
            >
              Done
            </UButton>
          </div>
        </div>
      </div>
    </template>
  </UPopover>
</template>
