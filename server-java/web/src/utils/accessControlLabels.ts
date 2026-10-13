import { builtInRoleLabels, permissionLabels } from '@/locales/accessControl'
import type { AuthRole, PermissionTreeNode } from '@/types/authRole'

type Translate = (key: string) => string

export function permissionDisplayName(permission: Pick<PermissionTreeNode, 'permissionKey' | 'name'>, t: Translate): string {
  const labels = Object.prototype.hasOwnProperty.call(permissionLabels, permission.permissionKey)
    ? permissionLabels[permission.permissionKey] : undefined
  return labels?.includes(permission.name)
    ? t(`accessControl.permissions.${permission.permissionKey}`) : permission.name
}

function roleField(role: Pick<AuthRole, 'roleKey' | 'authRoleName' | 'description'>, field: 'name' | 'description', t: Translate): string {
  const value = (field === 'name' ? role.authRoleName : role.description) ?? ''
  if (!Object.prototype.hasOwnProperty.call(builtInRoleLabels, role.roleKey)) return value
  const labels = builtInRoleLabels[role.roleKey as keyof typeof builtInRoleLabels][field]
  // Preserve administrator-defined names and descriptions, even for a built-in role.
  return (labels as readonly string[]).includes(value) ? t(`accessControl.roles.${role.roleKey}.${field}`) : value
}

export function authRoleDisplayName(role: Pick<AuthRole, 'roleKey' | 'authRoleName'>, t: Translate): string {
  return roleField(role, 'name', t)
}

export function authRoleDescription(role: Pick<AuthRole, 'roleKey' | 'authRoleName' | 'description'>, t: Translate): string {
  return roleField(role, 'description', t)
}
