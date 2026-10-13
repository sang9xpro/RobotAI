import { describe, expect, it } from 'vitest'
import { accessControlEn, accessControlVi, accessControlZh, permissionLabels } from '@/locales/accessControl'
import { authRoleDescription, authRoleDisplayName, permissionDisplayName } from '../accessControlLabels'

function translator(messages: typeof accessControlEn) {
  return (key: string) => {
    const path = key.split('.').slice(1)
    let value: unknown = messages
    for (const part of path) value = (value as Record<string, unknown>)[part]
    return String(value)
  }
}

describe('Access control labels', () => {
  it.each([['vi', accessControlVi], ['en', accessControlEn], ['zh', accessControlZh]] as const)('translates every known permission by key in %s, regardless of database language', (_, messages) => {
    const t = translator(messages)
    for (const [permissionKey, labels] of Object.entries(permissionLabels)) {
      for (const name of labels) {
        expect(permissionDisplayName({ permissionKey, name }, t)).toBe(messages.permissions[permissionKey])
      }
    }
  })

  it('leaves unknown permissions and customized role labels intact', () => {
    const t = translator(accessControlVi)
    expect(permissionDisplayName({ permissionKey: 'custom:reports', name: 'Team reports' }, t)).toBe('Team reports')
    expect(permissionDisplayName({ permissionKey: 'system:device', name: 'Custom devices' }, t)).toBe('Custom devices')
    expect(permissionDisplayName({ permissionKey: 'constructor', name: 'Custom' }, t)).toBe('Custom')
    expect(authRoleDisplayName({ roleKey: 'admin', authRoleName: 'Operations team' }, t)).toBe('Operations team')
    expect(authRoleDescription({ roleKey: 'user', authRoleName: 'Regular user', description: 'Can view reports' }, t)).toBe('Can view reports')
    expect(authRoleDisplayName({ roleKey: 'editor', authRoleName: 'Editors' }, t)).toBe('Editors')
    expect(authRoleDescription({ roleKey: 'constructor', authRoleName: 'Custom' }, t)).toBe('')
  })

  it('translates built-in role names and descriptions before and after migration', () => {
    const t = translator(accessControlVi)
    for (const authRoleName of ['Administrator', '管理员']) {
      expect(authRoleDisplayName({ roleKey: 'admin', authRoleName }, t)).toBe('Quản trị viên')
    }
    for (const description of ['Regular user with basic permissions', '普通用户，拥有基本操作权限']) {
      expect(authRoleDescription({ roleKey: 'user', authRoleName: 'Regular user', description }, t)).toBe('Người dùng thông thường, có quyền thao tác cơ bản')
    }
  })
})
