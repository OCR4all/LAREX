import { readFile } from 'node:fs/promises'
import { deflateSync } from 'node:zlib'
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const WIDTH = 800
const HEIGHT = 600

function crc32(data: Buffer): number {
  let crc = ~0
  for (const byte of data) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (0xEDB88320 & -(crc & 1))
  }
  return ~crc >>> 0
}

function chunk(type: string, data: Buffer): Buffer {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([length, body, crc])
}

function whitePng(width: number, height: number): Buffer {
  const header = Buffer.alloc(13)
  header.writeUInt32BE(width, 0)
  header.writeUInt32BE(height, 4)
  header.set([8, 0, 0, 0, 0], 8)
  const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(width, 255)])
  const pixels = Buffer.concat(Array.from({ length: height }, () => row))
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(pixels)),
    chunk('IEND', Buffer.alloc(0))
  ])
}

function textLine(id: string, text: string, left: number, top: number): string {
  const points = `${left},${top} ${left + 120},${top} ${left + 120},${top + 40} ${left},${top + 40}`
  return `<TextLine id="${id}"><Coords points="${points}"/><TextEquiv index="0"><Unicode>${text}</Unicode></TextEquiv></TextLine>`
}

function pageXml(): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15">
  <Metadata><Creator>e2e</Creator><Created>2026-11-20T10:00:00</Created><LastChange>2026-11-20T10:00:00</LastChange></Metadata>
  <Page imageFilename="table.png" imageWidth="${WIDTH}" imageHeight="${HEIGHT}">
    <TextRegion id="r1">
      <Coords points="0,0 ${WIDTH},0 ${WIDTH},${HEIGHT} 0,${HEIGHT}"/>
      ${textLine('l1', 'Name', 120, 120)}
      ${textLine('l2', 'Year', 520, 120)}
      ${textLine('l3', 'Alice', 120, 340)}
      ${textLine('l4', '1871', 520, 340)}
    </TextRegion>
  </Page>
</PcGts>`
}

type ApiResponse = Awaited<ReturnType<APIRequestContext['get']>>

async function expectOk(response: ApiResponse): Promise<void> {
  expect(response.ok(), `${response.url()} answered ${response.status()}: ${await response.text()}`).toBe(true)
}

async function json<T>(response: ApiResponse): Promise<T> {
  await expectOk(response)
  return response.json() as Promise<T>
}

const PROJECT_PREFIX = 'e2e tables '

async function seedProject(request: APIRequestContext) {
  const workspaces = await json<Array<{ id: string, personal?: boolean, isPersonal?: boolean }>>(await request.get('/api/workspaces'))
  const workspace = workspaces.find(item => item.personal ?? item.isPersonal) ?? workspaces[0]
  if (!workspace) throw new Error('The test user has no workspace')

  const existing = await json<Array<{ id: string, name: string }>>(await request.get(`/api/workspaces/${workspace.id}/projects`))
  for (const leftover of existing.filter(item => item.name.startsWith(PROJECT_PREFIX))) {
    await request.delete(`/api/workspaces/${workspace.id}/projects/${leftover.id}`, { timeout: 60_000 })
  }

  const project = await json<{ id: string }>(await request.post(`/api/workspaces/${workspace.id}/projects`, {
    data: { name: `${PROJECT_PREFIX}${Date.now()}` }
  }))
  const page = await json<{ id: string }>(await request.post(`/api/projects/${project.id}/pages`, { data: { name: 'table' } }))
  const upload = `/api/upload-proxy/projects/${project.id}/pages/${page.id}`
  await expectOk(await request.post(`${upload}/images`, {
    multipart: { files: { name: 'table.png', mimeType: 'image/png', buffer: whitePng(WIDTH, HEIGHT) } }
  }))
  await expectOk(await request.post(`${upload}/xml`, {
    multipart: { file: { name: 'table.xml', mimeType: 'application/xml', buffer: Buffer.from(pageXml()) } }
  }))
  return { workspaceId: workspace.id, projectId: project.id }
}

async function canvasPoint(page: Page, dx: number, dy: number) {
  const box = await page.locator('canvas.cursor-grab').first().boundingBox()
  if (!box) throw new Error('The editor canvas is not visible')
  return { x: box.x + box.width / 2 + dx, y: box.y + box.height / 2 + dy }
}

async function contextMenu(page: Page, dx: number, dy: number) {
  const point = await canvasPoint(page, dx, dy)
  await page.mouse.click(point.x, point.y)
  await page.mouse.click(point.x, point.y, { button: 'right' })
}

test.use({ viewport: { width: 1920, height: 1080 }, actionTimeout: 10_000 })

test('create a table, merge cells, split a row and export it as CSV', async ({ page }) => {
  test.setTimeout(120_000)
  const { workspaceId, projectId } = await seedProject(page.request)
  try {
    await page.goto(`/project/${projectId}`)
    await page.getByRole('button', { name: 'Open in Editor' }).first().click()
    await expect(page).toHaveURL(/\/editor/)
    await expect(page.locator('canvas.cursor-grab').first()).toBeVisible({ timeout: 30_000 })
    await expect(page.getByText(/Loading (image|annotations)/)).toHaveCount(0, { timeout: 30_000 })

    await contextMenu(page, -60, -60)
    await page.getByRole('menuitem', { name: 'Convert to Table...' }).click()
    const panel = page.getByRole('dialog').filter({ hasText: 'Convert to Table' })
    await panel.getByRole('spinbutton', { name: 'Rows', exact: true }).fill('2')
    await panel.getByRole('spinbutton', { name: 'Columns', exact: true }).fill('2')
    await expect(panel.getByLabel('Move 4 text lines into the cells')).toBeChecked()
    await panel.getByRole('button', { name: 'Create' }).click()
    await expect(panel).toBeHidden()

    const topLeft = await canvasPoint(page, -60, -60)
    const topRight = await canvasPoint(page, 60, -60)
    await page.mouse.click(topLeft.x, topLeft.y)
    await page.keyboard.down('Shift')
    await page.mouse.click(topRight.x, topRight.y)
    await page.keyboard.up('Shift')
    await page.mouse.click(topRight.x, topRight.y, { button: 'right' })
    await page.getByRole('menuitem', { name: 'Merge Cells' }).click()

    await contextMenu(page, -60, 60)
    await page.getByRole('menuitem', { name: 'Table', exact: true }).click()
    await page.getByRole('menuitem', { name: 'Split Row' }).click()
    await expect(page.getByText(/\d+ table problems?/)).toHaveCount(0)

    await contextMenu(page, -60, -60)
    await page.getByRole('menuitem', { name: 'Table', exact: true }).click()
    await page.getByRole('menuitem', { name: 'Export Table' }).click()
    const download = page.waitForEvent('download')
    await page.getByRole('menuitem', { name: 'CSV' }).click()
    const file = await download
    expect(file.suggestedFilename()).toMatch(/\.csv$/)

    const csv = (await readFile(await file.path())).toString('utf8').replace(/^\uFEFF/, '')
    expect(csv).toBe('"Name\nYear",\r\nAlice,1871\r\n,\r\n')
  } finally {
    await page.request.delete(`/api/workspaces/${workspaceId}/projects/${projectId}`, { timeout: 60_000 })
  }
})
