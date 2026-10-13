-- Additive, admin-only operational permissions; private memories remain owner scoped.
INSERT INTO sys_permission (parentId,name,permissionKey,permissionType,path,component,icon,sort,visible,status)
SELECT parent.permissionId,src.name,src.permissionKey,src.kind,src.path,NULL,src.icon,src.sort,'1','1'
FROM (
 SELECT 'Thư viện nhạc' name,'system:robot:music:list' permissionKey,'menu' kind,'/music' path,'SoundOutlined' icon,20 sort
 UNION ALL SELECT 'Sửa thư viện nhạc','system:robot:music:update','api',NULL,NULL,21
 UNION ALL SELECT 'Trạng thái dịch vụ','system:robot:status','menu','/system/status','DashboardOutlined',22
) src
JOIN sys_permission parent ON parent.permissionKey='system:config'
LEFT JOIN sys_permission existing ON existing.permissionKey=src.permissionKey
WHERE existing.permissionId IS NULL;
INSERT INTO sys_auth_role_permission (authRoleId,permissionId)
SELECT role.authRoleId,p.permissionId FROM sys_auth_role role JOIN sys_permission p
ON p.permissionKey IN ('system:robot:music:list','system:robot:music:update','system:robot:status')
LEFT JOIN sys_auth_role_permission existing ON existing.authRoleId=role.authRoleId AND existing.permissionId=p.permissionId
WHERE role.roleKey='admin' AND existing.id IS NULL;
