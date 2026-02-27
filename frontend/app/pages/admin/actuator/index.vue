<script setup lang="ts">
interface HealthDetail {
  status: string
  details?: Record<string, any>
}

interface HealthResponse {
  status: string
  components: Record<string, HealthDetail>
}

const { data: health, refresh: refreshHealth, pending: healthLoading, error: healthError } = await useLazyFetch<HealthResponse>('/api/actuator/health')

const refreshData = () => refreshHealth()

const getStatusColor = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'UP': return 'success'
    case 'DOWN': return 'error'
    case 'OUT_OF_SERVICE': return 'warning'
    default: return 'neutral'
  }
}

const getStatusIcon = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'UP': return 'i-lucide-check-circle'
    case 'DOWN': return 'i-lucide-x-circle'
    case 'OUT_OF_SERVICE': return 'i-lucide-alert-triangle'
    default: return 'i-lucide-help-circle'
  }
}
</script>

<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <div>
        <h1 class="text-2xl font-bold">
          Application Health
        </h1>
        <p class="text-sm text-muted mt-1">
          Monitor the health status of application components
        </p>
      </div>
      <UButton icon="i-lucide-refresh-cw" :loading="healthLoading" @click="refreshData">
        Refresh
      </UButton>
    </div>

    <UAlert
      v-if="healthError"
      icon="i-lucide-alert-circle"
      color="error"
      variant="soft"
      title="Failed to load health data"
      :description="healthError.data?.message || 'Unable to fetch application health status'"
      class="mb-6"
    />

    <div v-if="healthLoading" class="space-y-4">
      <USkeleton class="h-20 w-full" />
      <USkeleton class="h-32 w-full" />
    </div>

    <div v-else-if="health" class="space-y-6">
      <UCard>
        <template #header>
          <div class="flex items-center gap-3">
            <UIcon :name="getStatusIcon(health.status)" :class="['w-6 h-6', `text-${getStatusColor(health.status)}-500`]" />
            <div>
              <h2 class="text-lg font-semibold">
                Overall Status
              </h2>
              <p class="text-sm text-muted">
                Application health overview
              </p>
            </div>
          </div>
        </template>
        <div class="flex items-center gap-2">
          <UBadge :color="getStatusColor(health.status)" :label="health.status" size="lg" />
          <span class="text-sm text-muted">Last updated: <NuxtTime
            :datetime="Date.now()"
            hour="2-digit"
            minute="2-digit"
            second="2-digit"
          /></span>
        </div>
      </UCard>

      <div v-if="health.components" class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <UCard v-for="(component, name) in health.components" :key="name">
          <template #header>
            <div class="flex items-center gap-3">
              <UIcon :name="getStatusIcon(component.status)" :class="['w-5 h-5', `text-${getStatusColor(component.status)}-500`]" />
              <div>
                <h3 class="font-semibold capitalize">
                  {{ name }}
                </h3>
                <UBadge :color="getStatusColor(component.status)" :label="component.status" size="sm" />
              </div>
            </div>
          </template>
          <div v-if="component.details" class="space-y-2">
            <div v-for="(value, key) in component.details" :key="key" class="flex justify-between items-center text-sm">
              <span class="font-medium text-muted capitalize">{{ key.replace(/([A-Z])/g, ' $1').trim() }}:</span>
              <span class="font-mono">{{ typeof value === 'object' ? JSON.stringify(value) : value }}</span>
            </div>
          </div>
        </UCard>
      </div>
    </div>

    <div v-else class="text-center py-12">
      <UIcon name="i-lucide-database" class="w-12 h-12 text-muted mx-auto mb-4" />
      <h3 class="text-lg font-semibold mb-2">
        No health data available
      </h3>
      <p class="text-muted">
        Unable to retrieve application health information
      </p>
    </div>
  </div>
</template>
