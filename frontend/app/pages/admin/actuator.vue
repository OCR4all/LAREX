<script setup lang="ts">
definePageMeta({ layout: 'admin', middleware: 'admin' })

type HealthStatusColor = 'success' | 'error' | 'warning' | 'neutral'

interface HealthDetail {
  status: string
  details?: Record<string, unknown>
}

interface HealthResponse {
  status: string
  components?: Record<string, HealthDetail>
  groups?: string[]
}

interface ComponentMeta {
  label: string
  description: string
  icon: string
}

interface HealthComponent extends HealthDetail {
  name: string
  meta: ComponentMeta
}

interface DetailEntry {
  key: string
  label: string
  value: string
}

const PROBE_NAMES = new Set(['ping', 'livenessState', 'readinessState'])

const COMPONENT_META: Record<string, ComponentMeta> = {
  db: { label: 'Database', description: 'Connection to the primary database', icon: 'i-lucide-database' },
  diskSpace: { label: 'Disk Space', description: 'Free space on the application volume', icon: 'i-lucide-hard-drive' },
  mail: { label: 'Mail Server', description: 'SMTP server for outgoing notifications', icon: 'i-lucide-mail' },
  ssl: { label: 'TLS Certificates', description: 'Certificate chains configured for the backend', icon: 'i-lucide-shield-check' },
  ping: { label: 'Ping', description: 'Backend answers HTTP requests', icon: 'i-lucide-radio' },
  livenessState: { label: 'Liveness', description: 'Application process is running correctly', icon: 'i-lucide-activity' },
  readinessState: { label: 'Readiness', description: 'Application accepts incoming traffic', icon: 'i-lucide-door-open' }
}

const {
  data: health,
  refresh: refreshHealth,
  pending: healthLoading,
  error: healthError
} = await useLazyFetch<HealthResponse>('/api/actuator/health')

const lastUpdatedAt = ref<Date | null>(null)
const healthErrorMessage = computed(() =>
  extractApiErrorMessage(healthError.value, 'Unable to fetch application health status')
)

watch(health, (value) => {
  if (value) {
    lastUpdatedAt.value = new Date()
  }
}, { immediate: true })

const components = computed<HealthComponent[]>(() =>
  Object.entries(health.value?.components ?? {})
    .map(([name, component]) => ({
      name,
      ...component,
      meta: COMPONENT_META[name] ?? {
        label: humanize(name),
        description: 'Reported by the backend health endpoint',
        icon: 'i-lucide-box'
      }
    }))
    .sort((a, b) => statusRank(a.status) - statusRank(b.status) || a.meta.label.localeCompare(b.meta.label))
)

const serviceComponents = computed(() => components.value.filter(component => !PROBE_NAMES.has(component.name)))
const probeComponents = computed(() => components.value.filter(component => PROBE_NAMES.has(component.name)))

const healthyComponentCount = computed(() =>
  components.value.filter(component => normalizeStatus(component.status) === 'UP').length
)
const componentCount = computed(() => components.value.length)

const overallMeta = computed(() => {
  switch (normalizeStatus(health.value?.status)) {
    case 'UP':
      return {
        title: 'All systems operational',
        icon: 'i-lucide-circle-check',
        wrapClass: 'bg-success/10 text-success'
      }
    case 'DOWN':
      return {
        title: 'Application is down',
        icon: 'i-lucide-circle-x',
        wrapClass: 'bg-error/10 text-error'
      }
    case 'OUT_OF_SERVICE':
      return {
        title: 'Application is out of service',
        icon: 'i-lucide-triangle-alert',
        wrapClass: 'bg-warning/10 text-warning'
      }
    default:
      return {
        title: 'Health status unknown',
        icon: 'i-lucide-circle-help',
        wrapClass: 'bg-elevated text-muted'
      }
  }
})

const diskSpace = computed(() => {
  const details = health.value?.components?.diskSpace?.details
  const total = Number(details?.total)
  const free = Number(details?.free)
  if (!Number.isFinite(total) || !Number.isFinite(free) || total <= 0) {
    return null
  }

  const used = total - free
  const usedPercent = Math.round((used / total) * 100)
  const threshold = Number(details?.threshold)

  return {
    total,
    free,
    used,
    usedPercent,
    threshold: Number.isFinite(threshold) ? threshold : null,
    path: typeof details?.path === 'string' ? details.path : null,
    color: usedPercent >= 90 ? 'error' as const : usedPercent >= 75 ? 'warning' as const : 'primary' as const
  }
})

const sslChains = computed(() => {
  const details = health.value?.components?.ssl?.details
  if (!details) {
    return null
  }

  const count = (key: string) => (Array.isArray(details[key]) ? (details[key] as unknown[]).length : 0)

  return [
    { label: 'Valid', value: count('validChains'), class: 'text-highlighted' },
    { label: 'Expiring', value: count('expiringChains'), class: count('expiringChains') > 0 ? 'text-warning' : 'text-highlighted' },
    { label: 'Invalid', value: count('invalidChains'), class: count('invalidChains') > 0 ? 'text-error' : 'text-highlighted' }
  ]
})

function normalizeStatus(status?: string) {
  return status?.toUpperCase() ?? 'UNKNOWN'
}

function statusRank(status?: string) {
  switch (normalizeStatus(status)) {
    case 'DOWN': return 0
    case 'OUT_OF_SERVICE': return 1
    case 'UP': return 3
    default: return 2
  }
}

function getStatusColor(status?: string): HealthStatusColor {
  switch (normalizeStatus(status)) {
    case 'UP': return 'success'
    case 'DOWN': return 'error'
    case 'OUT_OF_SERVICE': return 'warning'
    default: return 'neutral'
  }
}

function getStatusLabel(status?: string) {
  switch (normalizeStatus(status)) {
    case 'UP': return 'Healthy'
    case 'DOWN': return 'Down'
    case 'OUT_OF_SERVICE': return 'Out of service'
    default: return 'Unknown'
  }
}

function getStatusDotClass(status?: string) {
  switch (normalizeStatus(status)) {
    case 'UP': return 'bg-success'
    case 'DOWN': return 'bg-error'
    case 'OUT_OF_SERVICE': return 'bg-warning'
    default: return 'bg-muted'
  }
}

function humanize(key: string) {
  const spaced = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }

  const units = ['KB', 'MB', 'GB', 'TB', 'PB']
  let value = bytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex++
  }

  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[unitIndex]}`
}

function formatDetailValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  if (typeof value === 'boolean') {
    return value ? 'Yes' : 'No'
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? 'None' : value.map(item => formatDetailValue(item)).join(', ')
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return String(value)
}

function getDetailEntries(component: HealthComponent): DetailEntry[] {
  return Object.entries(component.details ?? {}).map(([key, value]) => ({
    key,
    label: humanize(key),
    value: formatDetailValue(value)
  }))
}
</script>

<template>
  <UDashboardPanel id="admin-actuator">
    <template #header>
      <UDashboardNavbar title="Actuator">
        <template #right>
          <UPopover :content="{ align: 'end', side: 'bottom', sideOffset: 8 }">
            <UButton
              color="neutral"
              variant="ghost"
              icon="i-lucide-info"
              square
              aria-label="About the Actuator page"
            />

            <template #content>
              <div class="max-w-xs space-y-1 p-3">
                <p class="text-sm font-medium text-highlighted">
                  Spring Boot Actuator
                </p>
                <p class="text-xs leading-5 text-muted">
                  Live health of the backend and the services it depends on, as reported by Spring Boot Actuator.
                </p>
              </div>
            </template>
          </UPopover>

          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            label="Refresh"
            :loading="healthLoading"
            @click="refreshHealth()"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-6 p-6">
        <UAlert
          v-if="healthError"
          icon="i-lucide-circle-alert"
          color="error"
          variant="soft"
          title="Failed to load health data"
          :description="healthErrorMessage"
        />

        <template v-if="healthLoading && !health">
          <USkeleton class="h-28 w-full rounded-lg" />
          <div class="grid gap-6 lg:grid-cols-2">
            <USkeleton v-for="item in 4" :key="item" class="h-48 w-full rounded-lg" />
          </div>
        </template>

        <template v-else-if="health">
          <UCard>
            <div class="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
              <div class="flex items-start gap-4">
                <div
                  class="flex size-16 shrink-0 items-center justify-center rounded-2xl"
                  :class="overallMeta.wrapClass"
                >
                  <UIcon :name="overallMeta.icon" class="size-7" />
                </div>

                <div class="space-y-2">
                  <div class="text-lg font-semibold text-highlighted">
                    {{ overallMeta.title }}
                  </div>

                  <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted">
                    <span class="flex items-center gap-1.5">
                      <UIcon name="i-lucide-layers" class="size-4" />
                      {{ healthyComponentCount }} of {{ componentCount }} components healthy
                    </span>
                    <span class="flex items-center gap-1.5">
                      <UIcon name="i-lucide-clock-3" class="size-4" />
                      <template v-if="lastUpdatedAt">
                        Checked at
                        <NuxtTime
                          :datetime="lastUpdatedAt"
                          hour="2-digit"
                          minute="2-digit"
                          second="2-digit"
                        />
                      </template>
                      <span v-else>Not checked yet</span>
                    </span>
                  </div>
                </div>
              </div>

              <div v-if="health.groups?.length" class="flex flex-wrap items-center gap-2">
                <span class="text-xs uppercase tracking-wide text-muted">Probe groups</span>
                <UBadge
                  v-for="group in health.groups"
                  :key="group"
                  color="neutral"
                  variant="outline"
                  class="font-mono"
                >
                  {{ group }}
                </UBadge>
              </div>
            </div>
          </UCard>

          <div v-if="componentCount === 0" class="py-12 text-center">
            <UIcon name="i-lucide-lock" class="mx-auto mb-4 size-10 text-muted" />
            <p class="text-lg font-medium text-highlighted">
              No component details reported
            </p>
            <p class="mt-1 text-sm text-muted">
              The health endpoint only returned an overall status. Component details are shown to global admins only.
            </p>
          </div>

          <template v-else>
            <div v-if="serviceComponents.length > 0" class="space-y-3">
              <h2 class="text-sm font-semibold uppercase tracking-wide text-muted">
                Services
              </h2>

              <div class="grid gap-6 lg:grid-cols-2">
                <UCard
                  v-for="component in serviceComponents"
                  :key="component.name"
                  :ui="{ body: 'flex h-full flex-col gap-5' }"
                >
                  <div class="flex items-start gap-4">
                    <div class="flex size-12 shrink-0 items-center justify-center rounded-xl border border-default bg-elevated/30">
                      <UIcon :name="component.meta.icon" class="size-5 text-muted" />
                    </div>
                    <div class="min-w-0 flex-1 space-y-0.5">
                      <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                        <h3 class="text-base font-semibold text-highlighted">
                          {{ component.meta.label }}
                        </h3>
                        <UBadge
                          :color="getStatusColor(component.status)"
                          variant="subtle"
                          class="shrink-0"
                        >
                          <span class="size-1.5 rounded-full" :class="getStatusDotClass(component.status)" />
                          {{ getStatusLabel(component.status) }}
                        </UBadge>
                      </div>
                      <p class="text-sm text-muted">
                        {{ component.meta.description }}
                      </p>
                    </div>
                  </div>

                  <div v-if="component.name === 'diskSpace' && diskSpace" class="space-y-4">
                    <div class="space-y-2">
                      <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                        <span class="text-xl font-semibold text-highlighted">
                          {{ formatBytes(diskSpace.free) }}
                          <span class="text-sm font-normal text-muted">free</span>
                        </span>
                        <span class="text-sm text-muted">
                          {{ diskSpace.usedPercent }}% of {{ formatBytes(diskSpace.total) }} used
                        </span>
                      </div>
                      <UProgress :model-value="diskSpace.usedPercent" :color="diskSpace.color" size="sm" />
                    </div>

                    <dl class="grid grid-cols-2 gap-3 text-sm">
                      <div class="rounded-lg bg-elevated/30 px-3 py-2">
                        <dt class="text-xs uppercase tracking-wide text-muted">
                          Path
                        </dt>
                        <dd class="mt-1 truncate font-mono text-highlighted">
                          {{ diskSpace.path ?? '—' }}
                        </dd>
                      </div>
                      <div class="rounded-lg bg-elevated/30 px-3 py-2">
                        <dt class="text-xs uppercase tracking-wide text-muted">
                          Alert below
                        </dt>
                        <dd class="mt-1 text-highlighted">
                          {{ diskSpace.threshold !== null ? formatBytes(diskSpace.threshold) : '—' }}
                        </dd>
                      </div>
                    </dl>
                  </div>

                  <dl v-else-if="component.name === 'ssl' && sslChains" class="grid grid-cols-3 gap-3 text-sm">
                    <div
                      v-for="chain in sslChains"
                      :key="chain.label"
                      class="rounded-lg bg-elevated/30 px-3 py-2"
                    >
                      <dt class="text-xs uppercase tracking-wide text-muted">
                        {{ chain.label }}
                      </dt>
                      <dd class="mt-1 text-xl font-semibold" :class="chain.class">
                        {{ chain.value }}
                      </dd>
                    </div>
                  </dl>

                  <dl v-else-if="component.details" class="divide-y divide-default rounded-lg border border-default text-sm">
                    <div
                      v-for="entry in getDetailEntries(component)"
                      :key="entry.key"
                      class="flex items-start justify-between gap-4 px-3 py-2"
                    >
                      <dt class="text-muted">
                        {{ entry.label }}
                      </dt>
                      <dd class="break-all text-right font-mono text-highlighted">
                        {{ entry.value }}
                      </dd>
                    </div>
                  </dl>

                  <p v-else class="text-sm text-muted">
                    No details reported.
                  </p>
                </UCard>
              </div>
            </div>

            <div v-if="probeComponents.length > 0" class="space-y-3">
              <h2 class="text-sm font-semibold uppercase tracking-wide text-muted">
                Probes
              </h2>

              <UCard :ui="{ body: 'p-0 sm:p-0' }">
                <ul class="divide-y divide-default">
                  <li
                    v-for="probe in probeComponents"
                    :key="probe.name"
                    class="flex items-center justify-between gap-4 px-4 py-3 sm:px-6"
                  >
                    <div class="flex min-w-0 items-center gap-3">
                      <UIcon :name="probe.meta.icon" class="size-5 shrink-0 text-muted" />
                      <div class="min-w-0">
                        <p class="font-medium text-highlighted">
                          {{ probe.meta.label }}
                        </p>
                        <p class="truncate text-sm text-muted">
                          {{ probe.meta.description }}
                        </p>
                      </div>
                    </div>

                    <UBadge
                      :color="getStatusColor(probe.status)"
                      variant="subtle"
                      class="shrink-0"
                    >
                      <span class="size-1.5 rounded-full" :class="getStatusDotClass(probe.status)" />
                      {{ getStatusLabel(probe.status) }}
                    </UBadge>
                  </li>
                </ul>
              </UCard>
            </div>
          </template>
        </template>

        <div v-else-if="!healthError" class="py-12 text-center">
          <UIcon name="i-lucide-heart-off" class="mx-auto mb-4 size-10 text-muted" />
          <p class="text-lg font-medium text-highlighted">
            No health data available
          </p>
          <p class="mt-1 text-sm text-muted">
            The backend did not return any health information.
          </p>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
