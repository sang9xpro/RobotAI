import { test, expect, type Page } from '@playwright/test'
const permissions = ['system:robot:music:list', 'system:robot:music:update', 'system:robot:status', 'system:config', 'system:config:api:list', 'system:config:api:create', 'system:role', 'system:device', 'system:chat'].map((permissionKey, i) => ({ permissionKey, permissionId: i + 1, children: [] }))
const account = { userId: 7, username: 'admin-fixture', name: 'RobotAI test', isAdmin: '1' }
const profile = { deviceId: 'user_chat_7', roleId: 1, roles: [{ roleId: 1, roleName: 'Ken' }, { roleId: 2, roleName: 'Ken mới' }] }
async function session(page: Page, keys = permissions, admin = true) {
  const user = { ...account, isAdmin: admin ? '1' : '0' }
  await page.addInitScript(({ account, keys }) => {
    localStorage.setItem('token', 'robotai-e2e-fixture')
    localStorage.setItem('userInfo', JSON.stringify(account))
    localStorage.setItem('permissions', JSON.stringify(keys))
    localStorage.setItem('locale', 'vi-VN')
  }, { account: user, keys })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    const body = path.endsWith('/check-token') ? { user, permissions: keys, authRole: { roleKey: admin ? 'admin' : 'user' } }
      : path.endsWith('/robot-profile') ? profile : { list: [], total: 0 }
    await route.fulfill({ json: { code: 200, message: 'ok', data: body } })
  })
}
test('Vietnamese login is the default and protected admin routes require login', async ({ page }) => {
  await page.goto('/login')
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi-VN')
  await expect(page.locator('button[type=submit]')).toContainText('Đăng nhập')
  await page.goto('/music'); await expect(page).toHaveURL(/\/login/)
})
test('Android profile selection uses owner API and preserves session notice', async ({ page }) => {
  await session(page)
  let selected: unknown
  await page.route('**/api/user/robot-profile/role', async route => {
    selected = route.request().postDataJSON()
    await route.fulfill({ json: { code: 200, message: 'ok', data: { ...profile, roleId: 2 } } })
  })
  await page.goto('/robot/profile')
  await expect(page.getByText('user_chat_7', { exact: true })).toBeVisible()
  await page.locator('.ant-select-selector').first().click()
  await page.getByTitle('Ken mới', { exact: true }).click()
  await page.getByRole('button', { name: 'Dùng trên Android' }).click()
  await expect.poll(() => selected).toEqual({ roleId: 2 })
  await expect(page.getByText('Đã lưu. Cấu hình có hiệu lực ở phiên mới hoặc sau khi kết nối lại.', { exact: true })).toBeVisible()
})
test('catalog edits are guarded and saved with the observed revision', async ({ page }) => {
  await session(page)
  const catalog = { revision: 'revision-one', tracks: [{ id: 'one', title: 'Bài hát Ken', artist: 'Ca sĩ', fileName: 'one.mp3', url: null }] }
  let written: { revision: string; tracks: unknown[] } | undefined
  await page.route('**/api/robot/music/catalog', async route => {
    if (route.request().method() === 'PUT') { written = route.request().postDataJSON(); await route.fulfill({ json: { code: 200, message: 'ok', data: { ...written, revision: 'revision-two' } } }) }
    else await route.fulfill({ json: { code: 200, message: 'ok', data: catalog } })
  })
  await page.goto('/music')
  await page.getByLabel('Tên bài').fill('Tên mới')
  await expect(page.locator('button').filter({ hasText: 'Tải MP3 lên' })).toBeDisabled()
  await page.getByRole('button', { name: 'Lưu thư viện' }).click()
  await expect.poll(() => written?.revision).toBe('revision-one')
  await expect(page.locator('button').filter({ hasText: 'Tải MP3 lên' })).toBeEnabled()
})
test('unknown hardware status has no invented latency', async ({ page }) => {
  await session(page)
  await page.route('**/api/robot/system/status', route => route.fulfill({ json: { code: 200, message: 'ok', data: [{ id: 'motor', state: 'unknown', checkedTime: '2026-10-12T01:00:00Z', latencyMs: null, detail: 'simulated' }] } }))
  await page.goto('/system/status')
  const row = page.locator('tr').filter({ hasText: 'Motor (mô phỏng)' })
  await expect(row).toContainText('Chưa xác định'); await expect(row).toContainText('Chưa có số đo')
})
test('ordinary permission set cannot open the shared music admin', async ({ page }) => {
  await session(page, [{ permissionKey: 'system:device', permissionId: 1, children: [] }], false)
  await page.goto('/music'); await expect(page).toHaveURL(/\/403/)
})

for (const kind of ['stt', 'tts'] as const) {
  test(`${kind} cloud test uses unsaved endpoint and model only after explicit action`, async ({ page }) => {
    await session(page)
    let probes = 0
    let body = ''
    await page.route(`**/api/config/test/${kind}`, async route => {
      probes++; body = route.request().postData() || ''
      await route.fulfill({ json: { code: 200, message: 'ok', data: kind === 'stt'
        ? { success: true, detail: 'Xin chào Ken', elapsedMillis: 12 }
        : { audioUrl: '/uploads/robotai-preview.mp3?sign=fixture' } } })
    })
    await page.goto(`/config/${kind}`)
    await page.getByRole('tab').last().click()
    await page.locator('.ant-select-selector').last().click()
    await page.locator('.ant-select-item-option-content').filter({ hasText: 'OpenAI compatible' }).click()
    await page.getByLabel('Model ID', { exact: true }).fill('custom-speech-model')
    await page.getByLabel('API Base URL', { exact: true }).fill('https://speech.example/v1')
    if (kind === 'stt') {
      await page.locator('#robotai-stt-sample').setInputFiles({ name: 'sample.wav', mimeType: 'audio/wav', buffer: Buffer.from('RIFF fixture') })
    }
    expect(probes).toBe(0)
    await page.locator('button').filter({ hasText: 'Kiểm tra' }).click()
    await expect.poll(() => probes).toBe(1)
    expect(body).toContain('custom-speech-model'); expect(body).toContain('https://speech.example/v1')
    if (kind === 'stt') await expect(page.getByText('Xin chào Ken', { exact: true })).toBeVisible()
    else await expect(page.locator('audio')).toHaveAttribute('src', /robotai-preview\.mp3/)
  })
}
