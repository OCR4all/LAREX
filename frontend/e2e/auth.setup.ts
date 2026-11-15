import { readFileSync } from 'node:fs'
import { expect, test as setup } from '@playwright/test'

interface RealmUser {
  username: string
  credentials?: Array<{ value: string }>
}

function credentials() {
  const username = process.env.E2E_USERNAME ?? 'katarn'
  if (process.env.E2E_PASSWORD) return { username, password: process.env.E2E_PASSWORD }

  const realm = JSON.parse(readFileSync(new URL('../../config/keycloak/dev.realm.json', import.meta.url), 'utf8'))
  const password = (realm.users as RealmUser[]).find(user => user.username === username)?.credentials?.[0]?.value
  if (!password) throw new Error(`Set E2E_PASSWORD: ${username} has no password in the dev realm`)
  return { username, password }
}

setup('sign in', async ({ page }) => {
  const { username, password } = credentials()
  await page.goto('/')
  await page.getByLabel(/username/i).fill(username)
  await page.getByLabel(/^password$/i).fill(password)
  await page.getByRole('button', { name: /sign in/i }).click()
  await expect(page).toHaveURL(url => !url.pathname.startsWith('/realms/') && !url.pathname.startsWith('/auth/'))
  await page.context().storageState({ path: 'e2e/.auth/user.json' })
})
