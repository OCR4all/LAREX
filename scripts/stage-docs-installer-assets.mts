import { createHash } from 'node:crypto'
import { packageTheme, type ThemeMetadata } from './package-keycloak-theme.mts'
import { copyFile, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(scriptsDirectory, '..')
const outputDirectory = join(repositoryRoot, 'docs/public/installer-assets')
const fileListPath = join(scriptsDirectory, 'deployment-bundle-files.txt')

const fileList = (await readFile(fileListPath, 'utf8'))
  .split('\n')
  .map(line => line.trim())
  .filter(line => line && !line.startsWith('#'))

if (!outputDirectory.startsWith(join(repositoryRoot, 'docs/public/'))) {
  throw new Error(`Refusing to replace unexpected output directory: ${outputDirectory}`)
}

await rm(outputDirectory, { recursive: true, force: true })
await mkdir(outputDirectory, { recursive: true })

const version = (await readFile(join(repositoryRoot, 'VERSION'), 'utf8')).trim()
// Local docs can use the checked-in theme. Releases supply a fresh audited package.
const themeDirectory = join(repositoryRoot, 'config/keycloak')
try {
  const metadata: Pick<ThemeMetadata, 'sha256' | 'version'> = JSON.parse(await readFile(join(themeDirectory, 'theme.json'), 'utf8'))
  const actual = createHash('sha256').update(await readFile(join(themeDirectory, 'theme.jar'))).digest('hex')
  if (metadata.sha256 !== actual || metadata.version !== version) {
    throw new Error('Theme metadata does not match the JAR or VERSION; rebuild the theme package')
  }
} catch (error) {
  if (!(error instanceof Error) || !('code' in error) || error.code !== 'ENOENT') throw error
  await packageTheme(join(themeDirectory, 'theme.jar'), themeDirectory, version)
}
const sha256: Record<string, string> = {}
for (const relativePath of fileList) {
  const sourcePath = join(repositoryRoot, relativePath)
  const targetPath = join(outputDirectory, relativePath)
  await mkdir(dirname(targetPath), { recursive: true })
  await copyFile(sourcePath, targetPath)
  sha256[relativePath] = createHash('sha256').update(await readFile(targetPath)).digest('hex')
}

await writeFile(
  join(outputDirectory, 'manifest.json'),
  `${JSON.stringify({ version, files: fileList, sha256 }, null, 2)}\n`,
  'utf8'
)

console.log(`Staged ${fileList.length} deployment assets for the docs installer.`)
