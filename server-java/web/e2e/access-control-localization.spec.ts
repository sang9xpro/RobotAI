import { expect, test } from '@playwright/test'
import { permissionLabels } from '../src/locales/accessControl'

for (const databaseLanguage of ['Chinese', 'English'] as const) {
  test(`access control translates ${databaseLanguage} data, searches localized roles and saves unchanged IDs`, async ({ page }) => {
    const permissions = Object.entries(permissionLabels).map(([permissionKey, names], i) => ({
      permissionId: i + 1, permissionKey, name: names[databaseLanguage === 'Chinese' ? 2 : 0], permissionType: 'api', children: [], status: '1',
    }))
    const role = {
      authRoleId: 1, roleKey: 'admin', status: '1',
      authRoleName: databaseLanguage === 'Chinese' ? '管理员' : 'Administrator',
      description: databaseLanguage === 'Chinese' ? '系统管理员，拥有所有权限' : 'System administrator with all permissions',
    }
    const user = { userId: 7, username: 'acl-fixture', name: 'RobotAI test', isAdmin: '1' }
    const config = { ...role, permissionTree: permissions, checkedPermissionIds: [1, 2] }
    let saved: unknown
    await page.addInitScript(({ user, permissions, role }) => {
      localStorage.setItem('token', 'acl-localization-fixture')
      localStorage.setItem('userInfo', JSON.stringify(user))
      localStorage.setItem('permissions', JSON.stringify(permissions))
      localStorage.setItem('authRole', JSON.stringify(role))
      localStorage.setItem('locale', 'vi-VN')
    }, { user, permissions, role })
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname
      if (route.request().method() === 'PUT') saved = route.request().postDataJSON()
      const data = path.endsWith('/check-token') ? { user, permissions, authRole: role }
        : path.endsWith('/1/permissions') ? config : { list: [role], total: 1 }
      await route.fulfill({ json: { code: 200, data } })
    })
    await page.goto('/auth-role')
    await expect(page.locator('.auth-role-item__name')).toHaveText('Quản trị viên')
    await expect(page.locator('.auth-role-item__desc')).toContainText('Quản trị hệ thống')
    await expect(page.locator('.permission-name-cell__title').filter({ hasText: /^Quản lý người dùng$/ })).toBeVisible()
    await expect(page.locator('.auth-role-view')).not.toContainText('用户管理')
    await page.locator('.auth-role-sidebar input').fill('quản trị')
    await expect(page.locator('.auth-role-item__name')).toHaveText('Quản trị viên')
    await page.getByRole('button', { name: 'Lưu quyền', exact: true }).click()
    await expect.poll(() => saved).toEqual([1, 2])
    await page.locator('.locale-dropdown').hover()
    await page.getByRole('menuitem', { name: 'English', exact: true }).click()
    await expect(page.locator('html')).toHaveAttribute('lang', 'en-US')
    await page.locator('.auth-role-sidebar input').fill('administrator')
    await expect(page.locator('.auth-role-item__name')).toHaveText('Administrator')
    await expect(page.locator('.permission-name-cell__title').filter({ hasText: /^User management$/ })).toBeVisible()
  })
}
