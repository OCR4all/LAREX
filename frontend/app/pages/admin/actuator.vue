<script setup lang="ts">
definePageMeta({ layout: 'admin', middleware: 'admin' })

type HealthStatusColor = 'success' | 'error' | 'warning' | 'neutral'

interface HealthDetail {
  status: string
  details?: Record<string, unknown>
}

interface HealthResponse {
  status: string
  components: Record<string, HealthDetail>
}

const {
  data: health,
  refresh: refreshHealth,
  pending: healthLoading,
  error: healthError
} = await useLazyFetch<HealthResponse>('/api/actuator/health')

const lastUpdatedAt = ref<Date | null>(null)

watch(health, (value) => {
  if (value) {
    lastUpdatedAt.value = new Date()
  }
}, { immediate: true })

const components = computed(() =>
  Object.entries(health.value?.components ?? {}).map(([name, component]) => ({
    name,
    ...component
  }))
)

const healthyComponentCount = computed(() =>
  components.value.filter(component => component.status?.toUpperCase() === 'UP').length
)

const componentCount = computed(() => components.value.length)

const refreshData = () => refreshHealth()

const getStatusColor = (status?: string): HealthStatusColor => {
  switch (status?.toUpperCase()) {
    case 'UP': return 'success'
    case 'DOWN': return 'error'
    case 'OUT_OF_SERVICE': return 'warning'
    default: return 'neutral'
  }
}

const getStatusIcon = (status?: string) => {
  switch (status?.toUpperCase()) {
    case 'UP': return 'i-lucide-check-circle'
    case 'DOWN': return 'i-lucide-x-circle'
    case 'OUT_OF_SERVICE': return 'i-lucide-alert-triangle'
    default: return 'i-lucide-help-circle'
  }
}

const getStatusIconClass = (status?: string) => {
  switch (status?.toUpperCase()) {
    case 'UP': return 'text-success'
    case 'DOWN': return 'text-error'
    case 'OUT_OF_SERVICE': return 'text-warning'
    default: return 'text-muted'
  }
}

function formatDetailLabel(key: string) {
  return key.replace(/([A-Z])/g, ' $1').trim()
}

function formatDetailValue(value: unknown) {
  if (value === null) {
    return 'null'
  }

  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return String(value)
}
</script>

<template>
  <UDashboardPanel id="admin-actuator">
    <template #header>
      <UDashboardNavbar title="Application Health">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <p class="text-sm text-muted">
            Monitor the health status of application components
          </p>
        </template>

        <template #right>
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            label="Refresh"
            :loading="healthLoading"
            @click="refreshData"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="mx-auto flex w-full max-w-5xl flex-col gap-6">
        <UAlert
          v-if="healthError"
          icon="i-lucide-alert-circle"
          color="error"
          variant="soft"
          title="Failed to load health data"
          :description="healthError.data?.message || 'Unable to fetch application health status'"
        />

        <div v-if="healthLoading" class="space-y-4">
          <USkeleton class="h-28 w-full" />
          <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <USkeleton v-for="item in 6" :key="item" class="h-40 w-full" />
          </div>
        </div>

        <template v-else-if="health">
          <div class="grid gap-3 md:grid-cols-3">
            <div class="rounded-lg bg-elevated/30 px-4 py-3 ring ring-default">
              <p class="text-xs font-medium uppercase text-muted">
                Overall Status
              </p>
              <div class="mt-3 flex items-center gap-2">
                <UIcon
                  :name="getStatusIcon(health.status)"
                  :class="['size-5', getStatusIconClass(health.status)]"
                />
                <UBadge :color="getStatusColor(health.status)" :label="health.status" />
              </div>
            </div>

            <div class="rounded-lg bg-elevated/30 px-4 py-3 ring ring-default">
              <p class="text-xs font-medium uppercase text-muted">
                Components
              </p>
              <p class="mt-2 text-xl font-semibold text-highlighted">
                {{ healthyComponentCount }} / {{ componentCount }}
              </p>
              <p class="mt-1 text-xs text-muted">
                Reporting healthy
              </p>
            </div>

            <div class="rounded-lg bg-elevated/30 px-4 py-3 ring ring-default">
              <p class="text-xs font-medium uppercase text-muted">
                Last Updated
              </p>
              <p class="mt-2 text-xl font-semibold text-highlighted">
                <NuxtTime
                  v-if="lastUpdatedAt"
                  :datetime="lastUpdatedAt"
                  hour="2-digit"
                  minute="2-digit"
                  second="2-digit"
                />
                <span v-else>--:--:--</span>
              </p>
              <p class="mt-1 text-xs text-muted">
                Local time
              </p>
            </div>
          </div>

          <div v-if="componentCount > 0" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <UPageCard
              v-for="component in components"
              :key="component.name"
              variant="outline"
              class="min-h-40"
              :ui="{
                container: 'min-h-40 p-5',
                wrapper: 'h-full',
                header: 'mb-5',
                body: 'flex flex-1 flex-col'
              }"
            >
              <template #header>
                <div class="flex w-full items-start justify-between gap-3">
                  <div class="flex min-w-0 items-center gap-3">
                    <UIcon
                      :name="getStatusIcon(component.status)"
                      :class="['size-5 shrink-0', getStatusIconClass(component.status)]"
                    />
                    <h2 class="truncate text-base font-semibold capitalize text-highlighted">
                      {{ component.name }}
                    </h2>
                  </div>

                  <UBadge
                    :color="getStatusColor(component.status)"
                    :label="component.status"
                    variant="soft"
                  />
                </div>
              </template>

              <template #body>
                <div v-if="component.details" class="space-y-2">
                  <div
                    v-for="(value, key) in component.details"
                    :key="key"
                    class="flex items-start justify-between gap-4 text-sm"
                  >
                    <span class="text-muted capitalize">{{ formatDetailLabel(String(key)) }}</span>
                    <span class="break-all text-right font-mono text-xs text-highlighted">
                      {{ formatDetailValue(value) }}
                    </span>
                  </div>
                </div>
                <p v-else class="mt-auto text-sm text-muted">
                  No component details reported.
                </p>
              </template>
            </UPageCard>
          </div>

          <UPageCard v-else variant="outline" class="py-10 text-center">
            <UIcon name="i-lucide-database" class="mx-auto size-10 text-muted" />
            <h2 class="mt-4 text-lg font-semibold text-highlighted">
              No component data available
            </h2>
            <p class="mt-2 text-sm text-muted">
              The health endpoint responded without component details.
            </p>
          </UPageCard>
        </template>

        <UPageCard v-else variant="outline" class="py-10 text-center">
          <UIcon name="i-lucide-database" class="mx-auto size-10 text-muted" />
          <h2 class="mt-4 text-lg font-semibold text-highlighted">
            No health data available
          </h2>
          <p class="mt-2 text-sm text-muted">
            Unable to retrieve application health information.
          </p>
        </UPageCard>
      </div>
    </template>
  </UDashboardPanel>
</template>
