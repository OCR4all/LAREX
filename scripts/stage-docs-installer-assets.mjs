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

for (const relativePath of fileList) {
  const sourcePath = join(repositoryRoot, relativePath)
  const targetPath = join(outputDirectory, relativePath)
  await mkdir(dirname(targetPath), { recursive: true })
  await copyFile(sourcePath, targetPath)
}

const version = (await readFile(join(repositoryRoot, 'VERSION'), 'utf8')).trim()
await writeFile(
  join(outputDirectory, 'manifest.json'),
  `${JSON.stringify({ version, files: fileList }, null, 2)}\n`,
  'utf8'
)

console.log(`Staged ${fileList.length} deployment assets for the docs installer.`)
