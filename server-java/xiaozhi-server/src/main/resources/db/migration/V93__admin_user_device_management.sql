-- Dedicated administrative permissions; normal account and device APIs keep their owner checks.
INSERT INTO sys_permission (parentId,name,permissionKey,permissionType,path,component,icon,sort,visible,status)
SELECT parent.permissionId,src.name,src.permissionKey,'api',NULL,NULL,NULL,src.sort,'0','1'
FROM (
 SELECT 'Create user' name,'system:user:api:create' permissionKey,3 sort
 UNION ALL SELECT 'Manage user profile','system:user:api:manage',4
 UNION ALL SELECT 'List user devices','system:user:devices:api:list',5
 UNION ALL SELECT 'Assign user devices','system:user:devices:api:bind',6
) src
JOIN sys_permission parent ON parent.permissionKey='system:user'
LEFT JOIN sys_permission existing ON existing.permissionKey=src.permissionKey
WHERE existing.permissionId IS NULL;

INSERT INTO sys_auth_role_permission (authRoleId,permissionId)
SELECT role.authRoleId,p.permissionId FROM sys_auth_role role JOIN sys_permission p
ON p.permissionKey IN ('system:user:api:create','system:user:api:manage',
                      'system:user:devices:api:list','system:user:devices:api:bind')
LEFT JOIN sys_auth_role_permission existing ON existing.authRoleId=role.authRoleId AND existing.permissionId=p.permissionId
WHERE role.roleKey='admin' AND existing.id IS NULL;
