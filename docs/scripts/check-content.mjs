import { readFile, readdir, stat } from 'node:fs/promises'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import GithubSlugger from 'github-slugger'
import { parse } from 'yaml'

const docs = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const content = resolve(docs, 'content')
const errors = []
const pages = new Map()
let checkedLinks = 0

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  return (await Promise.all(entries.map(entry => entry.isDirectory()
    ? walk(resolve(directory, entry.name))
    : [resolve(directory, entry.name)]))).flat().sort()
}

function routeFor(file) {
  const segments = relative(content, file).replace(/\.md$/, '').split('/')
    .map(segment => segment.replace(/^\d+\./, ''))
  if (segments.at(-1) === 'index') segments.pop()
  return `/${segments.join('/')}`
}

function withoutCode(text) {
  // Preserve line count for useful diagnostics and ignore links inside examples.
  let fence = null
  return text.split('\n').map(line => {
    const marker = line.match(/^\s*(`{3,}|~{3,})/)
    if (marker && (!fence || marker[1][0] === fence[0] && marker[1].length >= fence.length)) {
      fence = fence ? null : marker[1]
      return ''
    }
    return fence ? '' : line
  }).join('\n')
}

function anchorsFor(body, mdc = true) {
  const slugger = new GithubSlugger()
  const anchors = new Set()
  for (const line of withoutCode(body).split('\n')) {
    const heading = line.match(/^#{1,6}\s+(.+?)(?:\s+#+)?$/)
    if (!heading) continue
    const text = heading[1].replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
      .replace(/<[^>]+>/g, '').replace(/[`*_]/g, '')
    const slug = slugger.slug(text)
    // Match the installed MDC compiler, including numeric-heading normalization.
    anchors.add(mdc ? slug.replace(/-+/g, '-').replace(/^-|-$/g, '').replace(/^(\d)/, '_$1') : slug)
  }
  return anchors
}

for (const file of await walk(content)) {
  const text = await readFile(file, 'utf8')
  if (file.endsWith('.navigation.yml')) {
    try {
      if (!parse(text)?.title) errors.push(`${relative(docs, file)}: navigation needs a title`)
    } catch (error) { errors.push(`${relative(docs, file)}: ${error.message}`) }
    continue
  }
  if (!file.endsWith('.md')) continue
  const match = text.match(/^---\n([\s\S]*?)\n---(?:\n|$)/)
  if (!match) { errors.push(`${relative(docs, file)}: missing frontmatter`); continue }
  let meta
  try { meta = parse(match[1]) } catch (error) {
    errors.push(`${relative(docs, file)}: ${error.message}`)
    continue
  }
  for (const field of ['title', 'description']) {
    if (typeof meta?.[field] !== 'string' || !meta[field].trim()) {
      errors.push(`${relative(docs, file)}: missing ${field}`)
    }
  }
  const route = routeFor(file)
  const body = text.slice(match[0].length)
  if (route !== '/' && (withoutCode(body).match(/^#\s+/gm) || []).length !== 1) {
    errors.push(`${relative(docs, file)}: expected exactly one H1`)
  }
  if (pages.has(route)) errors.push(`${relative(docs, file)}: duplicate route ${route}`)
  pages.set(route, { file, text, body, anchors: anchorsFor(body) })
}

async function checkLink(target, file) {
  if (/^(?:[a-z][\w+.-]*:|\/\/)/i.test(target)) return
  checkedLinks++
  const [pathAndQuery, rawHash] = target.split('#')
  const path = decodeURIComponent(pathAndQuery.split('?')[0])
  const hash = rawHash ? decodeURIComponent(rawHash) : ''
  const label = `${relative(docs, file)} → ${target}`
  let page
  if (!path) page = [...pages.values()].find(item => item.file === file)
  else if (path.startsWith('/')) {
    page = pages.get(path.replace(/\/$/, '') || '/')
    if (!page) {
      if (!await exists(resolve(docs, `public${path}`))) errors.push(`${label}: missing route or asset`)
      return
    }
  } else {
    const destination = resolve(dirname(file), path)
    if (!await exists(destination)) { errors.push(`${label}: missing file`); return }
    if (destination.endsWith('.md')) {
      page = [...pages.values()].find(item => item.file === destination)
      if (!page && hash) page = { anchors: anchorsFor(await readFile(destination, 'utf8'), false) }
    }
  }
  if (hash && page && !page.anchors.has(hash)) errors.push(`${label}: missing heading anchor`)
}

async function exists(file) {
  try { await stat(file); return true } catch { return false }
}

const markdownFiles = [
  ...[...pages.values()].map(page => page.file),
  resolve(docs, 'README.md'), resolve(docs, '../README.md'), resolve(docs, 'audit-reference.md'),
  ...(await walk(resolve(docs, 'maintenance'))).filter(file => file.endsWith('.md'))
]
for (const file of markdownFiles) {
  const text = withoutCode(await readFile(file, 'utf8'))
  for (const match of text.matchAll(/\[[^\]\n]*\]\((?:<([^>]+)>|([^\s)]+))(?:\s+"[^"]*")?\)/g)) {
    await checkLink(match[1] || match[2], file)
  }
  // Nuxt MDC components can carry navigation targets in YAML props.
  for (const match of text.matchAll(/^\s*to:\s*['"]?(\/[^\s'"]+)['"]?\s*$/gm)) {
    await checkLink(match[1], file)
  }
}
for (const file of (await walk(resolve(docs, 'app'))).filter(file => file.endsWith('.vue'))) {
  const text = await readFile(file, 'utf8')
  for (const match of text.matchAll(/\bto(?:=|:\s*)['"](\/[^'"\s]*)['"]/g)) {
    await checkLink(match[1], file)
  }
}

if (errors.length) {
  console.error(errors.join('\n'))
  process.exitCode = 1
} else if (process.argv.includes('--routes')) {
  console.log(JSON.stringify([...pages.keys()]))
} else {
  console.log(`Checked ${pages.size} pages and ${checkedLinks} internal links: metadata, routes, headings, assets, and navigation passed.`)
}
