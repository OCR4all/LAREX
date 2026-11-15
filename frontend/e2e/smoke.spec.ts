import { expect, test } from '@playwright/test'

test('a signed-in user lands on the project overview', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByText('Projects', { exact: true }).first()).toBeVisible()
  await expect(page).toHaveURL('/')
})
