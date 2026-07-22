<script setup lang="ts">
import { strToU8, zipSync } from 'fflate'

type AuthenticationMode = 'bundled' | 'external'
type ProxyMode = 'existing' | 'nginx'

interface InstallerManifest {
  version: string
  files: string[]
}

const toast = useToast()
const runtimeConfig = useRuntimeConfig()
const downloading = ref(false)
const wizardOpen = ref(false)
const manifest = ref<InstallerManifest>()

const configuration = reactive({
  authentication: 'bundled' as AuthenticationMode,
  proxy: 'existing' as ProxyMode,
  kraken: false,
  docs: false,
  instanceName: 'LAREX Production',
  appHost: 'app.example.org',
  authHost: 'auth.example.org',
  realm: 'larex-prod',
  docsUrl: 'https://docs.larex.kallimachos.de',
  imageTag: '1.0.0-SNAPSHOT'
})

const baseUrl = computed(() => {
  const configuredBase = runtimeConfig.app.baseURL || '/'
  return configuredBase.endsWith('/') ? configuredBase : `${configuredBase}/`
})

const hostnamePattern = /^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/

const configurationValid = computed(() => {
  return configuration.instanceName.trim().length > 0
    && hostnamePattern.test(configuration.appHost)
    && hostnamePattern.test(configuration.authHost)
    && configuration.realm.trim().length > 0
    && configuration.imageTag.trim().length > 0
    && (!configuration.docs || isHttpUrl(configuration.docsUrl))
})

onMounted(async () => {
  try {
    manifest.value = await $fetch<InstallerManifest>(`${baseUrl.value}installer-assets/manifest.json`)
    configuration.imageTag = manifest.value.version
  } catch {
    toast.add({
      title: 'Installer assets unavailable',
      description: 'Reload the page or use the versioned bundle attached to the GitHub release.',
      color: 'error',
      icon: 'i-lucide-triangle-alert'
    })
  }
})

function isHttpUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'http:'
  } catch {
    return false
  }
}

function replaceEnvValue(source: string, key: string, value: string) {
  const serialized = /\s|#|"/.test(value) ? JSON.stringify(value) : value
  const lines = source.split('\n')
  const index = lines.findIndex(line => line.startsWith(`${key}=`))

  if (index === -1) {
    lines.push(`${key}=${serialized}`)
  } else {
    lines[index] = `${key}=${serialized}`
  }

  return lines.join('\n')
}

function selectedFiles() {
  const files = new Set([
    'LICENSE',
    'compose.prod.base.yaml',
    'compose.prod.publish.localhost.yaml',
    'deployment/env/.env.prod.example',
    'scripts/bootstrap-env-secrets.sh',
    'scripts/larex-backup.sh',
    'scripts/larex-instance-backup.sh'
  ])

  if (configuration.authentication === 'bundled') {
    files.add('compose.prod.auth.bundled-keycloak.yaml')
    files.add('config/keycloak/larex.bootstrap.realm.json')
    files.add('config/keycloak/theme.jar')
  } else {
    files.add('compose.prod.auth.external-keycloak.yaml')
  }

  if (configuration.proxy === 'nginx') {
    files.add('compose.prod.nginx.yaml')
    files.add(
      configuration.authentication === 'bundled'
        ? 'deployment/nginx/templates/larex-proxy.conf.template'
        : 'deployment/nginx/templates/larex-proxy.external-keycloak.conf.template'
    )
  }

  if (configuration.kraken) {
    files.add('compose.actions.yaml')
    files.add('deployment/env/.env.actions.example')
    files.add('deployment/actions/kraken-segmentation.yaml')
  }

  if (configuration.docs) {
    files.add('compose.prod.docs.yaml')
  }

  return [...files]
}

function composeFiles() {
  const files = [
    'compose.prod.base.yaml',
    configuration.authentication === 'bundled'
      ? 'compose.prod.auth.bundled-keycloak.yaml'
      : 'compose.prod.auth.external-keycloak.yaml',
    'compose.prod.publish.localhost.yaml'
  ]

  if (configuration.proxy === 'nginx') {
    files.push('compose.prod.nginx.yaml')
  }

  if (configuration.kraken) {
    files.push('compose.actions.yaml')
  }

  if (configuration.docs) {
    files.push('compose.prod.docs.yaml')
  }

  return files
}

function composeCommand(command: string) {
  const environmentFiles = ['--env-file .env.prod']
  if (configuration.kraken) {
    environmentFiles.push('--env-file .env.actions')
  }

  const fileArguments = composeFiles().map(file => `-f ${file}`)
  const profileArguments = configuration.kraken ? ['--profile actions'] : []
  const argumentsList = [...environmentFiles, ...fileArguments, ...profileArguments]

  const continuation = ' \\\n  '
  return `docker compose${continuation}${argumentsList.join(continuation)}${continuation}${command}`
}

function generatedReadme() {
  const authLabel = configuration.authentication === 'bundled' ? 'Bundled Keycloak' : 'External Keycloak'
  const proxyLabel = configuration.proxy === 'nginx' ? 'Bundled Nginx' : 'Existing reverse proxy'
  const backupProfile = configuration.authentication === 'bundled' ? 'prod' : 'external-keycloak'
  const optionalNotes = [
    configuration.authentication === 'external'
      ? `- Configure the \`${configuration.realm}\` realm, clients, roles, redirect URIs, and admin client in your external Keycloak before startup.`
      : '- The first startup imports the included Keycloak realm and LAREX theme.',
    configuration.proxy === 'nginx'
      ? configuration.authentication === 'bundled'
        ? `- Place \`${configuration.appHost}.crt\`, \`${configuration.appHost}.key\`, \`${configuration.authHost}.crt\`, and \`${configuration.authHost}.key\` in \`deployment/nginx/certs/\` before startup.`
        : `- Place \`${configuration.appHost}.crt\` and \`${configuration.appHost}.key\` in \`deployment/nginx/certs/\` before startup. External Keycloak remains at \`https://${configuration.authHost}\` and is not proxied by bundled Nginx.`
      : `- Route \`${configuration.appHost}\` to \`127.0.0.1:3000\`${configuration.authentication === 'bundled' ? ` and \`${configuration.authHost}\` to \`127.0.0.1:8090\`` : ''}.`,
    configuration.kraken
      ? '- Kraken is enabled with CPU defaults. Review `.env.actions`, especially memory, CPU, device, model, and callback settings.'
      : '',
    configuration.docs
      ? '- Self-hosted documentation is published on `127.0.0.1:3001` by default.'
      : ''
  ].filter(Boolean).join('\n')

  return `# ${configuration.instanceName} deployment

Generated by the official LAREX deployment wizard.

- LAREX image tag: \`${configuration.imageTag}\`
- Authentication: ${authLabel}
- Routing: ${proxyLabel}
- Kraken Action processor: ${configuration.kraken ? 'enabled' : 'disabled'}
- Self-hosted docs: ${configuration.docs ? 'enabled' : 'disabled'}

## 1. Review and generate secrets

Review \`deployment/env/.env.prod.example\`, then generate \`.env.prod\` locally:

\`\`\`bash
bash scripts/bootstrap-env-secrets.sh prod
${configuration.kraken ? 'bash scripts/bootstrap-env-secrets.sh actions\n' : ''}\`\`\`

The downloaded bundle contains no generated credentials. Store the resulting env files securely and never commit them.

## 2. Deployment-specific preparation

${optionalNotes}

## 3. Validate and start

\`\`\`bash
${composeCommand('config --quiet')}
${composeCommand('pull')}
${composeCommand('up -d --wait --wait-timeout 300')}
\`\`\`

Open <https://${configuration.appHost}> after your reverse proxy and DNS are configured.

${configuration.kraken ? 'After the first startup, register `deployment/actions/kraken-segmentation.yaml` in the LAREX Admin Dashboard.\n\n' : ''}## Routine operations

\`\`\`bash
${composeCommand('ps')}
${composeCommand('logs -f')}
${composeCommand('pull')}
${composeCommand('up -d --wait --wait-timeout 300')}
${composeCommand('down')}
\`\`\`

Create a consistent instance backup:

\`\`\`bash
bash scripts/larex-instance-backup.sh create --profile ${backupProfile}
\`\`\`

Read the production and backup documentation before upgrades or restores. Database migrations may prevent image-only rollbacks.
`
}

async function downloadBundle() {
  if (!configurationValid.value || !manifest.value || downloading.value) {
    return
  }

  downloading.value = true

  try {
    const files = selectedFiles()
    const missingFiles = files.filter(file => !manifest.value?.files.includes(file))
    if (missingFiles.length) {
      throw new Error(`Installer manifest is missing: ${missingFiles.join(', ')}`)
    }

    const downloadedFiles = await Promise.all(files.map(async (path) => {
      const response = await fetch(`${baseUrl.value}installer-assets/${path}`)
      if (!response.ok) {
        throw new Error(`Could not load ${path}`)
      }
      return [path, new Uint8Array(await response.arrayBuffer())] as const
    }))

    const archiveRoot = `larex-deployment-${configuration.imageTag.replace(/[^A-Za-z0-9._-]/g, '-')}`
    const archive: Record<string, Uint8Array> = {}

    for (const [path, contents] of downloadedFiles) {
      archive[`${archiveRoot}/${path}`] = contents
    }

    const environmentPath = `${archiveRoot}/deployment/env/.env.prod.example`
    let environment = new TextDecoder().decode(archive[environmentPath])
    environment = replaceEnvValue(environment, 'LAREX_INSTANCE_NAME', configuration.instanceName.trim())
    environment = replaceEnvValue(environment, 'LAREX_PUBLIC_HOST', configuration.appHost)
    environment = replaceEnvValue(environment, 'KEYCLOAK_PUBLIC_HOST', configuration.authHost)
    environment = replaceEnvValue(environment, 'KEYCLOAK_REALM', configuration.realm.trim())
    environment = replaceEnvValue(environment, 'LAREX_IMAGE_TAG', configuration.imageTag.trim())
    environment = replaceEnvValue(environment, 'NUXT_PUBLIC_DOCUMENTATION_URL', configuration.docsUrl.trim())
    if (configuration.proxy === 'nginx' && configuration.authentication === 'external') {
      environment = replaceEnvValue(
        environment,
        'NGINX_TEMPLATE_FILE',
        './deployment/nginx/templates/larex-proxy.external-keycloak.conf.template'
      )
    }
    archive[environmentPath] = strToU8(environment)

    if (configuration.kraken) {
      const actionsEnvironmentPath = `${archiveRoot}/deployment/env/.env.actions.example`
      let actionsEnvironment = new TextDecoder().decode(archive[actionsEnvironmentPath])
      actionsEnvironment = replaceEnvValue(
        actionsEnvironment,
        'LAREX_ACTIONS_ENDPOINT_ALLOWED_ORIGINS',
        'http://action-kraken-segmentation:9000'
      )
      archive[actionsEnvironmentPath] = strToU8(actionsEnvironment)
    }

    archive[`${archiveRoot}/VERSION`] = strToU8(`${configuration.imageTag.trim()}\n`)
    archive[`${archiveRoot}/README.md`] = strToU8(generatedReadme())

    if (configuration.proxy === 'nginx') {
      archive[`${archiveRoot}/deployment/nginx/certs/PLACE_CERTIFICATES_HERE`] = strToU8('')
    }

    const zip = zipSync(archive, { level: 6 })
    const blob = new Blob([zip], { type: 'application/zip' })
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = href
    link.download = `${archiveRoot}.zip`
    link.click()
    URL.revokeObjectURL(href)

    toast.add({
      title: 'Deployment bundle created',
      description: 'No secrets were generated or sent to a server. Follow README.md inside the bundle.',
      color: 'success',
      icon: 'i-lucide-package-check'
    })
  } catch (error) {
    toast.add({
      title: 'Could not create deployment bundle',
      description: error instanceof Error ? error.message : 'Try again or use the release asset.',
      color: 'error',
      icon: 'i-lucide-triangle-alert'
    })
  } finally {
    downloading.value = false
  }
}
</script>

<template>
  <div class="not-prose my-8">
    <UCard :ui="{ body: 'sm:p-7' }">
      <div class="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex items-start gap-4">
          <div class="flex size-11 shrink-0 items-center justify-center rounded-md bg-elevated">
            <UIcon name="i-lucide-package-open" class="size-6 text-highlighted" />
          </div>
          <div>
            <p class="text-lg font-semibold text-highlighted">
              Build your deployment bundle
            </p>
            <p class="mt-1 max-w-xl text-sm text-muted">
              Open the guided installer to select a supported topology and download a complete, versioned administration bundle.
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <UBadge color="neutral" variant="soft">
                {{ configuration.authentication === 'bundled' ? 'Bundled Keycloak' : 'External Keycloak' }}
              </UBadge>
              <UBadge v-if="configuration.kraken" color="neutral" variant="soft">
                Kraken
              </UBadge>
              <UBadge v-if="configuration.docs" color="neutral" variant="soft">
                Self-hosted docs
              </UBadge>
            </div>
          </div>
        </div>
        <UButton
          size="lg"
          icon="i-lucide-wand-sparkles"
          trailing-icon="i-lucide-arrow-right"
          label="Open installer"
          class="shrink-0"
          @click="wizardOpen = true"
        />
      </div>
    </UCard>

    <USlideover
      v-model:open="wizardOpen"
      title="Build your deployment bundle"
      side="bottom"
      inset
      :ui="{
        content: 'h-[88dvh]',
        header: 'sm:px-8',
        wrapper: 'pe-10',
        description: 'ms-[3.25rem]',
        body: 'p-0 sm:p-0',
        footer: 'justify-between sm:px-8'
      }"
    >
      <template #title>
        <span class="flex items-center gap-3">
          <span class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-navy-600 text-white shadow-sm" aria-hidden="true">
            <UIcon name="i-lucide-package-open" class="size-5" />
          </span>
          <span>Build your deployment bundle</span>
        </span>
      </template>

      <template #body>
        <div class="mx-auto w-full max-w-4xl space-y-10 p-5 sm:p-8">
          <section aria-labelledby="authentication-heading">
            <div class="mb-3">
              <h3 id="authentication-heading" class="font-semibold text-highlighted">
                1. Authentication
              </h3>
              <p class="mt-1 text-sm text-muted">
                Bundled Keycloak is the simplest supported setup. Choose external only when your organization already operates Keycloak.
              </p>
            </div>
            <div class="grid gap-3 sm:grid-cols-2">
              <button
                v-for="option in [
                  { value: 'bundled', title: 'Bundled Keycloak', description: 'Includes Keycloak, PostgreSQL, the LAREX realm, and theme.' },
                  { value: 'external', title: 'External Keycloak', description: 'Connects LAREX to an existing, administrator-managed realm.' }
                ]"
                :key="option.value"
                type="button"
                class="rounded-md border p-4 text-left transition-colors"
                :class="configuration.authentication === option.value ? 'border-primary bg-elevated' : 'border-default hover:bg-elevated/60'"
                :aria-pressed="configuration.authentication === option.value"
                @click="configuration.authentication = option.value as AuthenticationMode"
              >
                <span class="block font-medium text-highlighted">{{ option.title }}</span>
                <span class="mt-1 block text-sm text-muted">{{ option.description }}</span>
              </button>
            </div>
          </section>

          <section aria-labelledby="routing-heading">
            <div class="mb-3">
              <h3 id="routing-heading" class="font-semibold text-highlighted">
                2. Public routing
              </h3>
              <p class="mt-1 text-sm text-muted">
                Existing reverse proxy is recommended for managed TLS. Bundled Nginx expects certificates that you provide and works with either authentication mode.
              </p>
            </div>
            <div class="grid gap-3 sm:grid-cols-2">
              <button
                type="button"
                class="rounded-md border p-4 text-left transition-colors"
                :class="configuration.proxy === 'existing' ? 'border-primary bg-elevated' : 'border-default hover:bg-elevated/60'"
                :aria-pressed="configuration.proxy === 'existing'"
                @click="configuration.proxy = 'existing'"
              >
                <span class="block font-medium text-highlighted">Existing reverse proxy</span>
                <span class="mt-1 block text-sm text-muted">Publishes loopback ports for Nginx, Caddy, Apache, a load balancer, or ingress.</span>
              </button>
              <button
                type="button"
                class="rounded-md border p-4 text-left transition-colors"
                :class="configuration.proxy === 'nginx' ? 'border-primary bg-elevated' : 'border-default hover:bg-elevated/60'"
                :aria-pressed="configuration.proxy === 'nginx'"
                @click="configuration.proxy = 'nginx'"
              >
                <span class="block font-medium text-highlighted">Bundled Nginx</span>
                <span class="mt-1 block text-sm text-muted">Publishes ports 80 and 443 using operator-provided certificates.</span>
              </button>
            </div>
          </section>

          <section aria-labelledby="services-heading">
            <div class="mb-3">
              <h3 id="services-heading" class="font-semibold text-highlighted">
                3. Optional services
              </h3>
            </div>
            <div class="grid gap-5 sm:grid-cols-2">
              <USwitch
                v-model="configuration.kraken"
                label="Kraken segmentation"
                description="Adds the official processor container and registration definition."
              />
              <USwitch
                v-model="configuration.docs"
                label="Self-hosted documentation"
                description="Adds the pre-built LAREX docs image on a loopback port."
              />
            </div>
          </section>

          <section aria-labelledby="configuration-heading">
            <div class="mb-3">
              <h3 id="configuration-heading" class="font-semibold text-highlighted">
                4. Instance configuration
              </h3>
              <p class="mt-1 text-sm text-muted">
                These non-secret values are written to the environment template inside the bundle.
              </p>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <UFormField label="Instance name" required>
                <UInput v-model="configuration.instanceName" class="w-full" autocomplete="organization" />
              </UFormField>
              <UFormField label="Image tag" required hint="Pin a release in production">
                <UInput v-model="configuration.imageTag" class="w-full" spellcheck="false" />
              </UFormField>
              <UFormField label="LAREX hostname" required :error="hostnamePattern.test(configuration.appHost) ? undefined : 'Enter a hostname without a scheme or path.'">
                <UInput v-model="configuration.appHost" class="w-full" placeholder="app.example.org" spellcheck="false" />
              </UFormField>
              <UFormField label="Keycloak hostname" required :error="hostnamePattern.test(configuration.authHost) ? undefined : 'Enter a hostname without a scheme or path.'">
                <UInput v-model="configuration.authHost" class="w-full" placeholder="auth.example.org" spellcheck="false" />
              </UFormField>
              <UFormField label="Keycloak realm" required>
                <UInput v-model="configuration.realm" class="w-full" spellcheck="false" />
              </UFormField>
              <UFormField v-if="configuration.docs" label="Documentation URL" required :error="isHttpUrl(configuration.docsUrl) ? undefined : 'Enter a complete HTTP or HTTPS URL.'">
                <UInput v-model="configuration.docsUrl" class="w-full" type="url" spellcheck="false" />
              </UFormField>
            </div>
          </section>

          <div class="rounded-md border border-default bg-elevated/50 p-4">
            <div class="flex items-start gap-3">
              <UIcon name="i-lucide-shield-check" class="mt-0.5 size-5 shrink-0 text-success" />
              <div class="text-sm">
                <p class="font-medium text-highlighted">
                  Secrets stay on your machine
                </p>
                <p class="mt-1 text-muted">
                  The ZIP is assembled in your browser. It contains placeholders only; the included bootstrap script generates credentials locally after download.
                </p>
              </div>
            </div>
          </div>
        </div>
      </template>

      <template #footer>
        <UButton
          color="neutral"
          variant="ghost"
          label="Close"
          @click="wizardOpen = false"
        />
        <NoiseBackground
          container-class="w-fit rounded-full"
          content-class="h-full"
          :gradient-colors="['rgb(255, 100, 150)', 'rgb(100, 150, 255)', 'rgb(255, 200, 100)']"
          :animating="!downloading"
        >
          <UButton
            size="lg"
            icon="i-lucide-download"
            trailing-icon="i-lucide-arrow-right"
            color="neutral"
            variant="ghost"
            :loading="downloading"
            :disabled="!configurationValid || !manifest"
            label="Download deployment bundle"
            class="h-full w-full cursor-pointer rounded-full bg-linear-to-r from-smoke-100 via-smoke-100 to-white px-5 py-2.5 text-smoke-950 shadow-[0px_2px_0px_0px_var(--color-smoke-50)_inset,0px_0.5px_1px_0px_var(--color-smoke-400)] transition-all duration-100 active:scale-[0.98] dark:from-smoke-950 dark:via-smoke-950 dark:to-smoke-900 dark:text-white dark:shadow-[0px_1px_0px_0px_var(--color-smoke-950)_inset,0px_1px_0px_0px_var(--color-smoke-800)] disabled:cursor-not-allowed disabled:transform-none"
            @click="downloadBundle"
          />
        </NoiseBackground>
      </template>
    </USlideover>
  </div>
</template>
