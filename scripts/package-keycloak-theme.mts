import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { join, resolve } from 'node:path'

export interface ThemeMetadata {
  schemaVersion: 1
  version: string
  theme: 'keycloakify-larex'
  types: ['login', 'email']
  keycloakVersion: string
  sha256: string
  source: { repository: string; commit: string | null }
  provenance: string
}

interface ThemeDescriptor {
  themes?: Array<{ name: string; types: string[] }>
}

// Only theme resources belong in this artifact, never Java providers.
export function auditTheme(jarPath: string): void {
  execFileSync('unzip', ['-tqq', jarPath])
  const entries = execFileSync('unzip', ['-Z1', jarPath], { encoding: 'utf8' }).trim().split('\n')
  const seen = new Set<string>()
  for (const entry of entries) {
    if (seen.has(entry) || entry.includes('\\') || /[\u0000-\u001f\u007f]/.test(entry)
      || entry.split('/').some(part => part === '..' || part === '.')
      || /\.(class|jar)$/i.test(entry)
      || !(entry.startsWith('theme/keycloakify-larex/')
        || /^META-INF\/maven\/(?:[^/]+\/){0,2}$/.test(entry)
        || /^META-INF\/maven\/[^/]+\/[^/]+\/pom\.(xml|properties)$/.test(entry)
        || ['theme/', 'META-INF/', 'META-INF/MANIFEST.MF', 'META-INF/keycloak-themes.json'].includes(entry))) {
      throw new Error(`Unexpected theme archive entry: ${entry}`)
    }
    seen.add(entry)
  }
  const descriptor: ThemeDescriptor = JSON.parse(execFileSync('unzip', ['-p', jarPath, 'META-INF/keycloak-themes.json'], { encoding: 'utf8' }))
  const theme = descriptor.themes?.[0]
  if (descriptor.themes?.length !== 1 || !theme || theme.name !== 'keycloakify-larex'
    || [...theme.types].sort().join(',') !== 'email,login') {
    throw new Error('Expected the LAREX login and email themes')
  }
  for (const type of ['login', 'email']) {
    if (!seen.has(`theme/keycloakify-larex/${type}/theme.properties`)) {
      throw new Error(`Missing ${type} theme.properties`)
    }
  }
}

export async function packageTheme(
  jarPath: string,
  outputDirectory: string,
  version: string,
  commit: string | null = null
): Promise<void> {
  if (!/^[A-Za-z0-9._-]+$/.test(version) || (commit !== null && !/^[0-9a-f]{40}$/.test(commit))) {
    throw new Error('Invalid theme version or source commit')
  }
  auditTheme(jarPath)
  const contents = await readFile(jarPath)
  const sha256 = createHash('sha256').update(contents).digest('hex')
  await mkdir(outputDirectory, { recursive: true })
  await writeFile(join(outputDirectory, 'theme.jar'), contents)
  await writeFile(join(outputDirectory, 'theme.jar.sha256'), `${sha256}  theme.jar\n`)
  const metadata: ThemeMetadata = {
    schemaVersion: 1, version, theme: 'keycloakify-larex', types: ['login', 'email'],
    keycloakVersion: '26.7.4', sha256,
    source: { repository: 'ocr4all/larex', commit },
    // A local build does not have release provenance; do not imply that it does.
    provenance: commit ? 'Verify release attestation with scripts/verify-keycloak-theme.sh' : 'local-unattested'
  }
  await writeFile(join(outputDirectory, 'theme.json'), `${JSON.stringify(metadata, null, 2)}\n`)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [jarPath, outputDirectory, version, commit] = process.argv.slice(2)
  if (!jarPath || !outputDirectory || !version) {
    throw new Error('Usage: node --experimental-strip-types scripts/package-keycloak-theme.mts <jar> <output-directory> <version> [source-commit]')
  }
  await packageTheme(resolve(jarPath), resolve(outputDirectory), version, commit)
}
